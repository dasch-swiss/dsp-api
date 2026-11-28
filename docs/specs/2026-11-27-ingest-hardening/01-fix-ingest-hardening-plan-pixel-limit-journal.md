---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 680b31d81ae9d64c500374d00892772b61d2376c
branch: impl/ingest-hardening-pixel-limit
started: 2026-11-27
unit: pixel-limit
unit_state: done
started_from: 52646863a175264f74afd35d9813def50b052391
curated_from: 983623ea2ddfda288fe86e387e2dea0bb7e88a3e
problem: >
  Very large images reach sipi, which runs out of memory or is killed while converting them (DEV-8101).
  Phase 5 reads width and height from the file header and rejects an image above a configurable pixel
  count with 422 image_too_large before sipi runs.
symptoms:
  - "sipi killed or out of memory on very large TIFF and JP2 uploads"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit pixel-limit)

## Repos

| repo    | base_commit | branch                            | merge_strategy | status   | pr                                                        |
| ------- | ----------- | --------------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 680b31d     | impl/ingest-hardening-pixel-limit | rebase         | complete | [#4468](https://github.com/dasch-swiss/dsp-api/pull/4468) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 5     | reviewed | dsp-api@680b31d | 1                 |

## Chunk queue

- 5.1 ingest: `ImageHeaderReader`, `max-image-pixels` in `ingest.conf` and `SipiConfig`, the check in
  `IngestService.handleImageFile`; tests
- 5.2 docs: the setting in the ingest service configuration and the deployment configuration page

## Chunks

| id  | repo    | status   | commit(s) | summary                                                                                                                                   | blocker |
| --- | ------- | -------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| 5.1 | dsp-api | complete | 875a82c   | header reader, config, check before sipi; the rejection test fails without the check                                                      | none    |
| 5.2 | dsp-api | complete | aad1e57   | `INGEST_SIPI_MAX_IMAGE_PIXELS` documented                                                                                                 | none    |
| 5.3 | dsp-api | complete | 2f1f0cf   | review round 1: unsigned JP2 and TIFF dimensions, one jp2h level and at most 16 boxes per level; both new tests fail on the previous code | none    |

## Deferrals

- Phase 5: the JP2 check trusts the `ihdr` box; a crafted file whose codestream `SIZ` declares more pixels than
  `ihdr` passes the check. Reading `SIZ` is beyond the plan (header from `jp2h`/`ihdr`); an owner question for
  `final`.
- Phase 5: the check fails open by design (an unreadable header lets sipi decide), so a header that ImageIO cannot
  read (for example BigTIFF) skips the limit. Same plan decision; noted for `final`.

## Deviations

## Side findings

- Phase 5 review (scala-zio, consistency, security, simplicity; sonnet; one re-review of the fix). Fixed and
  measured: nested `jp2h` boxes recursed without a limit (`StackOverflowError` on 100000 levels, which is not caught
  by `orElseSucceed`); JP2 `ihdr` read as signed, so a width of 2^32 - 1 gave `None` and skipped the limit; a file of
  many tiny boxes was walked to its end. Refuted: read the header only when a limit is set (the plan logs every
  image's dimensions); `ImageIO` out of memory on a crafted IFD (not reproduced, speculative); CR/LF in the logged
  filename (existing log lines log the same name; the logger writes structured fields).

- Phase 5: `just` is not on PATH in the cloud container (the Nix dev shell does not load); recipes run as the Bazel
  commands they wrap, markdownlint as `markdownlint-cli` 0.41.0 (no MD060; tables aligned by hand).
- Phase 5: the plan names `ImageIO.setUseCache(false)`; the reader opens a `FileImageInputStream` directly instead,
  which never uses the disk cache and leaves the global ImageIO setting alone.

## Owner decisions

- 2026-11-28 (final), card cmsg_01K9EcSWLHypmAgDdAKJ5xCEHAg17zVup9BzxMxwfvs6sC: Ivan accepted the JP2 `ihdr` and fail-open gaps for this release, tracked in DEV-8128. Rejected: close the unreadable-header gap only; close both.

## Closeout

- root_cause: Very large images reached sipi, which ran out of memory or was killed (DEV-8101).
- investigation: Review found and fixed unbounded `jp2h` recursion, signed `ihdr` reads, and walking many tiny boxes to the end of the file.
- solution: `ImageHeaderReader` reads width and height from TIFF, PNG, JPEG (ImageIO, no pixels decoded) and JP2/JPX (`jp2h`/`ihdr`); `IngestService` answers 422 `image_too_large` with the pixel count and the limit when `max-image-pixels` (`INGEST_SIPI_MAX_IMAGE_PIXELS`) is set. Final review: an unreadable header is logged as a warning before the check is skipped.
- prevention: `ImageHeaderReaderSpec`, `IngestServiceSpec`. Gaps Ivan accepted (DEV-8128): JP2 trusts `ihdr`, not the codestream `SIZ`; an unreadable header lets sipi decide.
