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

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-tools | src/dsp_tools/cli/{utils,entry_point,args,exceptions}.py, src/dsp_tools/cli/CLAUDE.md, test/unittests/cli/{test_helper_functions,test_utils,test_cli_with_network}.py, test/unittests/clients/test_bulk_ingest_client.py, test/integration/commands/ingest_xmlupload/test_ingest_xmlupload.py | — | LOCALHOST_INGEST; get_canonical_...; entry_point; args default; exceptions msg; test_helper_functions; test_utils; test_cli_with_network; test_bulk_ingest_client; integration test; cli/CLAUDE.md | ingest URL derived as f"{server}/ingest" everywhere; unit tests green | src/dsp_tools/cli/utils.py § get_canonical_server_and_dsp_ingest_url; cli/CLAUDE.md § Server URL Canonicalization | — |
| 1.2 | dsp-tools | src/dsp_tools/commands/ingest_xmlupload/upload_files/{upload_failures.py,CLAUDE.md}, test/unittests/commands/ingest_xmlupload/test_upload_failures.py | — | upload_failures.py filename; upload_files/CLAUDE.md | failure CSV for an /ingest URL is a single file in cwd | upload_failures.py § execute_error_protocol | — |
| 1.3 | dsp-tools | docs/developers/start-stack.md, ARCH-MAP.md | 1.1 | start-stack.md subdomain section; ARCH-MAP.md durable-state line | docs describe `<server>`/ingest | start-stack.md ~L90-120; ARCH-MAP.md § ingest-xmlupload | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-tools | complete | 4b8e9300 | ingest URL derived as f"{server}/ingest" (local <http://0.0.0.0:3333/ingest>); default_dsp_ingest_url removed; ServerCredentials default and local IngestNotReachableError message updated; CLI/bulk-client/integration tests and cli/CLAUDE.md follow | none |
| 1.2 | dsp-tools | complete | 42366d99 | failure CSV file name replaces '/' of the ingest URL (api.dasch.swiss/ingest → api.dasch.swiss_ingest); new unit test runs in tmp cwd; upload_files/CLAUDE.md describes the /ingest base URL | none |
| 1.3 | dsp-tools | complete | b7cbac51 | docs/developers/start-stack.md subdomain section and ARCH-MAP ingest-xmlupload durable-state line describe ingest under /ingest on the DSP-API host | none |
| 1.4 | dsp-tools | complete | 370f00d8 | review fix round 1: startup message, URL-derivation docstrings and upload_files/CLAUDE.md say ingest URL / ingest instead of a separate ingest server; docstring trimmed | none |
| 1.5 | dsp-tools | complete | 861f6b90 | review fix round 2: localhost canonicalisation sentence restored in get_canonical_server_and_dsp_ingest_url docstring; upload_files/CLAUDE.md says "the ingest service" (CONTEXT.md reserves Ingest for the processing step) | none |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 1 review suggestions not applied: `ServerCredentials.dsp_ingest_url` default in `cli/args.py` duplicates `LOCALHOST_INGEST` as a literal (a test asserting equality would pin it); remaining "ingest server" wording in `clients/ingest.py`, `clients/bulk_ingest_client.py`, `upload_files.py`, `upload_failures.py` docstrings; `UploadFailures` file name still keeps `:` from a URL with a port (pre-existing, invalid on Windows).
- `_check_ingest_health` classifies localhost by exact match with `LOCALHOST_INGEST`; an overridden `dsp_ingest_url` with a trailing slash would get the remote error message (not reachable from the CLI, which always derives the URL).
- The local `IngestNotReachableError` message (DSP-API must serve `/ingest/`) is transitional; revisit once start-stack runs without the ingest container (Phase 2).
- Local `just lint` needs GNU parallel and yamlfmt; the shell's `parallel` is moreutils'. Ran it as `nix shell nixpkgs#yamlfmt nixpkgs#parallel -c just lint`.
