---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dasch-swiss/dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-tempdir
started: 2026-11-27
unit: tempdir
unit_state: done
curated_from: 356320d667fe408116ed3c3fe3263ad699fc61f6
started_from: 0719ec28555f5f7375aa76ce1348f918969665d1
problem: >
  Parallel uploads to the same project in the same second share one temporary folder
  (`<temp>/<yyyyMMdd_HHmmss>/<shortcode>-ingest`). The first upload's scope release deletes the folder
  while the second upload still reads its file, so 81 of 1,221 files of the rdu-13 import failed (DEV-8100).
symptoms:
  - "NoSuchFileException on the uploaded temp file during a parallel import"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit tempdir)

## Repos

| repo    | base_commit | branch                        | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ----------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-tempdir | rebase         | complete | [#4460](https://github.com/dasch-swiss/dsp-api/pull/4460) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 2     | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 2.1: `StorageServiceLive.createTempDirectoryScoped` uses `<base>/<timestamp>/<uuid>/<name>`, release deletes `<uuid>`; tests in `StorageServiceLiveSpec` and `IngestServiceSpec`

## Chunks

| id  | repo    | status   | commit(s)        | summary                                                                                                | blocker |
| --- | ------- | -------- | ---------------- | ------------------------------------------------------------------------------------------------------ | ------- |
| 2.1 | dsp-api | complete | 78e14fc, 2a73aa4 | `<timestamp>/<uuid>/<name>` temp folder, release deletes `<uuid>`; both new tests fail without the fix | none    |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 2: review (scala-zio, consistency, simplicity, security; sonnet). Verified and fixed: the `<uuid>` came
  from ZIO `Random`, whose `TestRandom` fixed seed gives parallel tests at the epoch-0 `TestClock` the same
  `<uuid>` folder, so one test's release could delete another's directory; now `UUID.randomUUID()`.
  Refuted: "empty timestamp folder is a new leak" (main also left `<timestamp>` behind; the plan keeps it).
  Not taken (suggestions): single-segment check on `directoryName`/`prefix` (all callers pass validated
  shortcodes or constants), `raceFirst` on the test's first `await`, hoisting the formatter, splitting the
  StorageServiceLiveSpec test (the plan names its three checks).
- Phase 2: `just` and `markdownlint` are not on PATH in the cloud container (no Nix shell). Gates run as their
  Bazel commands: `just test-unit` targets 7/7 passed; scalafmt format-test for ingest, SPDX and lint tests
  passed. markdownlint over the journal was not run; CI runs it.

## Owner decisions

- 2026-11-27: where long runs go (card cmsg_01K9EcSWLHypmAgDdAKJ5xCE4BVoiRkpm2AHHUvxmggsjB): Ivan chose his Mac;
  this unit's `just test-unit` ran in the cloud before the answer

## Closeout

- root_cause: The upload temp folder was `<temp>/<yyyyMMdd_HHmmss>/<shortcode>-ingest`, so two uploads to one project in the same second shared it, and the first release deleted the second upload's file.
- investigation: The 81 failures of 1,221 matched the second-resolution folder name. ZIO `Random` was rejected for the folder ID: `TestRandom`'s fixed seed gives parallel tests the same folder.
- solution: Every upload gets `<temp>/<timestamp>/<uuid>/<shortcode>-ingest` from `UUID.randomUUID()`, released by `acquireRelease`.
- prevention: `StorageServiceLiveSpec` runs two parallel uploads at the same clock second.
