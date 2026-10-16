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
| 4 | reviewed | dsp-api@94bb8b85d | 1 |
| 5 | reviewed | dsp-api@b921f8fb3 | 1 |
| 6 | reviewed | dsp-api@ed917ecd1 | 1 |

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
| 4.1 | dsp-api | domain/service/ViewRestrictionsByPropertyService.scala, repo/ViewRestrictionsByPropertyRepo.scala, AdminDomainModule.scala, by-property specs and fixtures | — | all Phase 4 checkboxes | /property-values sums the cached per-class rows for the property; zero for a property not in the index | ViewRestrictionsByPropertyService.scala propertyValues; ViewRestrictionsByPropertyRepo.scala valueCountsQuery | — |
| 5.1 | dsp-api | domain/service (ViewRestrictionsAudience, ValueCountFold, ViewRestrictionsService), repo/ViewRestrictionsRepo.scala drill-down queries, specs | — | class drill-down tests, restricted-literal function, standoff filter, STR IN lists per branch, class BIND, empty lists, algebra test, quote test, All-branch test | /items lists restricted user link values, never standoff ones; REGEX case appears; counts and drill-down agree | ViewRestrictionsRepo.scala resourcePageQuery/resourceCountForDrillDownQuery/resourceQuery/valueQuery | — |
| 5.2 | dsp-api | ViewRestrictionsByPropertyService.scala, ViewRestrictionsByPropertyRepo.scala drill-down queries, spec | 5.1 | property drill-down tests, LinkValue filter removed, STR IN list, empty list or unknown property runs no query | /property-items lists restricted user link values | ViewRestrictionsByPropertyRepo.scala drillDown* queries | — |
| 5.3 | dsp-api | ViewRestrictionsRepo.scala, ViewRestrictionsByPropertyRepo.scala, AdminRepoModule/CacheManager wiring, legacy fixtures and suites, goldens | 5.1, 5.2 | remove REGEX, EhCache + CacheManager, ProjectClasses and probes, legacy fixtures and suites; drill-down goldens | no REGEX, LinkValue filter or subClassOf+ probe remains | ViewRestrictionsRepo.scala ProjectClasses, projectClassesQuery, multi-typed probes | — |

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
| 4.1 | dsp-api | complete | 44901bf36 | /property-values from cached batched rows of all classes (parallelism 4) + ValueCountFold; zero for a property outside the index without loads; per-property count query removed | none |
| 4.2 | dsp-api | complete | 0e53cde70 | Phase 4 review fixes: valueRowsOfProject on the row cache, shared test fixtures, constraint comments without measurements, property endpoint docs with the multi-type caveat, 8 tests checked by mutation | none |
| 5.1 | dsp-api | complete | 0f42c017c | class drill-down: standoff filter on ?prop, STR(?permissions) IN lists per branch from cached rows via restrictedLiterals, class bound with BIND, empty branches skipped; REGEX constant removed from the class repo; multi-typed resource listed under each class | none |
| 5.2 | dsp-api | complete | 567237905 | property drill-down: LinkValue filter removed, STR(?permissions) IN from the property's cached rows, empty page without a query for a property outside the index or without restricted literals; REGEX copy removed; legacy builder suite replaced by render tests | none |
| 5.3 | dsp-api | complete | 54a9f9864 | removed ProjectClasses, project-class discovery, multi-type probes, their EhCache and the repo CacheManager dependency, legacy RDF4J fixtures and suites; golden files for every drill-down query | none |
| 5.4 | dsp-api | complete | 6fcb28c70 | Phase 5 review fixes: one permissionFilter, DrillDownBranches, item-type-specific row loads, VALUES ?resource page anchor, stale texts, documented cache and ontology limits, positive query assertions | none |
| 6.1 | dsp-api | complete | c0ba5fa53 | e2e: class drill-down lists the restricted hasOtherThingValue of project-thing-1; /properties lists hasOtherThingValue, not hasStandoffLinkToValue; /property-values reports it; restriction specs and full e2e suite pass | none |
| 6.2 | dsp-api | complete | ce52bcefc | stage measurement of the final queries: 0 differences, no query over the timeout; LIMC cold by-property 46 s and drill-down 18.8 s accepted, follow-up DEV-7532 | none |
| 6.3 | dsp-api | complete | 4865c22ed, e404ef352 | REVIEW.md and SPARQL guide rules; webapi-admin ARCH-MAP entry (hand edit, schema 2 migration DEV-7534) | none |
| 6.4 | dsp-api | complete | d57f5fb22 | Phase 6 review fixes: REVIEW.md items point to the guide, batching item added, STR() test scope stated, map enforcement for batching, e2e test name | none |
| 7.1 | dsp-api | complete | 866630043, 00f870d75 | final whole-branch review fixes: unknown classes skip the cache, one item-type constraint, sealed DrillDownBranches, value items de-duplicated, shared helpers, unused creator join removed, comments once, counts/drill-down parity test; ARCH-MAP RDF4J line | none |

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

- Phase 6 `ARCH-MAP.md` update: the map is still schema 1 (last verified 2026-09-13, 715 files changed since). Owner
  decision 2026-10-16: hand-edit only the webapi-admin entry and keep the frontmatter; DEV-7534 tracks the full
  `/dune:dune-map update` migration. The map's count of 59 DTO imports could not be reproduced; the entry records 50
  with its command.
- Plan criterion "LIMC by-property wall time on a new cache below 40 s": measured 46 s on stage (164 batches through
  4 permits; slowest query 1.75 s). Owner decision 2026-10-16: accept; the criterion is per query, and the first
  request on a new cache may take longer. The LIMC drill-down page of `CatalogLIMC` took 18.8 s (worst case,
  superset literals); accepted for this PR, follow-up DEV-7532.
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
- Chunk 4.1: a mutation that removed the filter to the requested property passed all worker tests (every fixture
  had one counted property for each class). The session added "counts only the values of the requested property";
  it fails under the mutation.
- Phase 4 review DUNE-001 (repo count methods are public; only a doc points to the cache): recorded only. The
  services and the cache share a package, so visibility cannot restrict the methods to the cache;
  `valueRowsOfProject` is the shared read to follow. Full webapi unit suite passed at 44901bf36.
- Chunk 5.1: the algebra check catches a plain `?permissions IN` rewrite in the count and resource queries, but
  Jena does not rewrite it inside the page templates, so the check cannot fail there. All queries use
  `STR(?permissions) IN`, so this is a limit of the test, not a defect.
- Phase 5 review, accepted and documented (chunk 5.4): (1) the drill-down literals come from the cached counts, so
  an object whose permissions changed after the last `/classes` refresh (at most 10 minutes) can be missing until
  the next refresh; counts and drill-down still agree with each other. (2) The class value branch accepts any
  `hasValue` sub-property except the standoff link value, while the counts use the ontology index, so a value of a
  property missing from the ontology cache can be listed but not counted; the counts log a warning for such
  properties. A `VALUES ?prop` list would close the gap but measured slower in the design (C2).
- Phase 5 review: the graph-scoping constraint is still stated (`ViewRestrictionsRepo.scala:418-422`) and tested
  (`ViewRestrictionsQuerySpec`, "no view-restrictions query uses GRAPH scoping").
- Full webapi unit suite passed at 54a9f9864.
- Chunk 6.1: the e2e worker returned no report ("placeholder"); the session verified the diff, ran the two specs
  (the three new tests passed) and the full e2e suite (passed, 359.6 s). The worker had not run `just fmt`; the
  session formatted and amended the local, unpushed commit.
- Phase 6 review DUNE-001 (`refreshProject` is public, so a second invalidator is possible): recorded only. A
  read-only view of the cache for the property service would enforce it structurally; no code calls it outside
  `classSummaries` today. Measurements stay in the SPARQL guide, which already records incidents with numbers.

## Closeout

- root_cause: the restrictions report excluded every `knora-base:LinkValue` with
  `FILTER NOT EXISTS { ?value a knora-base:LinkValue }` in all 13 value queries, and the by-property list rejected
  every link value property (`!isLinkValueProp`). The intent was to hide the system-managed standoff link values,
  but the filter matched user link values too, so restricted user links never reached the counts or the drill-down.
  The drill-down also pre-filtered with a REGEX that judged a literal open when any clause granted anonymous View,
  which disagrees with `PermissionUtilADM` for literals such as `V knora-admin:UnknownUser|RV knora-admin:KnownUser`.
- investigation: a stage measurement (design doc 01) compared the current queries with shapes that classify in
  Scala. Pattern order was decisive: `?value knora-base:isDeleted false` has two bound terms, so Fuseki without
  statistics ran it before `?value` was bound and scanned the store (timeouts). Keeping the flag as a variable
  (`?valueDeleted`) fixed it. Paging a grouped result does not bound the query time; batching the input
  (`VALUES ?resource`) does, with identical rows and a lower total. Jena rewrites a plain-literal `IN` list into a
  `UNION` with the literal in the triple pattern; `STR(?permissions) IN` blocks it. Batch size 5000 exceeds Jetty's
  default form limit, so 2000 was chosen. Dead ends: a project-wide value query (56 s on LIMC), `VALUES ?prop`
  (slower), and a sub-select barrier.
- solution: `/classes` reads one project-wide resource-count query; `/values` and `/property-values` read per-class
  value rows listed and counted in batches of 2000 resources through a 4-permit semaphore, cached per project and
  class in `ViewRestrictionsRowCache` (10-minute TTL, refreshed only by `/classes`). Scala classifies properties
  with the ontology cache (`ValueKind`: user link values count, `hasStandoffLinkToValue` never), folds rows per item
  type (`ValueCountFold`) and resolves visibility once per literal (`ViewRestrictionsAudience`). The drill-downs
  select restricted objects with `FILTER (STR(?permissions) IN (...))` from the same classified literals, drop only
  `?prop = knora-base:hasStandoffLinkToValue`, bind the class directly and anchor page rows with `VALUES ?resource`.
  `ProjectClasses`, the multi-type probes, their EhCache, the REGEX and the legacy RDF4J fixtures are gone.
  Accepted: a multi-typed resource counts once for each class; a class with only deleted resources is not listed;
  the first by-property request on a new LIMC cache takes 46 s (each query stays under the timeout).
- prevention: golden files pin every count and drill-down query; a Jena algebra test pins the `STR()` rule; a parity
  test pins that counts and drill-down agree for every item type and audience; mutation checks confirmed the key
  tests. `REVIEW.md` and the SPARQL guide record the unbound-variable, `STR()` and batch-the-input rules. Follow-ups:
  DEV-7532 (drill-down speed on large classes), DEV-7534 (ARCH-MAP schema 2 migration). Worker reports were
  inaccurate several times (missing edits, skipped red steps, an unformatted commit, an empty report): verify each
  chunk with `git status`, the test report and a compiling mutation before committing.
