---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 52646863a175264f74afd35d9813def50b052391
branch: impl/ingest-hardening-sipi-errors
started: 2026-11-27
unit: sipi-errors
unit_state: done
curated_from: e2dcf82420c6531882042d7d1fcc6d0768dbba74
started_from: fc3269a931b690634fcb825251b41775403e3e9e
problem: >
  Sipi failures reach the client as a bare 500 with paths and process output (DEV-8105 Part B).
  Phase 4 passes the correlation ID to sipi, reads sipi's JSON error report, and maps it to 422 or 500.
symptoms:
  - "HTTP 500 with 'Command failed: /sbin/sipi convert ...' in the body"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit sipi-errors)

## Repos

| repo    | base_commit | branch                            | merge_strategy | status   | pr                                                        |
| ------- | ----------- | --------------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 5264686     | impl/ingest-hardening-sipi-errors | rebase         | complete | [#4469](https://github.com/dasch-swiss/dsp-api/pull/4469) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 4     | reviewed | dsp-api@5264686 | 1                 |

## Chunk queue

- 4.1 ingest: correlation ID to sipi, `SipiReport` fields and phases, command targets, `Query --json`, exit 137/139
  and no-report classification, path-free 422 message, `SipiCliError` cases in `IngestFailure`, tests per mapping row

## Chunks

| id  | repo | status | commit(s) | summary | blocker |
| --- | ---- | ------ | --------- | ------- | ------- |

## Deferrals

Phase 4 review (scala-zio and consistency reviewers over b0c938d), one fix round:

- Fixed: a 422 from sipi's fallback document (no `input_file` or `output_file`) sent sipi's raw message, which can
  hold a path. Such a report now gives the fixed message `<filename> cannot be read: <error_code>`; test in
  `IngestFailureSpec`.
- Deferred to `final` (Warning): bulk ingest logs a sipi content error (`decode_failed` and the other 422 codes) as
  an error, not as a warning, while `docs/03-endpoints/ingest.md` says a rejected file is a warning. The plan says
  Phase 4 does not edit the bulk-ingest log, so the doc sentence or the log branch is for `final` to settle.
- Refuted: `image_too_large` in the docs table has no producer (the pixel-limit unit adds it; the plan lists all six
  codes in Phase 1); paths in sipi's message other than `input_file` and `output_file` (the plan rules out a path
  search); `SipiCliError.getMessage` and `executeOrFail` hold paths (logged only, never in a body); sipi's
  `correlation_id` echoed unchecked (sipi gets it from `SIPI_CORRELATION_ID`).

Gates: `just test-unit` targets pass (7 of 7; ingest re-run after the fix); scalafmt `format-test` for ingest, lint
and license tests pass. `test-it` and `test-e2e` not run (no Docker daemon), left to `final`.

## Deviations

## Side findings

- Stacked on `impl/ingest-hardening-pixel-limit` (fc3269a). The rebase conflicted only in `IngestServiceSpec`
  (both units added a suite and an import); resolved by keeping both. The sipi-report suites now provide
  `SipiConfig`, which `IngestServiceLive` needs since the pixel limit. Ingest tests pass after the rebase;
  the review was not re-run by subagents, since the rebase changed only test-layer wiring. `webapi:test` failed 2 of
  4 runs in `V3ProjectsRestServiceSpec` (`NoSuchFileException` under `migration/exports`), the flaky test the sipi-pin
  unit saw on `origin/main` too; it passed the other 2 runs.
- `gh stack` and `crane` are not installed in the cloud container; the local stack was not recorded on entry. No
  `bdd.yaml`, so no hook needs it; the stack step runs on Ivan's Mac.

## Owner decisions

- 2026-11-28 (final), card cmsg_01K9EcSWLHypmAgDdAKJ5xCERVfYtpdn4SFUs5dWwVqsWZ: a sipi 422 message that still holds an absolute path after the replacement gives the fixed message. Ivan chose the fallback, a deviation from the plan's 'no regex search for paths' that keeps REQ-3. Rejected: keep the plan; never forward sipi's text.
- 2026-11-28 (final), card cmsg_01K9EcSWLHypmAgDdAKJ5xCEDdp43SVtJKup3icp3Y8p6d: bulk ingest logs a sipi content error as a warning, as the docs say. Ivan chose warning. Rejected: keep error and change the docs. Follow-up from the same answer: the uploader gets no failure report for a bulk ingest (DEV-8130, a separate failure CSV).

## Closeout

- root_cause: Sipi failures surfaced as `executeOrFail`'s IOException with paths and process output, answered as a bare 500 (DEV-8105 Part B).
- investigation: Sipi v10.2.0 writes a JSON report with `phase` and `error_code` under `--json`; `query --json` with a missing file exits 105 without a report.
- solution: Ingest passes the correlation ID in `SIPI_CORRELATION_ID`, reads the report, and maps an upload read failure with `decode_failed`, `unsupported_format`, `malformed_input` or `metadata_parse_failed` to 422 and every other sipi failure to 500 with its code (`sipi_killed`, `sipi_crashed`, `sipi_no_report` for exit 137, 139 and no report). Final review (owner decisions): the 422 message falls back to a fixed text when a path survives the replacement; bulk ingest logs a sipi content error as a warning, as the docs say.
- prevention: `SipiClientSpec`, `SipiReportSpec`, `IngestFailureSpec`, `CommandExecutorLiveSpec`, `BulkIngestServiceSpec`.
