---
title: "feat: dsp-tools reaches ingest under /ingest/ on the API host"
type: feat
date: 2026-10-26
author: "Raitis Veinbahs"
status: draft
repository: /Users/raitisveinbahs/work/dsp-tools
repositories:
  - name: dsp-tools
    path: /Users/raitisveinbahs/work/dsp-tools
prd: 01-move-ingest-into-webapi-PRD.md
---

<!-- markdownlint-configure-file { "MD024": { "siblings_only": true } } -->

# feat: dsp-tools reaches ingest under /ingest/ on the API host

## Overview

dsp-tools stops talking to a separate ingest server. Every ingest call goes to `<server>/ingest/...` on the API
host, with the paths after the prefix unchanged. `start-stack` and the e2e testcontainers no longer run the
`daschswiss/dsp-ingest` container or its SQLite database. The API container serves ingest instead and gets
ingest's storage settings and volumes.

The PR is opened as part of this work but merged only once prod serves `/ingest/` (PRD constraint). Older
dsp-tools releases keep working through the permanent `ingest.<domain>` proxy, so this release needs no fallback.

## Problem Statement / Motivation

The PRD moves dsp-ingest into dsp-api under `/ingest/` and stops publishing the standalone `dsp-ingest` image
(REQ-4.5). dsp-tools currently:

- derives the ingest URL as `https://ingest.<domain>` remotely and `http://0.0.0.0:3340` locally;
- runs `daschswiss/dsp-ingest:${API}` in `start-stack` and in the e2e testcontainers, with `DB_JDBC_URL` and an
  `ingest-db` volume.

Once a dsp-api release ships without the ingest image, `start-stack` and the e2e suite on that version can't
start. The remote `ingest.<domain>` host keeps working through the proxy, but the PRD's goal is that DaSCH
clients use one address.

## Proposed Solution

The base URL is the only thing that changes. All three ingest clients build their URLs as
`f"{dsp_ingest_url}/projects/..."`, so pointing `dsp_ingest_url` at `<server>/ingest` moves every call without
touching the client code:

| Call site (origin/main) | Path after the base URL (unchanged) |
| --- | --- |
| `src/dsp_tools/clients/ingest.py:98` (`DspIngestClientLive._ingest`) | `/projects/{sc}/assets/ingest/{filename}` |
| `src/dsp_tools/clients/bulk_ingest_client.py:110` | `/projects/{sc}/bulk-ingest/ingest/{file}` |
| `src/dsp_tools/clients/bulk_ingest_client.py:114` | `/projects/{sc}/bulk-ingest` |
| `src/dsp_tools/clients/bulk_ingest_client.py:160` | `/projects/{sc}/bulk-ingest/mapping.csv` |
| `src/dsp_tools/cli/utils.py:110` (`_check_ingest_health`) | `/health` |

**URL derivation.** `get_canonical_server_and_dsp_ingest_url` (`src/dsp_tools/cli/utils.py:128`) derives the
ingest URL from the canonical server, as `f"{server}/ingest"`:

| Input | Server | Ingest URL (new) |
| --- | --- | --- |
| `localhost:3333`, `0.0.0.0:3333` | `http://0.0.0.0:3333` | `http://0.0.0.0:3333/ingest` |
| `admin.`/`app.`/`api.`/`ingest.`/bare `<x>.dasch.swiss` | `https://api.<x>.dasch.swiss` | `https://api.<x>.dasch.swiss/ingest` |

The `default_dsp_ingest_url` parameter goes away (here and in `entry_point._derive_dsp_ingest_url`), since the
ingest URL is no longer independent of the server. `ingest.<x>.dasch.swiss` stays accepted as *input* and is
canonicalised to the API host like the other subdomains.

```python
# src/dsp_tools/cli/utils.py (sketch)
LOCALHOST_API = "http://0.0.0.0:3333"
LOCALHOST_INGEST = f"{LOCALHOST_API}/ingest"

def get_canonical_server_and_dsp_ingest_url(
    server: str, default_dsp_api_url: str = LOCALHOST_API
) -> tuple[str, str]:
    ...
    if localhost_match:
        server = default_dsp_api_url
    elif remote_url_match:
        server = f"https://api.{remote_url_match.group(1)}.swiss"
    else:
        raise CliUserError(f"Invalid DSP server URL '{server}'")
    dsp_ingest_url = f"{server}/ingest"
    ...
```

**Explicit ingest URL.** No `--dsp-ingest-url` CLI flag exists on origin/main: `_derive_dsp_ingest_url` always
overwrites `parsed_arguments.dsp_ingest_url`. The decision is to add no flag. The programmatic override stays:
`ServerCredentials.dsp_ingest_url` and `NetworkRequirements.ingest_url` remain separate fields, so a caller that
builds `ServerCredentials` itself (tests, library use) can still point ingest anywhere, including at the old
`ingest.<domain>` proxy. The `ServerCredentials.dsp_ingest_url` default becomes `http://0.0.0.0:3333/ingest`.

**Health check.** Keep the separate ingest health check, now at `<server>/ingest/health` (REQ-2.4/REQ-1.2 keep
`/health` on the ingest slice). Against a dsp-api that predates the move, it fails fast with
`IngestNotReachableError` instead of failing mid-upload. That error's local message, which tells the user to
check "the ingest container", now says that the local DSP-API must be a version that serves `/ingest/` and
suggests restarting with `dsp-tools start-stack`.

**start-stack.** The `ingest` service, its `ingest-db` volume and port 3340 are removed from
`docker-compose.yml`, `docker-compose.override.yml` and `docker-compose.override-host.j2`. The `api` service takes
over ingest's storage settings and volumes (see *start-stack api service* below). The dsp-app config files point
`ingestUrl` at `http://<host>:3333/ingest`.

**e2e testcontainers.** The ingest testcontainer, its port, its name, its image version and its `ingest_db`
artifact dir are removed. The API testcontainer gets the same ingest settings and volumes as the start-stack api
service, and `creds` points at `http://0.0.0.0:{ports.api}/ingest`.

### start-stack api service

This follows the dsp-api plan's cross-repo contract. dsp-api reads ingest's settings from its own `ingest.conf`
under an `INGEST_` prefix (REQ-4.1); the old unprefixed ingest variables are ignored, and unprefixed
`ALLOW_ERASE_PROJECTS` drives only dsp-api's own erase flag.

```yaml
  api:
    image: daschswiss/knora-api:${API}   # API = a dsp-api release that serves /ingest/ (see H1, H2)
    depends_on: [sipi, db]
    ports:
      - "3333:3333"                       # 3340 is gone; ingest is under :3333/ingest
    volumes:
      - ./sipi/images:/opt/images         # read-write: ingest writes assets here; sipi reads the same host dir
      - ./sipi/tmp-dsp-ingest:/opt/temp    # ingest temp dir; bulk ingest reads <temp>/import/<shortcode>
    environment:
      # ...existing KNORA_WEBAPI_* settings unchanged, except:
      # removed: KNORA_WEBAPI_DSP_INGEST_BASE_URL=http://ingest:3340   (dsp-api no longer reads it)
      - KNORA_WEBAPI_DSP_INGEST_AUDIENCE=http://localhost:3340       # unchanged; dsp-api mints and checks it
      - INGEST_STORAGE_ASSET_DIR=/opt/images
      - INGEST_STORAGE_TEMP_DIR=/opt/temp
      - INGEST_ALLOW_ERASE_PROJECTS=true  # ingest-side erase gate (ingest container set ALLOW_ERASE_PROJECTS)
      - ALLOW_ERASE_PROJECTS=true         # already set on api today; drives dsp-api's own flag only
      # INGEST_SIPI_USE_LOCAL_DEV is left unset (default false): the Sipi CLI shipped in the api image is used
```

Dropped with the ingest container: `SERVICE_HOST`, `SERVICE_PORT`, `SERVICE_LOG_FORMAT`, `JWT_AUDIENCE`,
`JWT_ISSUER`, `JWT_SECRET` (dsp-api's own authentication applies), `DB_JDBC_URL`, `DSP_API_URL`, `STORAGE_*`,
`SIPI_USE_LOCAL_DEV`, and the custom-host `JWT_ISSUER` override. `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR` and
`KNORA_WEBAPI_DSP_INGEST_EXTERNAL_BASE_URL` are not set either: dsp-api removes them and reads the asset directory
from `INGEST_STORAGE_ASSET_DIR`. `INGEST_BULK_MAX_PARALLEL` and `INGEST_EXECUTOR_CPU_SHARE` (default `0.5`) keep
their defaults. The host directories `./sipi/images` and `./sipi/tmp-dsp-ingest` keep their current paths
in `~/.dsp-tools/start-stack`, so a running user's existing assets stay where they are. No memory limit is set in
start-stack today, and none is added.

## Technical Considerations

- **Filename built from the ingest URL.** `UploadFailures.execute_error_protocol`
  (`src/dsp_tools/commands/ingest_xmlupload/upload_files/upload_failures.py:43`) strips the scheme from
  `dsp_ingest_url` and puts the rest into a CSV filename. With `api.dasch.swiss/ingest`, the `/` makes
  `Path("upload_failures_0001_api.dasch.swiss/ingest.csv")` point into a directory that doesn't exist, and the
  write fails exactly when more than `maximum_prints` uploads have failed. The `/` has to be replaced (e.g. by
  `_`) before the URL goes into the filename. This is the one behavioural change the new base URL forces outside
  the CLI layer.
- **Uploads send `Content-Length`.** dsp-api buffers chunked request bodies and answers `413` to a chunked upload
  over 1 MiB. Both upload paths (`DspIngestClientLive._ingest`, `BulkIngestClient.upload_file`) pass an open file
  object as `data=` to `requests`, which sets `Content-Length` from the file size, so no change is needed. Phase 2's
  manual run and Phase 3's e2e uploads exercise it with files above 1 MiB.
- **Client code is unchanged.** `DspIngestClientLive`, `BulkIngestClient` and `resume_xmlupload` take the base
  URL from `creds.dsp_ingest_url` and need no edit. `test/unittests/clients/test_bulk_ingest_client.py` uses
  `DSP_INGEST_URL = "https://example.com"`; changing it to `https://example.com/ingest` makes the tests show the
  prefix is carried through.
- **versions.env sequencing.** `versions.env` drives the start-stack images, the e2e images
  (`_get_image_versions`) and the dsp-api ref from which `sipi.docker-config.lua` is downloaded. The first dsp-api
  release without the ingest image breaks today's compose (it still references `daschswiss/dsp-ingest:${API}`).
  The automated bump PR for that release would therefore go red, and only this PR makes it green. This PR sets
  `API`/`DB` itself to a dsp-api release tag that serves `/ingest/` (a real git tag, since the e2e setup and
  `_get_url_prefix` fetch files from `raw.githubusercontent.com/dasch-swiss/dsp-api/<tag>/`). At merge time, the
  tag is moved to the release prod runs (H2).
- **dsp-app config.** The `ingestUrl` in `dsp-app-config.json` gains a path (`/ingest`). The `APP` version in
  `versions.env` must be a dsp-app release that accepts a path in `ingestUrl` (the dsp-app part of this PRD).
- **Repo rule on test edits.** dsp-tools' `CLAUDE.md` asks for confirmation before adjusting a test. The test
  edits listed in this plan (expected URLs, the testcontainer layout, the start-stack override assertions) are
  approved by this plan. Any other test change still needs that confirmation.
- **Conventions.** `src/dsp_tools/cli/**` follows `src/dsp_tools/cli/CLAUDE.md`, and
  `src/dsp_tools/commands/ingest_xmlupload/upload_files/**` follows its own `CLAUDE.md` (per `eng.yaml`). Commits
  and the PR title follow Conventional Commits (`CONVENTIONS.md`), e.g.
  `feat: reach ingest under /ingest/ on the API host`.

## Implementation Phases

### Phase 1: Ingest URL derived as `<server>/ingest`

#### dsp-tools

- [x] `src/dsp_tools/cli/utils.py`: `LOCALHOST_INGEST` becomes `http://0.0.0.0:3333/ingest`
- [x] `src/dsp_tools/cli/utils.py`: `get_canonical_server_and_dsp_ingest_url` derives the ingest URL as
  `f"{server}/ingest"`, drops the `default_dsp_ingest_url` parameter, and has its docstring rewritten for the new
  derivation
- [x] `src/dsp_tools/cli/entry_point.py`: remove `default_dsp_ingest_url` from `run` and `_derive_dsp_ingest_url`
- [x] `src/dsp_tools/cli/args.py`: `ServerCredentials.dsp_ingest_url` default becomes `http://0.0.0.0:3333/ingest`
- [x] `src/dsp_tools/cli/exceptions.py`: the local `IngestNotReachableError` message stops referring to an
  ingest container and says the local DSP-API must serve `/ingest/` (restart with `dsp-tools start-stack`)
- [x] `src/dsp_tools/commands/ingest_xmlupload/upload_files/upload_failures.py`: the CSV filename built from
  `dsp_ingest_url` replaces `/` so it stays a single file name; unit test for an `https://api.dasch.swiss/ingest`
  URL with more than `maximum_prints` failures
- [x] `test/unittests/cli/test_helper_functions.py`: expected ingest URLs become `https://api.<x>.dasch.swiss/ingest`
  and `http://0.0.0.0:3333/ingest`, including the `ingest.<x>.dasch.swiss` input rows
- [x] `test/unittests/cli/test_utils.py`: ingest health-check expectations become `.../ingest/health`
- [x] `test/unittests/cli/test_cli_with_network.py`: every expected `dsp_ingest_url` becomes `http://0.0.0.0:3333/ingest`
  or `server + "/ingest"` (replacing `server.replace("api", "ingest")` and `https://ingest.<x>.dasch.swiss`)
- [x] `test/unittests/clients/test_bulk_ingest_client.py`: `DSP_INGEST_URL` becomes `https://example.com/ingest`
- [x] `test/integration/commands/ingest_xmlupload/test_ingest_xmlupload.py`: `dsp_ingest_url` becomes
  `http://0.0.0.0:3333/ingest`
- [x] `src/dsp_tools/cli/CLAUDE.md`: the URL-derivation bullets (lines ~214-217) describe `<server>/ingest`
- [x] `src/dsp_tools/commands/ingest_xmlupload/upload_files/CLAUDE.md`: `creds.dsp_ingest_url` described as the
  `/ingest` base URL on the API host
- [x] `docs/developers/start-stack.md`: the subdomain section says ingest is reached at `https://api.<domain>/ingest`
  and that `ingest.<domain>` input is still accepted
- [x] `ARCH-MAP.md`: `ingest_xmlupload`'s durable-state line names `/ingest/` on dsp-api instead of a dsp-ingest server
- [x] Run `just unittests`; it passes
- [x] Run `just integration-tests`; it passes
- [x] Run `just lint`; it passes
- [x] Run `change-blast-radius` on the phase's changes; no unhandled ingest-URL consumer reported
- [x] Run `update-docs` on the phase's changes; no missing user-facing doc update reported
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: start-stack runs without the ingest container

#### dsp-tools

- [ ] `src/dsp_tools/resources/start-stack/versions.env`: `API` (and `DB`, which dsp-api tags in lockstep) set to the
  dsp-api release from H1
- [x] `src/dsp_tools/resources/start-stack/docker-compose.yml`: remove the `ingest` service and the `ingest-db`
  volume
- [x] `src/dsp_tools/resources/start-stack/docker-compose.yml`: remove `KNORA_WEBAPI_DSP_INGEST_BASE_URL` from `api`
- [x] `src/dsp_tools/resources/start-stack/docker-compose.yml`: add to `api` the ingest env vars from
  *start-stack api service* (`INGEST_STORAGE_ASSET_DIR`, `INGEST_STORAGE_TEMP_DIR`, `INGEST_ALLOW_ERASE_PROJECTS=true`)
- [x] `src/dsp_tools/resources/start-stack/docker-compose.yml`: mount `./sipi/images:/opt/images` and
  `./sipi/tmp-dsp-ingest:/opt/temp` read-write on `api` (`/opt/temp`: dsp-api's `ingest.conf` default)
- [x] `src/dsp_tools/resources/start-stack/docker-compose.override.yml`: remove the `ingest` image override
- [x] `src/dsp_tools/resources/start-stack/docker-compose.override-host.j2`: remove the `ingest` block
- [x] `src/dsp_tools/resources/start-stack/dsp-app-config.json`: `ingestUrl` becomes `http://0.0.0.0:3333/ingest`
- [x] `src/dsp_tools/resources/start-stack/dsp-app-config.override-host.j2`: `ingestUrl` becomes
  `http://{{ CUSTOM_HOST }}:3333/ingest`
- [x] `src/dsp_tools/cli/create_parsers.py`: the start-stack help text listing backend components drops `ingest`
- [x] `.github/workflows/bump-stack-versions.yml`: the `api` input description drops "Ingest"
- [x] `test/unittests/commands/start_stack/test_start_stack.py`: the override fixture and assertions no longer
  carry an `ingest` service
- [x] Run `dsp-tools start-stack --no-prune` from the branch (`uv run dsp-tools start-stack --no-prune`); then
  `curl -f http://0.0.0.0:3333/ingest/health` returns 200 and `docker ps` lists no ingest container
- [x] Against that stack, run the command sequence the e2e `ingest_xmlupload` tests use (`create` the project,
  then `upload-files`, `ingest-files`, `ingest-xmlupload`) on `testdata/dsp-ingest-data/e2e-sample-project`; each
  succeeds and the ingested assets appear under `~/.dsp-tools/start-stack/sipi/images`
- [x] Run `uv run dsp-tools stop-stack`; the stack and its volumes are removed without errors
- [x] Run `just unittests`; it passes
- [x] Run `just lint`; it passes
- [x] Run `update-docs` on the phase's changes; no missing user-facing doc update reported
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: e2e testcontainers run without the ingest container

#### dsp-tools

- [ ] `test/e2e/setup_testcontainers/containers.py`: remove `_get_ingest`, `INGEST_INTERNAL_PORT` and the `ingest`
  fields of `Containers`, `ImageVersions` and `ContainerNames`
- [ ] `test/e2e/setup_testcontainers/containers.py`: `_get_api` drops `KNORA_WEBAPI_DSP_INGEST_BASE_URL`, sets the
  ingest env vars from *start-stack api service*, and mounts `artifact_dirs.sipi_images` at `/opt/images` and
  `artifact_dirs.tmp_ingest` at `/opt/temp` read-write
- [ ] `test/e2e/setup_testcontainers/setup.py`: drop the ingest image version, container name and stop call
- [ ] `test/e2e/setup_testcontainers/ports.py`: drop the `ingest` port (three ports reserved and released)
- [ ] `test/e2e/setup_testcontainers/artifacts.py`: drop the `ingest_db` artifact dir
- [ ] `test/e2e/commands/conftest.py`: `creds` ingest URL becomes `http://0.0.0.0:{container_ports.api}/ingest`
- [ ] `test/e2e/commands/ingest_xmlupload/test_ingest_xmlupload.py`: ingest URL becomes
  `http://0.0.0.0:{ports.api}/ingest`
- [ ] `test/e2e/commands/ingest_xmlupload/test_upload_files.py`: ingest URL becomes
  `http://0.0.0.0:{ports.api}/ingest`
- [ ] `justfile`: `clean` stops removing `./testdata/e2e/ingest-db/`
- [ ] Run `just e2e-test-ingest-xmlupload`; it passes
- [ ] Run `just e2e-tests`; it passes
- [ ] Run `just lint`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Cut a dsp-api release (git tag + `knora-api`/`knora-sipi`/`apache-jena-fuseki` images on Docker Hub) that serves `/ingest/` and ships the Sipi CLI, and a dsp-app release that accepts a path in `ingestUrl`; pass both tags to the executor | dsp-api / dsp-app maintainers | before start | Cutting a release is a maintainer decision |
| H2 | Once prod serves `/ingest/`: set `versions.env` in this PR to the release prod runs (if it differs from H1), close the red automated bump PR for that release, wait for CI to pass, and merge | dsp-tools owner | after ship | PRD constraint: merged only once prod serves `/ingest/`; merging and the timing are the owner's decision |
| H3 | Release dsp-tools with this change and mention in the release notes that `start-stack` no longer runs an ingest container and that ingest is reached at `<server>/ingest` | dsp-tools owner | after ship | Releasing is the owner's decision |

## Acceptance Criteria

- [ ] For every supported server input, dsp-tools derives the ingest URL `<canonical server>/ingest`;
  `ingest.<x>.dasch.swiss` as input still resolves to `https://api.<x>.dasch.swiss` and
  `https://api.<x>.dasch.swiss/ingest`
- [ ] Every ingest request (single-asset upload, bulk-ingest upload, bulk-ingest start, mapping download, health)
  goes to `<server>/ingest/<unchanged path>`
- [ ] `ServerCredentials.dsp_ingest_url` remains an overridable field; no CLI flag is added
- [ ] More than `maximum_prints` upload failures against an `/ingest` URL write the failure CSV to a single file in
  the working directory
- [ ] `start-stack` starts no ingest container, uses no `DB_JDBC_URL`/`ingest-db` volume, and an
  `ingest-xmlupload` against it succeeds
- [ ] The e2e suite runs with three containers (fuseki, sipi, api) and passes
- [ ] No `3340`, `dsp-ingest` image or `ingest.<domain>` derivation remains in `src/` or `test/` (`git grep -n
  -E "3340|daschswiss/dsp-ingest|DB_JDBC_URL" -- src test` returns nothing)
- [ ] `just unittests`, `just integration-tests`, `just e2e-tests` and `just lint` pass

## Dependencies & Risks

- **dsp-api must ship first (H1).** Phases 2 and 3 need a tagged dsp-api release whose `knora-api` image serves
  `/ingest/`, includes the Sipi CLI, ffmpeg and ffprobe, and starts without any ingest database setting. Phase 1
  can be built and unit-tested before then.
- **Env var contract (dsp-api plan).** Phases 2 and 3 use the names the dsp-api plan fixes: `INGEST_STORAGE_*`,
  `INGEST_ALLOW_ERASE_PROJECTS`, kept `KNORA_WEBAPI_DSP_INGEST_AUDIENCE`, no `KNORA_WEBAPI_DSP_INGEST_*` base URL or
  asset dir. If the API container still carried the old unprefixed names, dsp-api would silently ignore them and
  ingest would fall back to its defaults (`/opt/images`, `/opt/temp`), so the temp mount path `/opt/temp` must be
  passed through `INGEST_STORAGE_TEMP_DIR`. `INGEST_SIPI_USE_LOCAL_DEV` stays unset, so the image's bundled Sipi
  CLI, ffmpeg and ffprobe are used.
- **Bump-PR timing.** Between the dsp-api release without the ingest image and this PR's merge, the automated
  `bump-stack-versions` PR for that release fails e2e and can't auto-merge. Released dsp-tools versions keep
  pulling the older pinned images from their own `versions.env`, so they are unaffected. Only main's bump is held
  back, which H2 resolves.
- **Older dsp-tools releases.** They use `https://ingest.<domain>` and `http://0.0.0.0:3340`. Remote use keeps
  working through the permanent proxy. Local `start-stack` of an old release keeps working because it pins its own
  older images. No fallback is added in this release.
- **dsp-app `ingestUrl` with a path.** If the pinned `APP` release builds ingest URLs in a way that drops the
  `/ingest` path, uploads from the local dsp-app break. H1 picks a dsp-app release from the dsp-app part of the
  PRD.
- **Custom host.** With `--custom-host`, ingest is reached through the API's external host and port; there's no
  separate ingest issuer to override any more. Phase 2's manual check covers the default host only. The
  custom-host template change is reviewed but not run.

## Success Metrics

- The PR is open with green CI against the H1 dsp-api release, and is merged after prod serves `/ingest/` (PRD
  success criterion for dsp-tools).
- Baseline before the change: `start-stack` runs 5 containers (api, sipi, ingest, app, db), and the e2e
  testcontainers run 4. After the change: 4 and 3.

## References

- PRD: `01-move-ingest-into-webapi-PRD.md` (repository impact, dsp-tools bullet; US-1, US-2, REQ-4.5)
- URL derivation: `src/dsp_tools/cli/utils.py:21-22,109-173`, `src/dsp_tools/cli/entry_point.py:46,65-69,238-272`
- Defaults: `src/dsp_tools/cli/args.py:16`
- Ingest clients: `src/dsp_tools/clients/ingest.py:98`, `src/dsp_tools/clients/bulk_ingest_client.py:110,114,160`
- Filename from URL: `src/dsp_tools/commands/ingest_xmlupload/upload_files/upload_failures.py:43`
- start-stack resources: `src/dsp_tools/resources/start-stack/` (`docker-compose.yml`, `docker-compose.override.yml`,
  `docker-compose.override-host.j2`, `dsp-app-config.json`, `dsp-app-config.override-host.j2`, `versions.env`)
- e2e setup: `test/e2e/setup_testcontainers/{containers,setup,ports,artifacts}.py`, `test/e2e/commands/conftest.py`
- Current ingest settings: dsp-api `modules/ingest/src/main/resources/application.conf`, dsp-api
  `docker-compose.yml` (`ingest` and `api` services)
- Verification commands: dsp-tools `eng.yaml` (`just unittests`, `just lint`, `just integration-tests`,
  `just e2e-tests`, `just e2e-test-ingest-xmlupload`; verify skills `change-blast-radius`, `update-docs`)
