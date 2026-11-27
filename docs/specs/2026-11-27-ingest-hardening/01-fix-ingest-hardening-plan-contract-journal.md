---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-contract
started: 2026-11-27
unit: contract
unit_state: done
curated_from: e6c6f5b2358134315ac0a235aa5e52936aa05455
started_from: a9277826b56d90f0c15883134eee266887b75a77
problem: >
  Ingest upload failures reach the client as a bare HTTP 500 whose body holds the exception message
  (server paths, sipi command lines), and defects in the upload handler are answered as plain-text 500s
  without a log entry. Phase 1 adds one ingest error contract (422 and structured 500 with correlation_id,
  error_code, message), one mapping function and logged defects (DEV-8105, DEV-8123).
symptoms:
  - "500 body: Command failed: '/sbin/sipi convert /tmp/...' ProcessOutput(...)"
  - "plain-text 500 without a log entry when Files.size fails in the upload handler"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit contract)

## Repos

| repo    | base_commit | branch                         | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ------------------------------ | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-contract | rebase         | complete | [#4462](https://github.com/dasch-swiss/dsp-api/pull/4462) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 1     | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 1.1 ingest: `ApiProblem` 422 and structured 500, `CorrelationId`, `IngestRejected`, `IngestFailure`,
  `HandlerFunctions.guarded` on every handler, callers changed, `.orDie` removed from the upload, bulk-ingest log; tests
- 1.2 docs: the error body and the six `error_code` values in `docs/03-endpoints/ingest.md`

## Chunks

| id  | repo    | status   | commit(s)        | summary                                                                                                                                                                                                  | blocker |
| --- | ------- | -------- | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| 1.1 | dsp-api | complete | e96c7c0          | error contract, correlation ID, guarded handlers, bulk-ingest log; 4 new tests fail with the change reverted                                                                                             | none    |
| 1.2 | dsp-api | complete | 857e3f2          | ingest.md documents the error body and codes                                                                                                                                                             | none    |
| 1.3 | dsp-api | complete | 9e3e4a6, f7748b6 | review round 1: guarded logs a defect that comes with a typed failure; bulk ingest catches defects, logs a rejected file as a warning with its path in the import folder; one builder for the logged 500 | none    |

## Deferrals

- Phase 1: the BagIt import 400 (`BagIt validation failed: <msg>`) echoes text from the uploaded zip and the
  extraction limits. Pre-existing, not in the plan; a follow-up issue is for `final` to file.
- Phase 1: `postExportEndpoint` and `getProjectsAssetsOriginal` stream files after `serverLogic` returns, so a
  failure while streaming is outside `guarded` and still gives tapir's plain 500. Pre-existing, not in the plan.

## Deviations

## Side findings

- Phase 1: `just` is not on PATH outside the Nix dev shell; its recipes were run as the Bazel commands they wrap,
  and markdownlint as `markdownlint-cli` 0.44.0 with the repository config.
- Phase 1: `gh stack` is not installable in the cloud container (no GitHub access to github/gh-stack). The repository
  has no `bdd.yaml`, so the local stack metadata is not needed while implementing; the stack-on step runs where
  `gh stack` is installed.
- Phase 1: Docker is not available in the cloud container, so `IngestEndpointsE2ESpec` (`//modules/test-e2e:test`) is
  not run here; it reads no `errorMessage` field, so no change to it was needed. `final` runs it.
- Phase 1: `BulkIngestService.uploadSingleFile` keeps two `.orDie` calls; `guarded` turns their defects into the
  logged structured 500, and the plan names only `postProjectAssetEndpoint`.
- Phase 1 review (scala-zio, consistency, security, observability, simplicity; sonnet). Fixed: a defect that comes
  with a typed failure was dropped unlogged; bulk ingest missed defects; a rejected bulk file was logged at error with
  a stack trace; the bulk log printed the absolute path while the docs said file name; the logged 500 was built in
  two places. Refuted: shared `ZTestLogger` across tests (each test has its own logger: the `logs.isEmpty` test
  passes while sibling tests log errors); a client-chosen `trace_id` (OTel accepts only a valid 32-hex trace ID).
  Pre-existing, left: the 401 body carries the JWT library message; `Principal` holds the raw JWT, so it must never
  be logged.

## Owner decisions

- none

## Closeout

- root_cause: Ingest handlers let every failure reach tapir's default handler, which answered with the exception message (server paths, sipi command lines) or, for a defect, a plain-text 500 without a log entry.
- investigation: The plain-text 500 came from a defect in `postProjectAssetEndpoint` (`Files.size` on a vanished temp file), which no `catchAll` saw. Typed failures and defects needed one place each.
- solution: `IngestFailure.toApiProblem` is the one mapping to the body (`correlation_id`, `error_code`, `message`); `HandlerFunctions.guarded` wraps the server logic of every route except `/info`, `/health` and `/metrics` and logs a defect once with the correlation ID; `CorrelationId` scopes the ID per request; bulk ingest logs each failed file once. Final review: the CI markdownlint findings (MD060, MD034) in this journal fixed.
- prevention: `HandlerFunctionsSpec`, `IngestFailureSpec`, `ApiProblemSpec` assert the body shape and the absence of `/tmp/`, `/data/` and `Command failed`. A new route must run inside `guarded` (ARCH-MAP ingest boundary rule).
