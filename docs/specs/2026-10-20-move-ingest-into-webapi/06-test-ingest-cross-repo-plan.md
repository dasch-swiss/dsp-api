---
title: "test: Ingest under /ingest/ works across dsp-api, dsp-app, dsp-tools and ops-deploy"
type: test
date: 2026-10-26
author: "Raitis Veinbahs"
status: draft
prd: 01-move-ingest-into-webapi-PRD.md
repositories:
  - name: dsp-app
    path: /Users/raitisveinbahs/work/dsp-app
  - name: dsp-tools
    path: /Users/raitisveinbahs/work/dsp-tools
  - name: ops-deploy
    path: /Users/raitisveinbahs/work/ops-deploy
---

# test: Ingest under /ingest/ works across dsp-api, dsp-app, dsp-tools and ops-deploy

## Overview

The four per-repo plans (02 dsp-api, 03 dsp-app, 04 dsp-tools, 05 ops-deploy) each test their own repository. This
plan covers what only shows up with the repositories together, and the merge and release order of the four PRs:

- a local stack built from the dsp-api branch, started by the dsp-tools branch's `start-stack`, used by the dsp-app
  branch and by dsp-tools' upload commands;
- the old `ingest.<domain>/<path>` URLs through a local Traefik with the same `addprefix` rule ops-deploy adds;
- the released dsp-tools from PyPI against the new stack through that proxy (PRD US-2);
- a light check, during an upload, that conversion processes in the running container are the OOM killer's first
  choice;
- the merge, release and deploy order, and a DEV smoke check after the ops-deploy cutover (Human Actions).

The plan produces no commits. Rollback procedures and monitoring are out of scope by decision.

## Problem Statement / Motivation

Each per-repo plan tests against a fixture of the others: dsp-api's E2E specs call `/ingest/` in-process, dsp-app's
CI runs dsp-api `main`, dsp-tools' manual check uses a released dsp-api tag, and ops-deploy only renders templates.
None of them runs the branches together before they merge, and none of them runs an old client through the proxy.
The PRD's success criteria ("existing `ingest.<domain>` URLs, including published download links, still resolve";
"dsp-app reaches ingest only through `/ingest/`") are cross-repo claims, and the four PRs have an order constraint
(PRD: dsp-tools merges only once prod serves `/ingest/`).

## Proposed Solution

1. Build the dsp-api branch images locally (`just docker-build`) and retag them with the `API` version the dsp-tools
   branch pins in `versions.env`, so the dsp-tools branch's `start-stack` (without `--latest`, which pulls) runs the
   local images. Docker Compose does not pull an image tag that exists locally.
2. Stop start-stack's dsp-app container and run the dsp-app branch's dev server on port 4200 against the same API.
3. Exercise the contract in plan 02's "Cross-repo contracts" section end to end: uploads from dsp-app and dsp-tools,
   `/ingest/health`, original downloads, FAIR and OAI export links, and project export, erase and import.
4. Run a Traefik container on port 3340 whose only rule is `addPrefix /ingest` to `host.docker.internal:3333`. That is
   the middleware ops-deploy adds to the `ingest.<domain>` router. Port 3340 is also where released dsp-tools versions
   send ingest calls for a local server, so the released client goes through the proxy without any setting.
5. Record the merge, release and deploy order, and the DEV smoke check, as Human Actions after ship.

## Technical Considerations

- **Shared shell values.** Steps use `API_URL=http://0.0.0.0:3333`, `PROXY_URL=http://0.0.0.0:3340`,
  `STACK_DIR=~/.dsp-tools/start-stack`, a scratch directory `SCRATCH` outside the repositories, and a root token:

  ```bash
  TOKEN=$(curl -s -X POST "$API_URL/v2/authentication" -H 'Content-Type: application/json' \
    -d '{"email":"root@example.com","password":"test"}' | jq -r .token)
  ```

  Compose commands run as `docker compose --env-file versions.env ...` inside `$STACK_DIR`, the way `start-stack` runs
  them. `API_CTR` is `$(cd $STACK_DIR && docker compose --env-file versions.env ps -q api)`.
- **Pinned tag before release.** `start-stack` fetches ontologies and `sipi.docker-config.lua` from
  `raw.githubusercontent.com/dasch-swiss/dsp-api/<API>/` and pulls `apache-jena-fuseki:<DB>`. Until the release from
  plan 04's H1 exists, the branch's `API` tag may not exist on GitHub. Then this run sets `API`/`DB` in the dsp-tools
  working tree's `versions.env` to the newest existing dsp-api release and restores the file afterwards.
- **Proxy equivalence.** The local Traefik uses a catch-all rule on its own entrypoint instead of
  `Host(ingest.<domain>)`, and no TLS. The part under test, `addprefix` and pass-through of method, query, body,
  `Authorization` and `Content-Length` (PRD REQ-2.1, REQ-2.2), is the same middleware. The Traefik version is the one
  ops-deploy pins (`traefik.yml`: `traefik_version: "v3.6.4"`).
- **Upload size.** dsp-api answers `413` to an upload above 1 MiB without `Content-Length` (plan 02 risks). The
  proxy and client checks use files above 1 MiB, so a proxy or client that drops the header fails visibly.
- **Not repeated here.** Endpoint shapes, auth rules, the image's `oom_score_adj` wrapper, dsp-tools' URL derivation,
  dsp-app's Cypress run and ops-deploy's template rendering are covered by plans 02 to 05.

## Implementation Approach

### Stage 1: Local stack from the branches

- [x] In `/Users/raitisveinbahs/work/dsp-api`, on the branch with plan 02 complete, run `just docker-build`; it
  loads `daschswiss/knora-api:latest` and `daschswiss/knora-sipi:latest`
- [x] In `/Users/raitisveinbahs/work/dsp-tools`, on the branch with plan 04 complete, read `API` and `DB` from
  `src/dsp_tools/resources/start-stack/versions.env`. If
  `git ls-remote --tags https://github.com/dasch-swiss/dsp-api "refs/tags/$API"` prints nothing, set `API` and `DB`
  in that file to the newest dsp-api release tag (uncommitted; Stage 5 restores it)
- [x] Run `docker tag daschswiss/knora-api:latest daschswiss/knora-api:$API` and
  `docker tag daschswiss/knora-sipi:latest daschswiss/knora-sipi:$API`
- [x] From the dsp-tools branch, run `uv run dsp-tools start-stack --no-prune`; it reports the API running on
  port 3333
- [x] `docker inspect --format '{{.Image}}' $API_CTR` equals
  `docker image inspect --format '{{.Id}}' daschswiss/knora-api:latest`
- [x] `docker ps --format '{{.Image}}'` lists no `dsp-ingest` image, and nothing listens on port 3340
- [x] `curl -fsS -o /dev/null -w '%{http_code}' $API_URL/ingest/health` prints `200`

### Stage 2: Clients on the new paths

- [x] From the dsp-tools branch checkout, run
  `uv run dsp-tools create testdata/json-project/systematic-project-4123.json`; it succeeds
- [x] Start this poll in the background; it records every Sipi, ffmpeg and ffprobe process in the api container
  with its `oom_score_adj`:

  ```bash
  while true; do docker exec "$API_CTR" /bin/sh -c 'for d in /proc/[0-9]*; do
    read -r c < $d/comm 2>/dev/null; case "$c" in sipi|ffmpeg|ffprobe)
    read -r s < $d/oom_score_adj; echo "$c $s";; esac; done'; done >> "$SCRATCH/oom.txt"
  ```

- [x] Run `uv run dsp-tools xmlupload testdata/xml-data/test-data-systematic-4123.xml`; it succeeds, and
  `docker logs $API_CTR` shows `POST /ingest/projects/4123/assets/ingest/...` requests
- [x] Stop the poll; `$SCRATCH/oom.txt` has at least one `sipi` and one `ffmpeg` or `ffprobe` line, and every line
  ends in `1000` (REQ-3.4 sanity check; the systematic data has TIFF and MP4 files)
- [x] In `testdata/dsp-ingest-data/e2e-sample-project`, run `uv run dsp-tools create e2e-ingest-project-0001.json`,
  `uv run dsp-tools upload-files e2e-ingest-0001.xml`, `uv run dsp-tools ingest-files 0001` and
  `uv run dsp-tools ingest-xmlupload e2e-ingest-0001.xml`; each succeeds. Remove the generated `mapping-0001.csv`
  and `id2iri_*.json` afterwards
- [ ] `POST $API_URL/v3/export/resources/oai` with body `{"shortcode":"4123"}` and the root token; every
  original-file link in the output starts with `http://0.0.0.0:3333/ingest/projects/4123/assets/` and ends with
  `/original`
- [x] Download one of those links with the root token; it answers `200`, and the body's `shasum -a 256` equals the
  source file's in `testdata/bitstreams/`
- [x] Pick a project 4123 resource with a file that `GET $API_URL/v2/resources/<url-encoded IRI>` returns without
  a token. `GET $API_URL/fair/resources/4123/<resourceId>/metadata.jsonld` contains an original link under
  `http://0.0.0.0:3333/ingest/`, and that link answers `200` without a token
- [x] Export project 4123: `POST $API_URL/v3/projects/<url-encoded project IRI>/exports`, poll
  `GET .../exports/<id>` until it completes, then save `GET .../exports/<id>/download` as a zip; the zip contains the
  project's assets
- [x] `DELETE $API_URL/admin/projects/shortcode/4123/erase` answers `200`, and `$STACK_DIR/sipi/images/4123` holds
  no assets
- [ ] Import the zip with `POST $API_URL/v3/projects/<url-encoded project IRI>/imports`
  (`Content-Type: application/zip`) and poll `GET .../imports/<id>` until it completes; the original link from the
  OAI step answers `200` again
- [ ] In `$STACK_DIR`, run `docker compose --env-file versions.env stop app`. Then in
  `/Users/raitisveinbahs/work/dsp-app`, on the branch with plan 03 complete, run `npm ci` and `npm run start-local`
- [x] With `eng:test-browser` on `http://localhost:4200`, log in as `root@example.com`/`test` and upload an image
  above 1 MiB to a new still-image resource of project 4123. The network log shows
  `POST http://0.0.0.0:3333/ingest/projects/4123/assets/ingest/<name>` answered `200` after a passing CORS
  preflight, no request goes to port 3340, and the resource then displays the image
  (Run 2026-10-28 from the page at `http://localhost:4200` with `fetch`, not the UI: the pre-existing dev server's
  build was stale. A 2.3 MB authenticated upload to `http://0.0.0.0:3333/ingest/projects/4123/...` answered `200`
  after the CORS preflight, and its asset info read back `200`.)

### Stage 3: Old ingest URLs through a local proxy

- [x] Write `$SCRATCH/dynamic.yml` with one router (rule ``PathPrefix(`/`)``, entrypoint `ingest`), one `addPrefix`
  middleware with prefix `/ingest`, and one service with server URL `http://host.docker.internal:3333`
- [x] Start the proxy:

  ```bash
  docker run -d --name ingest-proxy --add-host host.docker.internal:host-gateway -p 3340:3340 \
    -v "$SCRATCH/dynamic.yml:/etc/traefik/dynamic.yml:ro" traefik:v3.6.4 \
    --entrypoints.ingest.address=:3340 --providers.file.filename=/etc/traefik/dynamic.yml --accesslog=true
  ```

- [x] `curl -fsS -o /dev/null -w '%{http_code}' $PROXY_URL/health` prints `200` (REQ-2.4)
- [x] Create a file above 1 MiB with
  `head -c 5242880 /dev/urandom > "$SCRATCH/blob" && (cd "$SCRATCH" && zip -0 big.zip blob)`. Uploading it with
  `curl -X POST --data-binary @"$SCRATCH/big.zip" -H 'Content-Type: application/octet-stream'` to
  `$PROXY_URL/projects/4123/assets/ingest/big.zip` without a token answers `401`
- [x] The same upload with `-H "Authorization: Bearer $TOKEN"` answers `200` and returns an asset id, so
  `Authorization` and `Content-Length` reach dsp-api
- [x] `GET $PROXY_URL/projects/4123/assets/<id>` with the token returns the same JSON as
  `GET $API_URL/ingest/projects/4123/assets/<id>`
- [ ] `GET $PROXY_URL/projects/4123/assets/<id>/original` with the token answers `200`, its body's
  `shasum -a 256` equals `big.zip`'s, and its `Content-Length` header equals the one from
  `$API_URL/ingest/projects/4123/assets/<id>/original` (or both lack one)
- [x] The original link from Stage 2's OAI step, requested on `$PROXY_URL` without the `/ingest` prefix (the shape
  of a link published before the move), answers `200` with the same body
- [x] `GET $PROXY_URL/v2/resources/<any IRI>` answers `404`, so the proxy reaches nothing outside `/ingest/`

### Stage 4: Released dsp-tools through the proxy

- [x] `uvx --from dsp-tools dsp-tools --version` prints the latest PyPI release, which predates the plan 04 change
  and still sends ingest calls for a local server to `http://0.0.0.0:3340`
- [x] From the dsp-tools checkout, run
  `uvx --from dsp-tools dsp-tools create testdata/json-project/generic-e2e-project-4125.json`; it succeeds
- [x] Run `uvx --from dsp-tools dsp-tools xmlupload testdata/xml-data/test-data-e2e-4125.xml`; it passes its ingest
  health check and uploads every file, and `docker logs ingest-proxy` shows the uploads
- [x] One asset the released client uploaded downloads from
  `$API_URL/ingest/projects/4125/assets/<id>/original` with the root token, answering `200`

### Stage 5: Tear down

- [x] Run `docker rm -f ingest-proxy` and delete `$SCRATCH`
- [x] Stop the dsp-app dev server, then run `uv run dsp-tools stop-stack` from the dsp-tools branch; it removes the
  stack and its volumes
- [x] Run `docker rmi daschswiss/knora-api:$API daschswiss/knora-sipi:$API`, so a later `start-stack` pulls the
  published images
- [x] If Stage 1 edited `versions.env`, run
  `git -C /Users/raitisveinbahs/work/dsp-tools checkout -- src/dsp_tools/resources/start-stack/versions.env`;
  `git -C /Users/raitisveinbahs/work/dsp-tools status --short` then shows no change from this run

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Merge the dsp-api PR (plan 02) and cut a dsp-api release that serves `/ingest/` and publishes `knora-api`, `knora-sipi` and `apache-jena-fuseki` | dsp-api maintainer | after ship | Merging and releasing are the owner's decision |
| H2 | Re-run the dsp-app PR's `DSP-APP E2E tests` against the new dsp-api `main` and merge the dsp-app PR (plan 03) right after H1. dsp-app's CI starts dsp-api's compose with `sipi ingest api`, which fails on `main` once dsp-api drops the `ingest` service | dsp-app maintainer | after ship | Merging is the owner's decision |
| H3 | Merge the ops-deploy PR (plan 05) only after the H1 release exists, with `versions/RELEASE-DEV.json` pointing `api` at it; dev auto-deploys from `main` | DSP release owner | after ship | Merge and release ordering are the owner's decision |
| H4 | After the dev deploy from H3, run the DEV smoke checks below; continue only if they all pass | DSP release owner (or an agent given dev credentials) | after ship | Needs dev credentials and the deploy from H3 |
| H5 | Deploy the release to stage, then prod, as plan 05's H2 describes | Ops | after ship | Deploying needs credentials and production approval |
| H6 | Once prod serves `/ingest/`, pin `versions.env` in the dsp-tools PR (plan 04) to the release prod runs, let CI pass and merge it; then release dsp-tools (plan 04 H2, H3) | dsp-tools owner | after ship | PRD constraint: merged only once prod serves `/ingest/`; timing is the owner's decision |

### DEV smoke checks (H4)

With a token for a dev user who may write to a dev test project `<sc>`:

- `https://ingest.dev.dasch.swiss/health` and `https://api.dev.dasch.swiss/ingest/health` answer `200`.
- An upload of a file above 1 MiB to `https://api.dev.dasch.swiss/ingest/projects/<sc>/assets/ingest/<name>` and one
  to `https://ingest.dev.dasch.swiss/projects/<sc>/assets/ingest/<name>` both answer `200`.
- Each uploaded asset's `.../assets/<id>/original` downloads with a matching checksum from both hosts.
- A public resource's `https://api.dev.dasch.swiss/fair/resources/<sc>/<id>/metadata.jsonld` links its original
  under `https://api.dev.dasch.swiss/ingest/`, and that link answers `200`. An original link published before the
  move (`https://ingest.dev.dasch.swiss/projects/...`) still answers `200`.
- In dsp-app on dev (`https://app.dev.dasch.swiss`), uploading a file to a resource shows a `POST` to
  `https://api.dev.dasch.swiss/ingest/...` answered `200`.
- The released dsp-tools (`uvx --from dsp-tools dsp-tools xmlupload --server https://api.dev.dasch.swiss ...`), which
  still sends ingest calls to `ingest.dev.dasch.swiss`, uploads a small XML with one file.
- `docker service ls` on the dev host lists no `DSP_iiif_ingest` service.

## Acceptance Criteria

- [ ] On the local branch stack, dsp-app and dsp-tools (`xmlupload` and the
  `upload-files`/`ingest-files`/`ingest-xmlupload` flow) upload through `/ingest/` with no ingest container running
  (PRD US-1, REQ-4.4)
- [ ] `/ingest/health` answers `200` directly and through the proxy (REQ-2.4)
- [ ] FAIR metadata and OAI export emit original links under `<api>/ingest/`, and they resolve (REQ-2.3)
- [ ] Project export, erase and import through dsp-api succeed with no ingest container (REQ-4.3)
- [ ] Through the `addprefix` proxy, upload, asset info, original download and old-shape links work, a missing
  token is `401`, and nothing outside `/ingest/` is reachable (REQ-2.1, REQ-2.2)
- [ ] The released dsp-tools uploads through the proxy without any change (PRD US-2)
- [ ] Sampled conversion processes in the running container have `oom_score_adj` 1000 (REQ-3.4 sanity check)
- [ ] The merge, release and deploy order in Human Actions is followed, and the DEV smoke checks pass before stage
  and prod

## Dependencies & Risks

- **Preconditions.** Plans 02, 03 and 04 are complete on their branches, and plan 05's rendered labels define the
  `{{ STACK }}-ingest-addprefix` middleware this plan mirrors. Docker Desktop has room for the stack plus the
  larger api image.
- **Version skew in the local stack.** If the newest release's ontologies differ from the branch's `knora-base`,
  dsp-api upgrades or rejects the repository at startup. Then the run uses the dsp-tools branch with the H1 release
  instead, after H1.
- **Released client vs. main's test data.** The released dsp-tools may reject XML features newer than its release.
  If so, Stage 4 uses the test data at its release tag (`git -C <dsp-tools> show <tag>:<path>`).
- **CI window between H1 and H2.** dsp-app `main` is red from the dsp-api merge until the dsp-app PR merges. Plan 03
  names the alternative: dsp-api keeps the compose `ingest` service for one extra merge.
- **Deploy coupling.** After H3, any environment deployed with an `api` version that predates `/ingest/` loses
  uploads and the `ingest.<domain>` host. H5 deploys the same release everywhere.

## Success Metrics

- Every checkbox in Stages 1 to 4 passes on one run of the local branch stack.
- The DEV smoke checks (H4) pass before H5.
- After H6, no DaSCH client calls the `ingest.<domain>` host by default, and the host still answers for old clients
  and published links.

## References

- PRD: `01-move-ingest-into-webapi-PRD.md` (US-1, US-2, REQ-3.4, REQ-4.x, constraints, success criteria)
- Plans: `02-feat-move-ingest-into-webapi-plan.md` ("Cross-repo contracts"), `03-feat-ingest-urls-dsp-app-plan.md`,
  `04-feat-ingest-urls-dsp-tools-plan.md`, `05-feat-ingest-into-api-ops-deploy-plan.md`
- dsp-api: `justfile` (`docker-build`), `slice/fair/api/FairEndpoints.scala`, `slice/api/v3/export/ExportEndpoints.scala`,
  `slice/api/v3/projects/V3ProjectsEndpoints.scala`, `slice/api/admin/ProjectsEndpoints.scala` (`erase`)
- dsp-tools: `src/dsp_tools/commands/start_stack/start_stack.py` (`--env-file versions.env`, `--latest` pulls),
  `src/dsp_tools/resources/start-stack/`, `testdata/`
- dsp-app: `package.json` (`start-local`)
- ops-deploy: `traefik.yml` (`traefik_version`), `roles/dsp-deploy/templates/docker-compose-svc.yml.j2`
- Traefik AddPrefix middleware: <https://doc.traefik.io/traefik/middlewares/http/addprefix/>
