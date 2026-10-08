---
title: "fix: Bind the internal-reference lookup to the value's own standoff tag"
date: 2026-10-08
author: Nora Ammann
status: implemented
linear: DEV-7468
---

# fix: Bind the internal-reference lookup to the value's own standoff tag

## Overview

The read queries for resources serve a wrong `href` target ID in `textValueAsXml` when the store holds more than one internal reference. Bind the internal-reference OPTIONAL to `?standoffNode` and add a regression test that runs the queries against an in-memory triplestore.

## Problem Statement / Motivation

The standoff branch of the value query has this OPTIONAL:

```sparql
OPTIONAL {
  ?standoffTag knora-base:standoffTagHasInternalReference ?targetStandoffTag .
  ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
}
```

`?standoffTag` occurs nowhere else, so the OPTIONAL matches every internal reference in the whole store. The CONSTRUCT then puts `knora-base:targetHasOriginalXMLID` with each of these IDs on every standoff node of the value. This includes nodes without a reference.

The parser collects the assertions of each tag into a `FlatStatements` map with one object per predicate (`ConstructResponseRdfDataParser.scala:259-263`, `ConstructResponseRdfData.scala:49`). Thus one arbitrary ID survives. `StandoffTagUtilV2.scala:158` reads that ID.

Observed in `MaintenanceBackfillValueHasXmlE2ESpec` (DEV-7458): a single-reference value got `#_note1` instead of `#link_id`. The mutual-reference value got `#_note1` where `#_ref-note1` is correct. Feature 3 (serve `textValueAsXml` from storage) compares stored and served XML, so this bug blocks it.

## Proposed Solution

In each affected OPTIONAL, replace `?standoffTag` with `?standoffNode`. The Gravsearch main query already uses this binding (`GravsearchMainQueryGenerator.scala:385-398`), so the fix aligns the three hand-written templates with it.

Affected sites (the issue names the first two):

1. `modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/GetResourcePropertiesAndValuesQuery.scala:186-189` (current values).
2. Same file, `:368-371` (versioned values).
3. `modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/SearchResultResourcesQuery.scala:88-91` (full-text and label search results, called from `SearchResponderV2.scala:1169`).

No other main-code query has this defect. The CONSTRUCT templates (`GetResourcePropertiesAndValuesQuery.scala:119,280`, `SearchResultResourcesQuery.scala:51`) stay unchanged.

## Technical Considerations

- A standoff tag has at most one `standoffTagHasInternalReference`. After the fix, each referring node gets exactly one `targetHasOriginalXMLID`, and nodes without a reference get none.
- The target lookup has no value or start-index constraint. A target tag in another value, or with a negative start index, still gives its ID. Gravsearch behaves the same.
- The fix removes a store-wide cross product, so the queries get cheaper.
- The `standoffTagFilter` branch has no such OPTIONAL. No change there.
- Keep the `sparql"..."` interpolator (CLAUDE.md rule).
- `GetResourcePropertiesAndValuesQuerySpec.scala:316` (`expectedStandoff`) and `SearchResultResourcesQuerySpec.scala:128` contain the only expected strings with this OPTIONAL. They are inline strings, not golden files. No versioned-standoff rendering test exists.
- Rendering tests do not catch this class of bug (learning `dasch-specs/learnings/logic-errors/query-builder-refactoring-breaks-ordering.md`). The behavior test against the in-memory store is the primary regression guard.

## Implementation Approach

TDD order: the behavior test first, then the fix, then the rendering tests.

- [x] Add a failing spec `StandoffInternalReferenceQuerySpec` in `modules/webapi/src/test/scala/org/knora/webapi/slice/resources/repo/`. Annotate it with `@RunWith(classOf[DspZTestJUnitRunner])`, or Bazel skips it silently. Provide `TriplestoreServiceInMemory.emptyLayer` and `StringFormatter.test`. No `BUILD.bazel` change is necessary (the test target globs `*Spec`).
- [x] Write an inline TriG fixture and load it through `TestTripleStore.setDatasetFromTriG` (pattern: `ResourcesRepoLiveSpec.scala:1429`). Contents:
    - Resource 1 of a subclass of `knora-base:Resource`, with `attachedToProject`, `attachedToUser`, `hasPermissions`, `creationDate`, `rdfs:label` and `isDeleted false`.
    - Value A on resource 1: one tag with `standoffTagHasOriginalXMLID "link_id"`, one tag that refers to it, and one tag without a reference.
    - Value B on resource 1: two tags that refer to each other (`_note1`, `_ref-note1`).
    - Resource 2 with a value that has an unrelated internal reference, so the store holds candidates outside resource 1.
    - On each value: `rdf:type`, `hasPermissions`, `valueHasUUID`, `valueCreationDate`, `valueHasStandoff`. Attach the values through a subproperty of `knora-base:hasValue`, with the `rdfs:subPropertyOf` triple.
    - On each tag: `rdf:type`, `standoffTagHasStartIndex` (0 or more), `standoffTagHasEndIndex`.
- [x] Test the current-values shape: run `GetResourcePropertiesAndValuesQuery.build(Seq(resource1), preview = false, withDeleted = false, queryStandoff = true)` through `TriplestoreService.query(Construct)`. Assert on the CONSTRUCT model, not on parsed responses. Each referring tag has exactly the set `{ID of its own target}` as `targetHasOriginalXMLID`. The tag without a reference has none.
- [x] Test the versioned shape with the same assertions (`maybeVersionDate` after all `valueCreationDate` and `creationDate` values).
- [x] Test `SearchResultResourcesQuery.build(Seq(resource1), queryStandoff = true)` with the same assertions.
- [x] Run `bazel test //modules/webapi:test --test_filter='.*StandoffInternalReferenceQuerySpec.*'`. Confirm that the three tests fail because tags carry more than one ID.
- [x] Fix the current-values OPTIONAL in `GetResourcePropertiesAndValuesQuery.scala`.
- [x] Fix the versioned-values OPTIONAL in `GetResourcePropertiesAndValuesQuery.scala`.
- [x] Fix the OPTIONAL in `SearchResultResourcesQuery.scala`.
- [x] Update `expectedStandoff` in `GetResourcePropertiesAndValuesQuerySpec.scala:316`. Do not touch other `standoffTag` occurrences (`standoffTagFilter`, `standoffTagHas…` predicates).
- [x] Update the expected string in `SearchResultResourcesQuerySpec.scala:128`.
- [x] In the mutual-reference test of `MaintenanceBackfillValueHasXmlE2ESpec.scala` (`:425-437`), add `storedEqualsServed(resource, value, StandoffMappingIri.StandardMapping)` after the backfill. Keep the stored-against-submitted assertion. Remove the DEV-7468 comment (`:426-427`).
- [x] Run `bazel test //modules/webapi:test --test_filter='.*(StandoffInternalReferenceQuery|GetResourcePropertiesAndValuesQuery|SearchResultResourcesQuery)Spec.*'`. All tests pass.
- [x] Run `just test-unit`. All tests pass.
- [x] Run `bazel test //modules/test-e2e:test --test_filter='.*MaintenanceBackfillValueHasXmlE2ESpec.*'`. All tests pass.
- [x] Run `just fmt`, then `just check`. No findings.
- [x] Commit as `fix(dsp-api): ...` with a body. Do not use "Knora" in the message.

## Acceptance Criteria

- [x] In the CONSTRUCT result of the three query shapes, each referring tag has exactly one `targetHasOriginalXMLID`: the ID of its own target.
- [x] Tags without an internal reference have no `targetHasOriginalXMLID`.
- [x] The served `textValueAsXml` of the single-reference and the mutual-reference values in the E2E spec equals the stored `valueHasXml`.

## Alternative Approaches Considered

Remove the OPTIONAL. The backfill query (DEV-7458) does this, and `StandoffTagUtilV2.scala:161-168` then looks up the ID on the target node in the value's own standoff. Not chosen: the fallback throws `InconsistentRepositoryDataException` when the target is not in the value's loaded standoff. The bound OPTIONAL keeps the current contract and matches Gravsearch.

## Dependencies & Risks

- Risk: a consumer depends on the cross-product output. Low: `StandoffTagUtilV2` reads one ID per tag, and Gravsearch already emits the bound form.
- Risk: a referring tag whose target has no `standoffTagHasOriginalXMLID` now gets no ID from the OPTIONAL, so it falls back to `StandoffTagUtilV2.scala:161-168`. That fallback can throw. Before the fix, a random ID from the store hid this case. Such data is inconsistent, so a failure is correct behavior.
- Risk: E2E specs share one JVM and a fixed port. See the test-isolation memory for DEV-7458 specs.

## Success Metrics

The E2E mutual-reference test compares stored and served XML without a workaround, and passes.

## References

- Linear: DEV-7468. Related: DEV-7458 (backfill, PR #4380).
- Backfill plan, "Dependencies & Risks": dasch-specs `specs/2026-09-10-store-xml-with-standoff/03-feat-backfill-value-has-xml-plan.md`.
- Learnings: `dasch-specs/learnings/logic-errors/resource-query-templates-enumerate-predicates.md`, `dasch-specs/learnings/best-practices/test-fixture-isolation-shared-data-scope.md`, `dasch-specs/learnings/configuration-errors/union-default-graph-differs-across-environments.md`.
- Bazel `--test_filter` is a regex, not a glob (`docs/development/dsp-api-conventions.md`).
