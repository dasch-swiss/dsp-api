---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 680b31d81ae9d64c500374d00892772b61d2376c
branch: impl/ingest-hardening-sipi-pin
started: 2026-11-27
unit: sipi-pin
unit_state: done
curated_from: c374e0dd83b0ba1bae03124d5f1d230010a7fcd2
started_from: b805774639259585eead8ada4d49636cfbd9176e
problem: >
  The pinned sipi (v10.0.1, and v10.1.0 too) has Git LFS pointer text in its binary (DEV-8099).
  Phase 3 pins daschswiss/sipi:v10.2.0, the release that also emits the JSON error report Phase 4 reads.
symptoms:
  - "LFS pointer text in the sipi binary of v10.0.1"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit sipi-pin)

## Repos

| repo    | base_commit | branch                         | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ------------------------------ | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 680b31d     | impl/ingest-hardening-sipi-pin | rebase         | complete | [#4467](https://github.com/dasch-swiss/dsp-api/pull/4467) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 3     | reviewed | dsp-api@680b31d | 0                 |

## Chunk queue

- 3.1 build: pin `daschswiss/sipi:v10.2.0` by its two per-arch digests in `MODULE.bazel`, comment points to
  "Bumping Sipi" in `docs/05-internals/development/third-party.md`

## Chunks

| id  | repo    | status   | commit(s) | summary                                                                                    | blocker |
| --- | ------- | -------- | --------- | ------------------------------------------------------------------------------------------ | ------- |
| 3.1 | dsp-api | complete | 3b5ef74   | v10.2.0 digests and comment; LFS check 0; JSON error report seen in a chroot; images build | none    |

## Deferrals

Phase 3 review (consistency reviewer over the pin commit): no findings. Gates: `just test-unit` targets pass
(7 of 7); lint and license tests pass; markdownlint passes on this journal; scalafmt `format-test` not run (no
Scala file changed); `test-it` and `test-e2e` not run (no Docker daemon), left to `final`.

## Deviations

## Side findings

- `gh stack` is not installed in the cloud container, so the local stack was not recorded on entry. The repository
  has no `bdd.yaml`, so no hook needs it; the stack step runs on Ivan's Mac.
- `crane` is not on PATH in the cloud container (no Nix dev shell). The digests were read from the Docker Hub
  registry API directly; each manifest's sha256 was checked against the digest the index names.
- In the cloud container, `//modules/sipi:runtime_fs_amd64` fails in the linux sandbox: bsdtar runs as root in a
  user namespace and cannot chown to the image's 997/987. The v10.0.1 binary has the same owner, so this is not from
  the pin; `--strategy=CraneRootfsExtract=local` builds. Not changed here.
- `sipi query --json /nonexistent` exits 105 with CLI usage text and no JSON document: the CLI checks that the file
  exists before it runs. Relevant to Phase 4 (`sipi_no_report`).
- `V3ProjectsRestServiceSpec` "a service that triggers with its own export in the slot gets export_exists, then
  deletes and re-triggers" is flaky: `NoSuchFileException` on `.../temp/rdf` in `ProjectMigrationExportService.deleteExport`.
  It failed 5 of 10 runs on `origin/main` and on the stack top alike, so it is not from this stack. All other unit
  targets pass after the rebase onto `impl/ingest-hardening-empty-text`. A follow-up issue is for `final` to file.

## Closeout

- root_cause: The pinned sipi v10.0.1 (and v10.1.0) had Git LFS pointer text in its binary (DEV-8099).
- investigation: Digests read from the Docker Hub registry API and checked against the index, as `crane` was not available.
- solution: `MODULE.bazel` pins `daschswiss/sipi:v10.2.0` by index and per-arch digests; it also ships the JSON error report the sipi-errors unit reads.
- prevention: The LFS check over the image binary returns 0; bump per `docs/05-internals/development/third-party.md`.
