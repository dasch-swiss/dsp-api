---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-empty-text
started: 2026-11-27
unit: empty-text
unit_state: done
curated_from: 00a26e10357ea2c58f19a235945afada621b23ad
started_from: ca7e9c33733db4222c7af9d9d2c3473a1e40aa94
problem: >
  A text value with a blank knora-api:valueAsString and no XML is rejected with the generic
  "Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or
  knora-api:textValueHasMapping" message, which does not tell the client that the text is empty (DEV-8098).
symptoms:
  - "400: Invalid combination of knora-api:valueHasString, knora-api:textValueAsXml, and/or knora-api:textValueHasMapping"
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit empty-text)

## Repos

| repo    | base_commit | branch                           | merge_strategy | status   | pr                                                        |
| ------- | ----------- | -------------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-empty-text | rebase         | complete | [#4466](https://github.com/dasch-swiss/dsp-api/pull/4466) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 10    | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 10.1 resources: `TextValueContentV2.from` fails a blank-but-present `valueAsString` without XML with
  "Text value must not be empty"; `ValueContentV2Spec` and `ValuesEndpointsE2ESpec` tests

## Chunks

| id   | repo    | status   | commit(s) | summary                                                                                                                                             | blocker |
| ---- | ------- | -------- | --------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| 10.1 | dsp-api | complete | e1d60b0   | blank valueAsString without XML fails with 'must not be empty'; 2 new tests fail with the fix reverted; e2e assertion compiles, not run (no Docker) | none    |
| 10.2 | dsp-api | complete | 5532516   | review round 1: one read of valueAsString in `from`; the blank check runs after the mapping lookup, so a bad mapping IRI keeps its own error        | none    |

## Deferrals

- Phase 10: `ValuesEndpointsE2ESpec` needs Docker, which this environment lacks; `final` runs it.
- Phase 10: `ARCH-MAP.md` fingerprint for the resources component may be stale; `final` runs `/dune:dune-map update`.

## Deviations

## Side findings

- Review: `scala-zio-reviewer` and `dune-reviewer` (sonnet). Verified Warning (dune, double read of
  `valueAsString` with two meanings of blank) fixed in 10.2. No Critical.

## Closeout

- root_cause: A text value with a blank `valueAsString` and no XML fell through to the generic invalid-combination message; on update, the parser swallowed the content error so a permissions-only update could pass, then failed with 'No value content or permissions provided' (DEV-8098).
- investigation: The create path was fixed in the unit; the update path (`PUT /v2/values`, `ValuesEndpointsE2ESpec`) surfaced in CI.
- solution: `ValueMessagesV2` says 'Text value must not be empty'; `ApiComplexV2JsonLdRequestParser.updateValueV2fromJsonLd` keeps the content error and returns it when there are no permissions either (final review fixup).
- prevention: `ValueContentV2Spec`, `ApiComplexV2JsonLdRequestParserSpec` (update without permissions), `ValuesEndpointsE2ESpec`.
