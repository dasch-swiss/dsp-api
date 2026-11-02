---
plan: docs/specs/2026-10-20-move-ingest-into-webapi/04-feat-ingest-urls-dsp-tools-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-tools
base_commit: c0e6aaeb
branch: feat/ingest-under-api-path
started: 2026-10-26
problem: >
  dsp-ingest moves into dsp-api under /ingest/ and the standalone dsp-ingest image is no longer
  published. dsp-tools derives the ingest URL as https://ingest.<domain> (remote) and
  http://0.0.0.0:3340 (local) and runs a separate dsp-ingest container in start-stack and the e2e
  testcontainers; it must reach ingest at <server>/ingest on the API host instead.
symptoms:
status: in-progress
---

# Execution Journal: 04-feat-ingest-urls-dsp-tools-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-tools | c0e6aaeb | feat/ingest-under-api-path | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-tools@c0e6aaeb | 2 |
| 2 | reviewed | dsp-tools@861f6b90 | 1 |
| 3 | reviewed | dsp-tools@628fb302 | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-tools | src/dsp_tools/cli/{utils,entry_point,args,exceptions}.py, src/dsp_tools/cli/CLAUDE.md, test/unittests/cli/{test_helper_functions,test_utils,test_cli_with_network}.py, test/unittests/clients/test_bulk_ingest_client.py, test/integration/commands/ingest_xmlupload/test_ingest_xmlupload.py | — | LOCALHOST_INGEST; get_canonical_...; entry_point; args default; exceptions msg; test_helper_functions; test_utils; test_cli_with_network; test_bulk_ingest_client; integration test; cli/CLAUDE.md | ingest URL derived as f"{server}/ingest" everywhere; unit tests green | src/dsp_tools/cli/utils.py § get_canonical_server_and_dsp_ingest_url; cli/CLAUDE.md § Server URL Canonicalization | — |
| 1.2 | dsp-tools | src/dsp_tools/commands/ingest_xmlupload/upload_files/{upload_failures.py,CLAUDE.md}, test/unittests/commands/ingest_xmlupload/test_upload_failures.py | — | upload_failures.py filename; upload_files/CLAUDE.md | failure CSV for an /ingest URL is a single file in cwd | upload_failures.py § execute_error_protocol | — |
| 1.3 | dsp-tools | docs/developers/start-stack.md, ARCH-MAP.md | 1.1 | start-stack.md subdomain section; ARCH-MAP.md durable-state line | docs describe `<server>`/ingest | start-stack.md ~L90-120; ARCH-MAP.md § ingest-xmlupload | — |
| 2.1 | dsp-tools | src/dsp_tools/resources/start-stack/{docker-compose.yml,docker-compose.override.yml,docker-compose.override-host.j2}, test/unittests/commands/start_stack/test_start_stack.py | — | compose: remove ingest service + ingest-db volume; remove KNORA_WEBAPI_DSP_INGEST_BASE_URL; add INGEST_* env; mount images + temp on api; override.yml ingest; override-host.j2 ingest; test_start_stack.py | start-stack compose has no ingest service; api carries ingest storage env and mounts; unit tests green | docker-compose.yml § api, ingest | — |
| 2.2 | dsp-tools | src/dsp_tools/resources/start-stack/{dsp-app-config.json,dsp-app-config.override-host.j2} | — | dsp-app-config.json ingestUrl; dsp-app-config.override-host.j2 ingestUrl | dsp-app ingestUrl is `<host>:3333/ingest` | dsp-app-config.json § ingestUrl | — |
| 2.3 | dsp-tools | src/dsp_tools/cli/create_parsers.py, .github/workflows/bump-stack-versions.yml | — | create_parsers.py help text; bump-stack-versions.yml api input | no ingest component named in help or workflow input | create_parsers.py § `_add_start_stack` | — |
| 2.4 | dsp-tools | (no files; manual start-stack checks) | 2.1, 2.2 | start-stack --no-prune + health; e2e command sequence; stop-stack; just unittests; just lint | stack runs 4 containers; /ingest/health 200; ingest-xmlupload sequence succeeds | plan § Phase 2 run items | — |
| 3.1 | dsp-tools | test/e2e/setup_testcontainers/{containers,setup,ports,artifacts}.py, test/e2e/commands/conftest.py, test/e2e/commands/ingest_xmlupload/{test_ingest_xmlupload,test_upload_files}.py, justfile, .gitignore | — | containers.py remove ingest; containers.py `_get_api` ingest env + mounts; setup.py; ports.py; artifacts.py; conftest.py creds; test_ingest_xmlupload.py URL; test_upload_files.py URL; justfile clean | testcontainers start fuseki, sipi, api only; api serves /ingest with /opt/images and /opt/temp mounted RW | test/e2e/setup_testcontainers/containers.py § `_get_api` | — |
| 3.2 | dsp-tools | (no files; e2e and lint runs) | 3.1 | just e2e-test-ingest-xmlupload; just e2e-tests; just lint | e2e suite green on local dsp-api images tagged v40.4.0; lint green | plan § Phase 3 run items | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-tools | complete | 4b8e9300 | ingest URL derived as f"{server}/ingest" (local <http://0.0.0.0:3333/ingest>); default_dsp_ingest_url removed; ServerCredentials default and local IngestNotReachableError message updated; CLI/bulk-client/integration tests and cli/CLAUDE.md follow | none |
| 1.2 | dsp-tools | complete | 42366d99 | failure CSV file name replaces '/' of the ingest URL (api.dasch.swiss/ingest → api.dasch.swiss_ingest); new unit test runs in tmp cwd; upload_files/CLAUDE.md describes the /ingest base URL | none |
| 1.3 | dsp-tools | complete | b7cbac51 | docs/developers/start-stack.md subdomain section and ARCH-MAP ingest-xmlupload durable-state line describe ingest under /ingest on the DSP-API host | none |
| 1.4 | dsp-tools | complete | 370f00d8 | review fix round 1: startup message, URL-derivation docstrings and upload_files/CLAUDE.md say ingest URL / ingest instead of a separate ingest server; docstring trimmed | none |
| 1.5 | dsp-tools | complete | 861f6b90 | review fix round 2: localhost canonicalisation sentence restored in get_canonical_server_and_dsp_ingest_url docstring; upload_files/CLAUDE.md says "the ingest service" (CONTEXT.md reserves Ingest for the processing step) | none |
| 2.1 | dsp-tools | complete | db076704 | start-stack compose drops the ingest service, ingest-db volume, port 3340 and KNORA_WEBAPI_DSP_INGEST_BASE_URL; api mounts sipi/images:/opt/images and sipi/tmp-dsp-ingest:/opt/temp and sets INGEST_STORAGE_ASSET_DIR, INGEST_STORAGE_TEMP_DIR, INGEST_ALLOW_ERASE_PROJECTS; override files and start-stack unit test follow | none |
| 2.2 | dsp-tools | complete | 6aba178f | start-stack dsp-app config (default and custom-host) sets ingestUrl to `<host>:3333/ingest` | none |
| 2.3 | dsp-tools | complete | a317b593 | start-stack --latest help text and bump-stack-versions api input description no longer name ingest | none |
| 2.4 | dsp-tools | complete | — (checks only) | start-stack --no-prune with local dsp-api images (feat/serve-ingest-from-dsp-api @ 4ea296d1) tagged v40.4.0: 4 containers (api, sipi, app, db), /ingest/health 200, api mounts /opt/images and /opt/temp RW; create, upload-files, ingest-files, ingest-xmlupload on e2e-sample-project succeed, assets under sipi/images/0001; extra 8.9 MB JPEG uploaded and ingested (Content-Length path); stop-stack clean; just unittests (3081 passed) and just lint green | none |
| 2.5 | dsp-tools | complete | 628fb302 | review fix round 1: comments on the api service say the 3340 ingest audience is a JWT label, not an address, and that INGEST_ALLOW_ERASE_PROJECTS is ingest's own erase gate | none |
| 3.1 | dsp-tools | complete | fb8df145 | e2e testcontainers drop the ingest container, its port (three reserved), name, image version and ingest-db artifact dir; api gets INGEST_STORAGE_ASSET_DIR=/opt/images, INGEST_STORAGE_TEMP_DIR=/opt/temp, INGEST_ALLOW_ERASE_PROJECTS=true and RW mounts of sipi_images and tmp_ingest, no KNORA_WEBAPI_DSP_INGEST_BASE_URL; creds and ingest_xmlupload tests use `<api>/ingest`; justfile clean and .gitignore drop ingest-db | none |
| 3.2 | dsp-tools | complete | — (checks only) | local dsp-api images (feat/serve-ingest-from-dsp-api @ 4ea296d1) tagged v40.4.0: just e2e-test-ingest-xmlupload 2 passed; just e2e-tests 106 passed (with CI's DSP_USER_PASSWORD=pw and TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock for Docker Desktop's Ryuk mount); just lint green | none |
| 3.3 | dsp-tools | complete | 88eabdc3 | review fix round 1: comment on the e2e api container names start-stack's docker-compose.yml api service as the counterpart of its ingest settings and mounts (DUNE-001) | none |

## Deferrals

- none

## Deviations

- Phase 2 (db076704, committed): the plan's *start-stack api service* mounts the ingest temp dir at `/opt/tmp` with
  `INGEST_STORAGE_TEMP_DIR=/opt/tmp`; committed is `./sipi/tmp-dsp-ingest:/opt/temp` with
  `INGEST_STORAGE_TEMP_DIR=/opt/temp`. Reason: the brief said to follow dsp-api, whose `ingest.conf` default and
  `docker-compose.yml` api service use `/opt/temp`. The Phase 2 checkbox wording was updated; the plan's
  *start-stack api service* block and *Dependencies & Risks* bullet, and Phase 3's `_get_api` checkbox, still say
  `/opt/tmp`.

## Side findings

- Phase 1 review suggestions not applied: `ServerCredentials.dsp_ingest_url` default in `cli/args.py` duplicates `LOCALHOST_INGEST` as a literal (a test asserting equality would pin it); remaining "ingest server" wording in `clients/ingest.py`, `clients/bulk_ingest_client.py`, `upload_files.py`, `upload_failures.py` docstrings; `UploadFailures` file name still keeps `:` from a URL with a port (pre-existing, invalid on Windows).
- `_check_ingest_health` classifies localhost by exact match with `LOCALHOST_INGEST`; an overridden `dsp_ingest_url` with a trailing slash would get the remote error message (not reachable from the CLI, which always derives the URL).
- The local `IngestNotReachableError` message (DSP-API must serve `/ingest/`) is transitional; revisit once start-stack runs without the ingest container (Phase 2).
- Local `just lint` needs GNU parallel and yamlfmt; the shell's `parallel` is moreutils'. Ran it as `nix shell nixpkgs#yamlfmt nixpkgs#parallel -c just lint`.
- Phase 2: the local `IngestNotReachableError` message (local DSP-API must serve `/ingest/`, restart with
  `dsp-tools start-stack`) still fits once start-stack runs without an ingest container: it is the message a user of
  a stack on a pre-move DSP-API version sees. Left unchanged.
- Phase 2 review suggestions not applied: `start-stack --max_file_size` help and `StackConfiguration` docstring say
  it limits ingest, but it only patches Sipi's `max_post_size` (pre-existing; DSP-API's `/ingest` uploads do not
  read it); the `./sipi/images` and `./sipi/tmp-dsp-ingest` bind-mount dirs are created by Docker on first `up`
  (root-owned on Linux hosts; same as the old ingest mounts); an `ingest-db` named volume from an older stack stays
  orphaned until pruned; `.gitignore` still lists `/testdata/e2e/ingest-db` and `/testdata/e2e/tmp-dsp-ingest`
  (Phase 3's e2e change should take the `ingest-db` entry with it).
- Phase 2 manual run: `ingest-xmlupload` of an XML that references only some of a project's ingested files fails
  by design (mapping CSV covers every file ingested for the shortcode); the plan's sequence on
  `e2e-sample-project` itself succeeded.
- Phase 3: running the e2e suite locally on Docker Desktop (macOS) needs `DSP_USER_PASSWORD=pw` (CI sets it in
  `tests-e2e.yml`; without it `create` rejects `testerProjectAdmin`) and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (else Ryuk fails to mount
  `~/.docker/run/docker.sock`).
- Phase 3 review suggestions not applied: a parity test (or deriving the e2e api env from the packaged compose
  file) would enforce the start-stack/e2e ingest settings link that the comment only states (DUNE-001); the e2e
  api container does not set `KNORA_WEBAPI_DSP_INGEST_AUDIENCE` and relies on DSP-API's default, which DSP-API
  both mints and checks; the `<api>/ingest` URL is built inline in three e2e fixtures; the `tmp_ingest` /
  `tmp-dsp-ingest` names predate the move (kept for parity with start-stack's `./sipi/tmp-dsp-ingest`); stale
  local `testdata/e2e/ingest-db/` dirs are no longer ignored or cleaned; ARCH-MAP's tests entry does not name the
  e2e container set.
