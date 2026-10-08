---
plan: docs/specs/2026-10-08-internal-reference-xml-id/01-fix-internal-reference-xml-id-plan.md
target_repo: /Users/noraammann/Documents/Cloned_GitHub/dsp-api
base_commit: dc9e159cc23c9d441b143f29d558cf9d9b594181
branch: feature/dev-7468-served-textvalueasxml-carries-a-wrong-internal-reference-id
started: 2026-10-08
problem: >
  The read queries for resources bind an unbound `?standoffTag` in the internal-reference OPTIONAL. This
  makes a store-wide cross product, so a served `textValueAsXml` can carry the original XML ID of a
  different value's tag. Feature 3 (serve from storage) compares stored and served XML, so this blocks it.
symptoms:
  - A single-reference value is served with `href="#_note1"` instead of `#link_id`.
  - A mutual-reference value is served with `#_note1` where `#_ref-note1` is correct.
status: complete
---

# Execution Journal: 01-fix-internal-reference-xml-id-plan

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
|----|-------|------------|------------|------------|---------|----------|
| 1 | `StandoffInternalReferenceQuerySpec.scala` (new), `GetResourcePropertiesAndValuesQuery.scala`, `SearchResultResourcesQuery.scala`, `GetResourcePropertiesAndValuesQuerySpec.scala`, `SearchResultResourcesQuerySpec.scala` | — | regression spec, three OPTIONAL fixes, two rendering strings | new spec fails before the fix, all three query specs pass after it | `GetResourcePropertiesAndValuesQuery.scala:186-189,368-371`, `SearchResultResourcesQuery.scala:88-91`, fixture pattern `ResourcesRepoLiveSpec.scala:1429` | — |
| 2 | `MaintenanceBackfillValueHasXmlE2ESpec.scala` | 1 | E2E mutual-reference stored-equals-served | E2E spec passes | `MaintenanceBackfillValueHasXmlE2ESpec.scala:425-437` | — |
| 3 | — | 1, 2 | `just test-unit`, `just fmt`, `just check` | all green | — | — |

## Chunks

| id | status | commit(s) | summary | blocker |
|----|--------|-----------|---------|---------|
| 1 | complete | 8355ee920 | Regression spec (fails before fix with all IDs per tag), `?standoffNode` binding at three query sites, two rendering strings | none |
| 2 | complete | b4a310379 | E2E mutual-reference test also compares stored with served XML; workaround comment removed | none |
| 3 | complete | — | `just test-unit` green, E2E spec 59/59 green, `just fmt` and `just check` clean | none |
| 4 | complete | 8be481c67 | Review fix: spec asserts the full returned-tag map (presence of tags without reference, absence of decoys); revert check fails as expected | none |

## Deferrals

- none

## Side findings

- The internal-reference OPTIONAL exists in three hand-copied templates; the copy-paste spread the defect (dune review DUNE-001). Not extracted: the query files keep each shape as a complete template by design.

## Closeout

- root_cause: The internal-reference OPTIONAL in the hand-written read templates used `?standoffTag`, which no other pattern binds. The OPTIONAL therefore matched every internal reference in the store, and the CONSTRUCT put every target ID on every standoff node of the value. The parser keeps one object per predicate (`FlatStatements`), so the served ID was arbitrary.
- investigation: The backfill E2E spec (DEV-7458) exposed it: stored XML was correct, served XML was not. The same copy existed in `SearchResultResourcesQuery`, which the issue did not name. Gravsearch was already correct. Removing the OPTIONAL (the backfill approach) was rejected, because the fallback in `StandoffTagUtilV2` throws when the target is not in the value's loaded standoff.
- solution: Bind the lookup to `?standoffNode` at the three sites; update two rendered-SPARQL strings; let the E2E mutual-reference test compare served XML.
- prevention: `StandoffInternalReferenceQuerySpec` runs the three query shapes against the in-memory store, with decoy references in another resource, and asserts the full returned-tag map. Rendered-SPARQL string tests cannot catch this class of defect. The three template copies remain (dune finding DUNE-001, left as a leftover).
