---
plan: docs/specs/2026-10-15-restrictions-count-queries/02-fix-restrictions-link-values-plan.md
target_repo: /Users/noraammann/Documents/Cloned_GitHub/dsp-api
base_commit: 8e26dbf63
branch: investigate/restrictions-matrix-query
started: 2026-10-15
problem: >
  The restrictions overview drops every knora-base:LinkValue from its value queries, so restricted user link values
  are not visible in the migration-readiness report (DEV-7390). The drill-down REGEX prefilter also disagrees with
  PermissionUtilADM for some literals, and the count queries are slower than the measured R/C5 shapes.
symptoms:
  - Restricted user link values do not appear in /values, /property-values, /items or /property-items.
  - /properties does not list user link value properties.
  - A resource with "V knora-admin:UnknownUser|RV knora-admin:KnownUser" is dropped from the drill-down.
status: in-progress
---

# Execution Journal: 02-fix-restrictions-link-values-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 8e26dbf63 | investigate/restrictions-matrix-query | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@8e26dbf63 | 1 |
| 2 | reviewed | dsp-api@6260e451a | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | slice/admin/domain/service/ViewRestrictionsClassification.scala (new), repo/ViewRestrictionsByPropertyRepo.scala, test repo/ViewRestrictionsByPropertyRepoSpec.scala, new test spec | — | ValueKind tests (3), add ValueKind + index, replace isValueProperty, update ByPropertyRepoSpec | ValueKind.of classifies link/file/plain/none; /properties filter uses it | ViewRestrictionsByPropertyRepo.scala:214-228 § isValueProperty, :67-75 § projectValueProperties | — |
| 1.2 | dsp-api | slice/admin/domain/service/ValueCountFold.scala (new), repo/ViewRestrictionsRepo.scala (ValueCountRow), both services, new test spec, service specs | 1.1 | fold tests (3), move classify/foldRows, implement fold, warning log | fold drops deleted/unknown, maps item types, one warning per fold; both services use ValueCountFold | ViewRestrictionsService.scala:59-111, ViewRestrictionsByPropertyService.scala:55-132 | — |
| 2.1 | dsp-api | repo/ViewRestrictionsRepo.scala, test repo/ViewRestrictionsQuerySpec.scala, test resources golden files | — | R query + parser, golden R, C5 query + parser, golden C5, C5 Scaladoc on pattern order, remove orphan golden files | R/C5 render exactly the plan text; goldens pinned through GoldenTest | ViewRestrictionsRepo.scala:597-705 (current count queries), ValueHasXmlBackfillQuerySpec.scala (GoldenTest pattern) | — |
| 2.2 | dsp-api | domain/service/ViewRestrictionsService.scala, ViewRestrictionsByPropertyService.scala (OntologyRepo only), repo/ViewRestrictionsRepo.scala, test service specs, legacy fixtures | 2.1 | service tests (4), OntologyRepo in services, test double, classSummaries via R, valueCounts via C5, remove 2 old count queries + fixtures, multi-typed count tests | /classes and /values use R/C5 + fold; link values count, standoff not | ViewRestrictionsService.scala classSummaries/valueCounts; CardinalityServiceLiveSpec.scala:173-182 (OntologyCacheFake layer) | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 0d74acb3b | ValueKind.of and ProjectOntologyIndex; by-property list counts user link value properties, not hasStandoffLinkToValue; spec moved to ViewRestrictionsClassificationSpec | none |
| 1.2 | dsp-api | complete | 3948eadf7 | ValueCountRow; ValueCountFold shares classify/foldRows/audienceUser/visibilityOf and adds permissionCounts + fold; warning only for IRIs absent from the ontology cache (index.knownProperties) | none |
| 1.3 | dsp-api | complete | ab75ddab2 | Phase 1 review fixes: ViewRestrictionsAudience split, warning text, sequential logger tests, deduplicated audience tests, dangling link, isCounted | none |
| 2.1 | dsp-api | complete | 2b382475a | resourceCountsQuery/valueCountsQuery + parsers + ResourceCountRow; goldens; in-memory count spec; 9 orphan goldens removed | none |
| 2.2 | dsp-api | complete | 21eabe6bc | /classes via resourceCounts + resource-class filter, /values via valueCounts + ValueCountFold; ProjectOntologyIndex.load; old count queries and fixtures removed; link/standoff/second-ontology/deleted-class tests | none |
| 2.3 | dsp-api | complete | c887b1c1d | Phase 2 review fixes: malformed rows fail with InconsistentRepositoryDataException, PermissionCountRow.groupId removed, stale class-list and ProjectClasses docs, fixture helpers | none |

## Deferrals

- Phase 2 checkboxes "Give both services an OntologyRepo" and "Provide OntologyCacheFake ... in both service specs":
  split; the by-property service parts moved to Phase 3, where that service first uses the index.
- Phase 1 review DUNE-001/DUNE-003 (stale `ARCH-MAP.md`, admin-domain import of API DTOs): Phase 5 checkbox
  "Update `ARCH-MAP.md` for the webapi-admin component".

## Deviations

- none

## Side findings

- The two services keep `audienceUser`, `visibilityOf`, `classify` and `foldRows` as deliberate copies, and the
  Scaladoc of `ViewRestrictionsByPropertyService` records why (avoid the old `groupBy` seam). The plan merges them
  into `ValueCountFold`; that Scaladoc must change with the move.
- Chunk 1.1: the worker reported an edit to `ViewRestrictionsByPropertyRepo.scala` that was not on disk; the session
  made it inline. A compiling mutation of the standoff check failed the new test, so the spec runs.
- Chunk 1.2: as briefed, the fold warned for every property outside `valueProperties`, so every link target
  (`isPartOf`, `hasLinkTo` objects carry `hasPermissions`) would log a warning on real data. The session added
  `ProjectOntologyIndex.knownProperties` and warns only for IRIs absent from the ontology cache (test first).
- Phase 1 review DUNE-002 (package cycle repo <-> domain/service through `ValueCountRow`/`PermissionCountRow` and
  `ValueKind`): recorded only; the same edge pattern already exists in the admin slice.
- Phase 1 review: `ValueKind.Link` has the same count effect as `Plain` today; kept distinct on purpose (decision 4).
- Phase 2 review: the performance reviewer claimed no service calls `projectClasses`; a grep showed the drill-down
  still does (`ViewRestrictionsService.scala:124`), as planned until Phase 4. Not acted on.
- Phase 2 review: no warning when `/classes` drops a class outside `index.resourceClasses`, because list nodes and
  ontologies are dropped on every request; the Scaladoc states it instead. Index memoization skipped (milliseconds).
- Full webapi unit suite passed at 21eabe6bc (21.4 s).
