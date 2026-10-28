---
plan: docs/specs/2026-10-20-move-ingest-into-webapi/06-test-ingest-cross-repo-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: f143b97df
branch: feat/serve-ingest-from-dsp-api
started: 2026-10-28
problem: >
  The four per-repo plans (02 dsp-api, 03 dsp-app, 04 dsp-tools, 05 ops-deploy) each test their own repository
  against fixtures of the others. This test plan runs the branches together on one local stack: dsp-api's images
  under dsp-tools' start-stack, dsp-app's dev server and dsp-tools' upload commands on /ingest/, old
  ingest.<domain> URLs through a local Traefik addPrefix proxy, and the released dsp-tools through that proxy.
symptoms:
status: in-progress
---

# Execution Journal: 06-test-ingest-cross-repo-plan

Test-only plan: no code commits. Each chunk is one stage of checks; its row records pass/fail per check.
Branches: dsp-api `feat/serve-ingest-from-dsp-api` (f143b97df), dsp-tools `feat/ingest-under-api-path` (88eabdc3),
dsp-app `feat/ingest-under-api-path` (da96504af).

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | — (local stack) | — | Stage 1, all 7 | stack runs branch images; /ingest/health 200; no dsp-ingest, nothing on 3340 | plan § Stage 1 | — |
| 2 | — (dsp-tools testdata, dsp-app) | 1 | Stage 2, all but the `eng:test-browser` check (session's) | uploads, OOM sample, OAI/FAIR links, export/erase/import pass; dev server on :4200 | plan § Stage 2 | — |
| 3 | — (Traefik on 3340) | 2 | Stage 3, all 8 | proxy upload/info/original/old links work, 401 without token, 404 outside /ingest/ | plan § Stage 3 | — |
| 4 | — (released dsp-tools) | 3 | Stage 4, all 4 | released client uploads through the proxy | plan § Stage 4 | — |

Stage 5 (tear down) and the browser check are run by the session.

## Chunks

| id | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- |
| 1 | complete | — | 7/7 pass. `just docker-build` loaded both images; `API=v40.4.0` exists on GitHub, so `versions.env` was not edited; retagged `:v40.4.0`; start-stack reported the API on 3333; api container image = `knora-api:latest` (sha256:56a09792…); no dsp-ingest image in the stack, nothing on host port 3340; `/ingest/health` 200 | none |
| 2 | blocked | — | 10/13 in scope pass, 2 fail, 1 blocked (details below) | dev server: port 4200 held by a dsp-app `nx serve` started 2026-10-13 (PID 64779); stopping it was denied by the permission classifier |
| 3 | complete | — | 7/8 pass, 1 fails as written (details below); proxy `ingest-proxy` (traefik:v3.6.4) left running on 3340 | none |
| 4 | complete | — | 4/4 pass. PyPI dsp-tools 19.3.0.post12 (sends ingest to `http://0.0.0.0:3340`); `create` 4125 needs `DSP_USER_PASSWORD` (testerProjectAdmin has `password: null`); xmlupload: health check and 4 POSTs (`test.jpg/.pdf/.png/.jpeg`) in `docker logs ingest-proxy`, all 200; asset `3hZPqVFFyE1-tvrlN0CO1FM` original 200 from `$API_URL/ingest/`, sha256 = `testdata/bitstreams/test.jpg` | none |

### Stage 2 results

- pass: `create` 4123; OOM poll started.
- pass: `xmlupload` 4123 (81 resources, 48 files). dsp-api writes no access log, so `docker logs $API_CTR` has no
  `POST /ingest/...` lines. Two other sources show the requests instead: the client log (`~/.dsp-tools/logs/2026-10-28_14-05-08-748_logging.log`)
  has 48 `POST http://0.0.0.0:3333/ingest/projects/4123/assets/ingest/<file>` requests, and the api container logs 48
  `IngestServiceLive` "Successfully ingesting" lines.
- pass: `oom.txt`: 5 `sipi 1000`, 1 `ffprobe 1000`, no other value.
- pass: e2e-sample-project `create`, `upload-files` (to `http://0.0.0.0:3333/ingest`), `ingest-files 0001`,
  `ingest-xmlupload`; generated `mapping-0001.csv`, `id2iri_*.json`, `warnings.log` removed.
- FAIL: `POST /v3/export/resources/oai {"shortcode":"4123"}` never answers. Fuseki receives ~16k queries/min,
  re-reading the same two pairs of mutually linked resources (`VALUES ?resource { <…/cEoku7A4S1Cl_YxAZ0c8Kg>
  <…/7hpBE_NvQ3-46hJ-BQiX3w> }` and `{ <…/e4h6jkzQRkeetjJhU7cVpA> <…/Zyx-Qa_HRYeRF4uPVr54Xg> }`, ~2000 times each
  per 30 s). The loop stops when the client disconnects. It is in `readResourcesSequencePar`, which the branch does not
  change, so it predates this branch. The same call for project 0001 answers 200 and its links have the decided shape,
  `http://0.0.0.0:3333/ingest/projects/0001/assets/<id>/original`.
- pass: original download, with a link of the OAI shape taken from `image_thing_0`'s file value:
  `…/ingest/projects/4123/assets/3MtIWiVEDLy-01EiHSHm7Jq/original` 200; its sha256 equals `testdata/bitstreams/test.tif`'s.
- pass: FAIR `metadata.jsonld` for `4123/SdI1bcTNTPuw9U0MxtNqmw` links
  `http://0.0.0.0:3333/ingest/projects/4123/assets/3MtIWiVEDLy-01EiHSHm7Jq/original`, which answers 200 without a token.
  The FAIR routes first answered 404: they are off unless `KNORA_WEBAPI_FAIR_APP_EXTERNAL_BASE_URL` is set, and
  start-stack does not set it. This run added `$STACK_DIR/docker-compose.override.yml` setting it to
  `http://localhost:4200` and recreated the api container.
- pass: the export completes; the zip holds `data/assets/assets.zip` with 148 entries, against 144 files under
  `sipi/images/4123`.
- pass: erase answers 200; `$STACK_DIR/sipi/images/4123` no longer exists.
- FAIL: the import reaches `status: failed` with `"Import contains the root user 'http://rdfh.ch/users/root'. Resources
  referencing root require pre-migration cleanup."`. That rule is in `ProjectMigrationImportService`, unchanged on
  the branch. It fires because dsp-tools uploads as root. The original link then answers 404. To restore project 4123,
  this run re-ran `create` and `xmlupload` with the dsp-tools branch. The project's new IRI is
  `http://rdfh.ch/projects/ZizzJiZlQrm-EUnNoG6mMA`, and `image_thing_0` (`http://rdfh.ch/4123/d4_BhclvR6iQdYLJ75zHiQ`)
  now has asset `3YnusEtQS6L-mN3NAMzvuEU`, whose original answers 200 with the `test.tif` checksum.
- BLOCKED: the `app` container is stopped. `npm ci` and `npm run start-local` were not run: port 4200 is held by an
  `nx run dsp-app:serve:development --host 0.0.0.0 --allowed-hosts host.docker.internal` in the dsp-app checkout,
  started 2026-10-13 (PIDs 64657→64658→64760→64779), and killing it was denied. It serves the branch config
  (`ingestUrl: http://0.0.0.0:3333/ingest`), but its `node_modules` predates 22 dependency updates on the branch.

### Stage 3 results

- pass: `dynamic.yml` written; proxy started; `$PROXY_URL/health` 200.
- pass: a 5 MiB `big.zip` upload with no token answers 401 (`Invalid value for: header Authorization (missing)`).
  With the token it answers 200 with asset id `19sMjIld4yO-DjN3ENhVt5N` and `sizeOriginal` 5243038.
- pass: asset info via the proxy is byte-identical to `$API_URL/ingest/projects/4123/assets/<id>`.
- FAIL as written: the `big.zip` original answers 500 `{"errorMessage":"error fetching permissions"}`, through the
  proxy and directly. The asset belongs to no resource, so `getProjectsAssetsOriginal`'s permission lookup fails
  (`modules/ingest/.../ProjectsEndpointsHandler.scala:124`, unchanged on the branch). The same check on an asset that
  belongs to a resource (`3YnusEtQS6L-mN3NAMzvuEU`) passes: proxy and direct both answer 200 with the `test.tif`
  sha256, and both lack `Content-Length` (chunked).
- pass: the old-shape link `$PROXY_URL/projects/4123/assets/3YnusEtQS6L-mN3NAMzvuEU/original` (the link of OAI shape
  from Stage 2) answers 200 without a token, and its body is byte-identical to the direct download.
- pass, with a note: `/v2/resources/abc`, `/admin/projects` and `/v2/authentication` on the proxy answer 404 (dsp-api
  receives `/ingest/...`). A URL-encoded IRI (`/v2/resources/http%3A%2F%2F…`) answers 400 from Traefik itself, which
  rejects encoded slashes and has no access-log entry for it. Either way nothing outside `/ingest/` is reached.

## Deferrals

- Session: the Stage 2 dev-server checkbox (stop or keep the 2026-10-13 `nx serve` on 4200, then `npm ci` +
  `npm run start-local`), the browser check, and Stage 5. Stage 5 should also delete
  `$STACK_DIR/docker-compose.override.yml`. Stage 1 did not edit `versions.env`, so it needs no restore.

## Deviations

- none

## Side findings

- OAI export (`/v3/export/resources/oai`) loops forever on projects with mutually linked resources. The systematic
  4123 data triggers it, and the loop predates this branch. Worth an issue.
- Project import refuses any project whose data references the root user. Every project dsp-tools uploads as root
  hits this, so plan 06's export, erase and import round trip cannot pass on dsp-tools test data as written.
- `/ingest/projects/<sc>/assets/<id>/original` answers 500 ("error fetching permissions") for an asset that belongs to
  no resource, even for root. A 404 or 403 would be more accurate. The behaviour predates this branch.
- start-stack never enables dsp-api's FAIR routes (`KNORA_WEBAPI_FAIR_APP_EXTERNAL_BASE_URL` is unset).
- Traefik v3.6.4 answers 400 to `%2F` in paths before routing. ops-deploy's `ingest.<domain>` router has the same
  behaviour today, so this is not a regression.
- dsp-api writes no HTTP access log, so plan checks that grep `docker logs $API_CTR` for request lines cannot pass.
  Use the client log or the ingest service log lines instead.
- The released dsp-tools 19.3.0.post12 needs `DSP_USER_PASSWORD` set to create `generic-e2e-project-4125.json`.

## Session follow-up (2026-10-28)

- Browser check: done from the app origin with `fetch` (stale dev server PID 64779 had a broken build); CORS and a 2.3 MB upload to `/ingest/` pass.
- Stage 5 done: proxy removed, compose override deleted, stack stopped, local `v40.4.0` tags removed, `versions.env` untouched.
- Failures judged not caused by this work: OAI 4123 hang (`readResourcesSequencePar`), import refused for the root user (existing rule), `big.zip` original `500` for an asset with no file value (same lookup as the old `/admin/files` path).
