---
title: "fix: Bind the internal-reference lookup to the value's own standoff tag"
date: 2026-10-08
author: Nora Ammann
status: draft
linear: DEV-7468
---

# fix: Bind the internal-reference lookup to the value's own standoff tag

## Overview

The read queries for resources serve a wrong `href` target ID in `textValueAsXml` when the store holds more than one internal reference. Bind the internal-reference OPTIONAL to `?standoffNode` and add a regression test that runs the query against an in-memory triplestore.

## Problem Statement / Motivation

The standoff branch of the value query has this OPTIONAL:

```sparql
OPTIONAL {
  ?standoffTag knora-base:standoffTagHasInternalReference ?targetStandoffTag .
  ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
}
```

`?standoffTag` occurs nowhere else, so the OPTIONAL matches every internal reference in the whole store. The CONSTRUCT then puts `knora-base:targetHasOriginalXMLID` with each of these IDs on every standoff node of the value. `StandoffTagUtilV2` (`modules/webapi/src/main/scala/org/knora/webapi/messages/util/standoff/StandoffTagUtilV2.scala:158`) reads one value of this property per tag, so the selected ID is arbitrary.

Observed in `MaintenanceBackfillValueHasXmlE2ESpec` (DEV-7458): a single-reference value got `#_note1` instead of `#link_id`. The mutual-reference value got `#_note1` where `#_ref-note1` is correct. Feature 3 (serve `textValueAsXml` from storage) compares stored and served XML, so this bug blocks it.

## Proposed Solution

In each affected OPTIONAL, replace `?standoffTag` with `?standoffNode`. The Gravsearch main query already uses this binding (`GravsearchMainQueryGenerator.scala:384-397`), so the fix aligns the three hand-written templates with it.

Affected sites (same bug, one more than the issue names):

1. `modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/GetResourcePropertiesAndValuesQuery.scala:186-189` (current values).
2. Same file, `:368-371` (versioned values).
3. `modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/SearchResultResourcesQuery.scala:88-91` (full-text and label search results, called from `SearchResponderV2.scala:1169`).

## Technical Considerations

- A standoff tag has at most one `standoffTagHasInternalReference` (`objectClassConstraint` to one target tag). After the fix, each node gets at most one `targetHasOriginalXMLID`.
- The fix also removes a store-wide cross product, so the queries get cheaper. No behavior change for values without internal references.
- The `standoffTagFilter` branch has no such OPTIONAL. No change there.
- The SPARQL is built by the `sparql"..."` interpolator. Keep it that way (CLAUDE.md rule).
- `GetResourcePropertiesAndValuesQuerySpec` and `SearchResultResourcesQuerySpec` compare rendered SPARQL against inline expected strings (lines 316 and 128). Update these strings. They are not file-based golden tests.

## Implementation Approach

TDD order: the behavior test first, then the fix, then the rendering tests.

- [ ] Add a failing regression test `GetResourcePropertiesAndValuesQueryStoreSpec` in `modules/webapi/src/test/scala/org/knora/webapi/slice/resources/repo/`. Load an inline TriG fixture through `TestTripleStore.setDatasetFromTriG` and `TriplestoreServiceInMemory.emptyLayer` (pattern: `ResourcesRepoLiveSpec.scala:1429`). The fixture holds one resource with two text values. Value A: one tag with `originalXMLID "link_id"` and one tag that refers to it. Value B: two tags that refer to each other (`_note1`, `_ref-note1`). Add the class and property hierarchy triples the query needs (`rdfs:subClassOf* knora-base:Resource`, `rdfs:subPropertyOf* knora-base:hasValue`).
- [ ] In the test, run `GetResourcePropertiesAndValuesQuery.build(..., queryStandoff = true)` through `TriplestoreService.query(Construct)`. Assert that each referring tag has exactly one `targetHasOriginalXMLID`, equal to the ID of its own target.
- [ ] Add the same assertion for the versioned shape (`maybeVersionDate = Some(...)`, with a `valueCreationDate` before it on both values).
- [ ] Add the same assertion for `SearchResultResourcesQuery.build(..., queryStandoff = true)`.
- [ ] Run `bazel test //modules/webapi:test --test_filter='.*(GetResourcePropertiesAndValuesQueryStoreSpec).*'`. Confirm that the new tests fail with more than one ID per tag.
- [ ] Fix the current-values OPTIONAL in `GetResourcePropertiesAndValuesQuery.scala`.
- [ ] Fix the versioned-values OPTIONAL in `GetResourcePropertiesAndValuesQuery.scala`.
- [ ] Fix the OPTIONAL in `SearchResultResourcesQuery.scala`.
- [ ] Update the expected SPARQL string in `GetResourcePropertiesAndValuesQuerySpec.scala` (line 316 and the versioned-standoff string, if one exists).
- [ ] Update the expected SPARQL string in `SearchResultResourcesQuerySpec.scala` (line 128).
- [ ] In `MaintenanceBackfillValueHasXmlE2ESpec.scala:426`, change the mutual-reference test to also compare stored XML with served XML (`storedEqualsServed`). Remove the DEV-7468 comment.
- [ ] Run `bazel test //modules/webapi:test --test_filter='.*(GetResourcePropertiesAndValuesQuery|SearchResultResourcesQuery).*'`. All tests pass.
- [ ] Run `just test-unit`. All tests pass.
- [ ] Run `bazel test //modules/test-e2e:test --test_filter='.*MaintenanceBackfillValueHasXmlE2ESpec.*'`. All tests pass.
- [ ] Run `just fmt`, then `just check`. No findings.

## Acceptance Criteria

- [ ] Each standoff tag in the CONSTRUCT result of the three query shapes has at most one `targetHasOriginalXMLID`, and it is the ID of the tag that the tag refers to.
- [ ] The served `textValueAsXml` of the single-reference and the mutual-reference values in the E2E spec equals the stored `valueHasXml`.
- [ ] No other query output changes for values without internal references.

## Dependencies & Risks

- Risk: a consumer depends on the cross-product output. Low: `StandoffTagUtilV2` reads one ID per tag, and the Gravsearch path already emits the bound form.
- Risk: the E2E spec shares one JVM and a fixed port. See the test-isolation memory for DEV-7458 specs.

## Success Metrics

The E2E mutual-reference test compares served XML without a workaround and passes.

## References

- Linear: DEV-7468; related DEV-7458 (backfill, PR #4380).
- Consumer: `StandoffTagUtilV2.scala:158`.
- Correct reference form: `GravsearchMainQueryGenerator.scala:384-397`.
- Memory: bazel `--test_filter` is a regex, not a glob.
