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
| 3 | reviewed | dsp-api@6c1347ccd | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | slice/admin/domain/service/ViewRestrictionsClassification.scala (new), repo/ViewRestrictionsByPropertyRepo.scala, test repo/ViewRestrictionsByPropertyRepoSpec.scala, new test spec | — | ValueKind tests (3), add ValueKind + index, replace isValueProperty, update ByPropertyRepoSpec | ValueKind.of classifies link/file/plain/none; /properties filter uses it | ViewRestrictionsByPropertyRepo.scala:214-228 § isValueProperty, :67-75 § projectValueProperties | — |
| 1.2 | dsp-api | slice/admin/domain/service/ValueCountFold.scala (new), repo/ViewRestrictionsRepo.scala (ValueCountRow), both services, new test spec, service specs | 1.1 | fold tests (3), move classify/foldRows, implement fold, warning log | fold drops deleted/unknown, maps item types, one warning per fold; both services use ValueCountFold | ViewRestrictionsService.scala:59-111, ViewRestrictionsByPropertyService.scala:55-132 | — |
| 2.1 | dsp-api | repo/ViewRestrictionsRepo.scala, test repo/ViewRestrictionsQuerySpec.scala, test resources golden files | — | R query + parser, golden R, C5 query + parser, golden C5, C5 Scaladoc on pattern order, remove orphan golden files | R/C5 render exactly the plan text; goldens pinned through GoldenTest | ViewRestrictionsRepo.scala:597-705 (current count queries), ValueHasXmlBackfillQuerySpec.scala (GoldenTest pattern) | — |
| 2.2 | dsp-api | domain/service/ViewRestrictionsService.scala, ViewRestrictionsByPropertyService.scala (OntologyRepo only), repo/ViewRestrictionsRepo.scala, test service specs, legacy fixtures | 2.1 | service tests (4), OntologyRepo in services, test double, classSummaries via R, valueCounts via C5, remove 2 old count queries + fixtures, multi-typed count tests | /classes and /values use R/C5 + fold; link values count, standoff not | ViewRestrictionsService.scala classSummaries/valueCounts; CardinalityServiceLiveSpec.scala:173-182 (OntologyCacheFake layer) | — |
| 3.1 | dsp-api | domain/service/ViewRestrictionsRowCache.scala (new), ViewRestrictionsService.scala, admin layer wiring, test cache spec, service spec layers | — | cache tests (4), RowCache with makeCache, R cache, C5 cache, remove EhCache/CacheManager, wiring, class report uses cache, removal on /classes | single-flight, failures not kept, per-project removal, cross-tab reuse | AssetPermissionsCache.scala:34-110, AssetPermissionsCacheSpec.scala | — |
| 3.2 | dsp-api | ViewRestrictionsByPropertyService.scala, ViewRestrictionsByPropertyRepo.scala, by-property spec, legacy fixtures | 3.1 | OntologyRepo + fixture for by-property, property-values sum test, standoff zero test, propertyValues via cache + foreachPar(4) + fold, zero for unknown property, remove by-property valueCountsQuery | /property-values sums C5 rows of all classes for the property | ViewRestrictionsByPropertyService.scala propertyValues | — |
| 3.3 | dsp-api | assets/bench_batch.py, assets/results/*batch*, assets note | — | bench script, stage run, equality check, batch size, assets note | summed batch rows equal unbatched rows; slowest LIMC batch under 10 s at the chosen size | assets/bench.py, bench_c5.py | — |
| 3.4 | dsp-api | repo/ViewRestrictionsRepo.scala, domain/service/ViewRestrictionsRowCache.scala, ValueCountFold or row-sum helper, specs, goldens | 3.3 | repo equality test, sum test, listing query + golden, batched query + golden, loader change, remove unbatched query | the value-count loader lists, batches and sums; equality with the unbatched result on the fixture | ViewRestrictionsRepo.scala valueCountsQuery, ViewRestrictionsRowCache.scala layer | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 0d74acb3b | ValueKind.of and ProjectOntologyIndex; by-property list counts user link value properties, not hasStandoffLinkToValue; spec moved to ViewRestrictionsClassificationSpec | none |
| 1.2 | dsp-api | complete | 3948eadf7 | ValueCountRow; ValueCountFold shares classify/foldRows/audienceUser/visibilityOf and adds permissionCounts + fold; warning only for IRIs absent from the ontology cache (index.knownProperties) | none |
| 1.3 | dsp-api | complete | ab75ddab2 | Phase 1 review fixes: ViewRestrictionsAudience split, warning text, sequential logger tests, deduplicated audience tests, dangling link, isCounted | none |
| 2.1 | dsp-api | complete | 2b382475a | resourceCountsQuery/valueCountsQuery + parsers + ResourceCountRow; goldens; in-memory count spec; 9 orphan goldens removed | none |
| 2.2 | dsp-api | complete | 21eabe6bc | /classes via resourceCounts + resource-class filter, /values via valueCounts + ValueCountFold; ProjectOntologyIndex.load; old count queries and fixtures removed; link/standoff/second-ontology/deleted-class tests | none |
| 2.3 | dsp-api | complete | c887b1c1d | Phase 2 review fixes: malformed rows fail with InconsistentRepositoryDataException, PermissionCountRow.groupId removed, stale class-list and ProjectClasses docs, fixture helpers | none |
| 3.1 | dsp-api | complete | f10def074 | ViewRestrictionsRowCache (zio-cache, 10 min TTL, failures not kept, refreshProject: invalidate R, reload R, invalidate each fresh class key); class report reads through it; 4 cache tests | none |
| 3.3 | dsp-api | complete | 673719bc7 | stage measurement of batched value counts: 0 differences at 500/2000/5000; slowest LIMC Monument batch 3.35 s at 5000; listing 3.28 s for 55,824 resources; batch size 5000 | none |
| 3.4 | dsp-api | complete | 1431e2e25 | resourceListingQuery + batched valueCountsQuery (VALUES ?resource first), sumBatches, batch size 5000, parallelism 2; equality test with batch size 1; empty class runs no value query | none |
| 3.5 | dsp-api | complete | 288f3f523 | Phase 3 review fixes: refresh drops old and new class rows, batch size 2000, 4-permit semaphore for batch queries, class capacity 2000, private constants and test-only overload, docs, stronger single-flight test | none |

## Deferrals

- 2026-10-16 design change (owner decision): value counts are read in resource batches (decision 6). Phase 3 becomes
  "Row cache and batched value counts" with a stage measurement first; the by-property items move to a new Phase 4;
  the drill-down and the final phase become Phases 5 and 6.
- Phase 3 checkbox "Remove the EhCache `viewRestrictionsProjectClasses` ...": moved to Phase 4, because the
  drill-down uses `projectClasses` (and its EhCache) until Phase 4 removes `ProjectClasses`.
- Phase 2 checkboxes "Give both services an OntologyRepo" and "Provide OntologyCacheFake ... in both service specs":
  split; the by-property service parts moved to Phase 3, where that service first uses the index.
- Phase 1 review DUNE-001/DUNE-003 (stale `ARCH-MAP.md`, admin-domain import of API DTOs): Phase 5 checkbox
  "Update `ARCH-MAP.md` for the webapi-admin component".

## Deviations

- Batch size: the plan's rule ("largest measured size whose slowest LIMC batch stays under 10 s") selects 5000.
  Phase 3 review: at 5000 IRIs the form-encoded request body is about 295 KB, above Jetty's 200,000-byte default form
  limit; the repo does not raise it, and only stage is known to accept it. 2000 IRIs is about 118 KB, with the same
  measured total (19.2 s against 19.7 s) and a slowest LIMC batch of 0.84 s. Chosen: 2000 (chunk 3.5). Owner informed
  in the session.
- Chunk 3.4: the batching (list, batch, sum) lives in `ViewRestrictionsRepo.valueCounts`, not in the loader of
  `ViewRestrictionsRowCache` as the plan checkbox says; the cache key and rows stay the same. Data-access policy
  belongs in the repo, and the cache stays generic.

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
- Chunk 3.1: `refreshProject` reloads R first and then invalidates the value key of each class in the fresh rows, so
  removal also works when no older R entry was cached. A mutation that keeps failures in the cache failed the
  failure-retention test.
- Full webapi unit suite passed at 1431e2e25.
