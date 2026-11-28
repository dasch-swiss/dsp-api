---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-filename
started: 2026-11-27
unit: filename
unit_state: done
final_state: owner-decisions
curated_from: 475d3d4a815e7d7b53cdf8473b7ccf5b57fb7909
started_from: bc4f51b9a1cbbc3e039893dee0d11a6c182751f0
problem: >
  An upload whose filename has an unsupported extension or path information is refused by the tapir
  path decoder with a generic 400 that does not say what is wrong (DEV-8094).
symptoms:
  - "POST .../ingest/m-ds-kirche-18_1.gif gives a 400 that does not name the extension or the supported list"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit filename)

## Repos

| repo    | base_commit | branch                         | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ------------------------------ | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-filename | rebase         | complete | [#4470](https://github.com/dasch-swiss/dsp-api/pull/4470) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 7     | reviewed | dsp-api@680b31d | 1                 |

## Chunk queue

- 7.1 ingest: `postProjectAsset` takes `path[String]("filename")`, validated in the handler (400 with the reason);
  `AssetFilename.from` names the extension and the supported list; specs
- 7.2 docs: accepted extensions in `docs/03-endpoints/ingest.md`; `SupportedFileTypeSpec` checks the list

## Chunks

| id      | repo    | status   | commit(s) | summary                                                                                  | blocker |
| ------- | ------- | -------- | --------- | ---------------------------------------------------------------------------------------- | ------- |
| 7.1+7.2 | dsp-api | complete | 0ca13cb   | filename validated in the handler, reason names extension and list; docs list is checked | none    |
| 7.3     | dsp-api | complete | 949d593   | review round 1: the route description names the 400 for a rejected filename              | none    |

## Deferrals

- Phase 7: the bulk-ingest upload route (`postBulkIngestUpload`) still answers a rejected filename with
  `BadRequest.invalidPathVariable`, which echoes the raw filename, so its 400 shape differs from the single upload.
  Pre-existing and not in the plan.

## Deviations

## Side findings

- Phase 7: the new endpoint and `AssetFilename` tests fail with the main code reverted; the docs-list test needs the
  file as Bazel `data`, so the root package exports `docs/03-endpoints/ingest.md`.

- Phase 7 review (scala-zio, consistency, security; sonnet). Fixed: the OpenAPI description of the upload route did
  not name the 400. Refuted: stale assertions on the old message (grep over the repository finds none); the docs test
  needs the Bazel runfiles directory (Bazel is the only test runner). Left: the 400 echoes the rejected extension,
  which the plan asks for. Pre-existing, not in this unit: `docs/01-introduction/file-formats.md` lists the extensions
  by hand and lacks `htm`.
- Phase 7: `just test-unit` run as its Bazel line: 7 targets pass. `just check` markdownlint run as
  `markdownlint-cli` 0.44.0 on the changed markdown, which lacks MD060; the tables are aligned by hand.

- Stack on `impl/ingest-hardening-sipi-errors`: the rebase applied without conflicts and left the unit's diff
  unchanged, so the review was not re-run; `just test-unit` (as its Bazel line) passes on the rebased head.

## Owner decisions

- none

## Closeout

- root_cause: The tapir path decoder refused an upload whose filename had an unsupported extension with a generic 400 that named neither the extension nor the supported list (DEV-8094).
- investigation: The bulk-ingest upload route keeps its pre-existing 400 shape (`invalidPathVariable`); not in the plan, filed as a follow-up.
- solution: `AssetFilename.from` names the extension and the supported list; the single upload route answers it as a 400 `errors` body; `docs/03-endpoints/ingest.md` lists the extensions and `SupportedFileTypeSpec` checks the list against the code. Final review: ARCH-MAP ingest and admin entries updated, ingest fingerprint rewritten.
- prevention: `AssetFilenameSpec`, `SupportedFileTypeSpec`, `ProjectsEndpointSpec`.
