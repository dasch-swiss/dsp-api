---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dasch-swiss/dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-early-close
started: 2026-11-27
unit: early-close
unit_state: done
curated_from: 2d286ec552035137eda07d257f05a490fa1ceaa8
started_from: 680b31d81ae9d64c500374d00892772b61d2376c
problem: >
  During the rdu-13 bulk import the client waited for its read timeout after the server had already
  answered an upload with an error (DEV-8106). Phase 6 tests at the webapi level whether an error answered
  while a streamed body is still unread reaches the client at once with `Connection: close`.
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit early-close)

## Repos

| repo    | base_commit | branch                            | merge_strategy | status   | pr                                                        |
| ------- | ----------- | --------------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-early-close | rebase         | complete | [#4463](https://github.com/dasch-swiss/dsp-api/pull/4463) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 6     | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 6.1: `EarlyErrorStreamingSpec`: a real server with `DspApiServer.serverOptions` and the server's streaming
  config; a `ClosesOnUnreadBody` stub that reads one chunk, then fails (typed error, defect); a 20 MiB upload
  with `Content-Length` gets the 500 with `Connection: close` within 10 s. Fix the close rule if a case fails.

## Chunks

| id  | repo    | status   | commit(s) | summary                                                                                                                                                                     | blocker |
| --- | ------- | -------- | --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| 6.1 | dsp-api | complete | 0848d9f   | Reproduced: a defect in a streamed route's logic answered 500 without `Connection: close`, connection left open. Exception handler adds the close; spec + interceptor tests | none    |

## Deferrals

- Phase 6: `EarlyErrorStreamingSpec` closes with about 20 MiB unread on the server side, so the kernel may answer
  with RST. A client stack that drops queued data on RST could fail the head read. Not seen in 5 runs on Linux;
  the reader is blocked in `read` before the answer arrives. Revisit if the spec flakes in CI.
- Phase 6: dune review suggestions, not done (Suggestion level, scope): hoist one shared close decision for the
  interceptor and the exception handler, and a per-path, per-marker parity table in
  `ClosesOnUnreadBodyInterceptorSpec` (passthrough marker on the exception path is not covered).

## Deviations

- none

## Side findings

- Phase 6: DEV-8106 reproduced at the webapi level. A typed failure was already answered with
  `Connection: close`; a defect (or any Throwable) in the server logic was not, and the connection stayed open.
- Phase 6: the Nix dev shell does not load in the cloud container (GitHub tarball fetch refused), so `just`
  recipes were run as their bazel commands, and markdownlint through `npx markdownlint-cli@0.41.0`.
- Entry: `gh stack` is not installed in the cloud container. Implementation and curation run here; the
  `stack on` step needs `gh stack` (the Mac via Remote Control, as for the plan).

- Phase 6: stacked on `impl/ingest-hardening-contract` (680b31d) with a clean rebase; that layer changes no
  webapi file, so the phase review stands. Unit tests pass on the rebased head. The PR was opened with the
  GitHub API, as `gh stack` is not installed here; `final` adopts the branches with `gh stack init`.

## Owner decisions

- none

## Closeout

- root_cause: A defect (any Throwable) in an upload's server logic went through tapir's default exception handler, which answered without `Connection: close`, so a client still sending a streamed body waited for its read timeout (DEV-8106).
- investigation: Reproduced at the webapi level: a typed failure already closed the connection, a defect did not.
- solution: `DspApiServer` installs `closesOnUnreadBodyExceptionHandler`, which applies the same close rule as `closeConnectionIfBodyUnread` (a declared `Content-Length` above the aggregation threshold). Final review: a doc comment reworded to state the rule, not its history.
- prevention: `EarlyErrorStreamingSpec` sends a 20 MiB body and asserts the answer with `Connection: close` within 10 s; `ClosesOnUnreadBodyInterceptorSpec` covers the exception path.
