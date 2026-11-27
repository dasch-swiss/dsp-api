---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dasch-swiss/dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-standoff-uri
started: 2026-11-27
unit: standoff-uri
unit_state: done
curated_from: 8f8ce86bcd700643cddbab4307d02f8828196440
started_from: 3d27db4bcb744cdf61b8150d131590eca0a1350c
problem: >
  A standoff link to a Wayback Machine URL (`https://web.archive.org/web/<ts>/https://...`) is rejected with
  "URI invalid", because `Iri.validateAndEscapeIri` uses a `UrlValidator` without `ALLOW_2_SLASHES` (DEV-8095).
symptoms:
  - "BadRequestException: URI invalid: https://web.archive.org/web/20260616062143/https://..."
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit standoff-uri)

## Repos

| repo    | base_commit | branch                             | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ---------------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-standoff-uri | rebase         | complete | [#4465](https://github.com/dasch-swiss/dsp-api/pull/4465) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 9     | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 9.1: `StandoffLinkUri.validate` (escape, then `UrlValidator` with `ALLOW_2_SLASHES`), used by `StandoffTagUtilV2`
  for `StandoffUriTag`, `www.` fallback kept; `StandoffLinkUriSpec`

## Chunks

| id  | repo    | status   | commit(s) | summary                                                                                          | blocker |
| --- | ------- | -------- | --------- | ------------------------------------------------------------------------------------------------ | ------- |
| 9.1 | dsp-api | complete | d313173   | `StandoffLinkUri.validate` used for standoff links; Wayback tests fail without `ALLOW_2_SLASHES` | none    |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 9: review (scala-zio, security, dune; sonnet). `just test-unit` passed (7 of 7 targets).
  Fixed: a test that `Iri.validateAndEscapeIri` still rejects the Wayback URLs, so the boundary is pinned.
  Refuted: "`ALLOW_2_SLASHES` accepts `//` in every path, not only Wayback URLs" (REQ-13 asks for exactly that);
  security found no issue (schemes stay http/https, output is XML-escaped, storage is a typed literal).
  Left for `final`: `ARCH-MAP.md` webapi-standoff key entities gain `StandoffLinkUri` with the dune-map update
  (Ship order step 3). Not taken: a pointer from `Iri.urlValidator` to `StandoffLinkUri` (the plan keeps `Iri`
  unchanged); a `www.` fallback test (private branch in `StandoffTagUtilV2`, unchanged logic).
- Stack on `impl/ingest-hardening-report` (2026-11-27): clean rebase, `git range-diff` shows the commit unchanged,
  so the review was not re-run; `just test-unit` passed again (7 of 7).

## Closeout

- root_cause: `Iri.validateAndEscapeIri` uses a `UrlValidator` without `ALLOW_2_SLASHES`, so a standoff link to a Wayback Machine URL (`//` in the path) was rejected (DEV-8095).
- investigation: Changing `Iri.validateAndEscapeIri` would loosen every SmartIri; the fix is scoped to standoff link targets.
- solution: `StandoffLinkUri.validate` accepts http and https URLs with `//` in the path and escapes as before; `StandoffTagUtilV2` uses it for `valueHasUri`. Final review: a doc comment that misnamed what `Iri.validateAndEscapeIri` guards reworded.
- prevention: `StandoffLinkUriSpec` pins both sides: Wayback URLs pass here and still fail `Iri.validateAndEscapeIri`.
