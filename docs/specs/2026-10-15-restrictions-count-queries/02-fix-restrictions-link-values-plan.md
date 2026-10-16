---
title: "fix: restrictions overview counts user link values, with count queries classified in Scala"
type: fix
date: 2026-10-15
author: "Nora Ammann"
status: reviewed
repositories: []
linear: DEV-7390
---

# fix: restrictions overview counts user link values, with count queries classified in Scala

## Overview

The fix has two parts:

1. Include user link values in every count and every drill-down. Exclude only standoff link values
   (`knora-base:hasStandoffLinkToValue`).
2. Use the fastest measured query shapes, from the stage measurement in `01-…-design.md`:
   - Step 1 uses R. Step 2 uses C5 for each class, in batches of resources so that each query stays under the timeout.
   - The by-property counts come from the C5 rows of all classes, in a cache for each project.
   - Scala does all classification.

The drill-down keeps its paging in SPARQL. Its REGEX prefilter changes to an exact list of restricted permission
literals, and Scala makes that list.

## Problem Statement / Motivation

The report has three problems:

- **Link values.** Every value query has `FILTER NOT EXISTS { ?value a knora-base:LinkValue . }` (13 sites in
  `ViewRestrictionsRepo` and `ViewRestrictionsByPropertyRepo`). `isValueProperty` also rejects `isLinkValueProp`.
  As a result, restricted user links are not visible: 428,682 values on LIMC and 42,635 on BEOL. The report is a
  migration-readiness gate.
- **REGEX.** The drill-down REGEX does not agree with `PermissionUtilADM` for some literals, for example `V
  knora-admin:UnknownUser|RV knora-admin:KnownUser`. It drops rows that are restricted for authenticated users.
- **Speed.** Step 1 takes 2.5–16.4 s. R takes 0.4–2.8 s and gives identical counts. The by-property loop sends one query
  for each property and takes 23–156 s for each project.

## Proposed Solution

| Endpoint | Today | After |
| --- | --- | --- |
| `/classes` | class discovery + 2 multi-type probes + resource counts | **R**, classes filtered in Scala |
| `/values` | `valueCountsByPermissionQuery` (2 templates) | **C5** for the class in resource batches, from the cache, folded in Scala for each item type |
| `/properties` | ontology cache | unchanged; the filter now accepts user link value properties |
| `/property-values` | `valueCountsQuery` for each property | C5 rows of all classes, from the cache, filtered to the property in Scala |
| `/items`, `/property-items` | REGEX + `LinkValue` filter | `FILTER (?permissions IN (…))` with Scala-classified literals; standoff excluded by `?prop`; class bound directly |

R and C5 are copied exactly from the design doc. Their pattern order is load-bearing. A `?value` pattern with two bound
terms makes Jena scan the whole store.

```sparql
# R
SELECT ?resClass ?permissions (COUNT(*) AS ?cnt)
WHERE {
  ?resource knora-base:attachedToProject $project .
  ?resource a ?resClass ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
}
GROUP BY ?resClass ?permissions

# C5
SELECT ?prop ?permissions ?valueDeleted (COUNT(*) AS ?cnt) (COUNT(?comment) AS ?commented)
WHERE {
  ?resource a $resourceClass .
  ?resource knora-base:attachedToProject $project .
  ?resource knora-base:isDeleted false .
  ?resource ?prop ?value .
  ?value knora-base:hasPermissions ?permissions .
  ?value knora-base:isDeleted ?valueDeleted .
  OPTIONAL { ?value knora-base:valueHasComment ?comment . }
}
GROUP BY ?prop ?permissions ?valueDeleted
```

### Decisions

These decisions are fixed for this plan:

1. **By-property counts.** They come from the cached C5 rows, as the design doc recommends.
2. **Multi-typed resources.** They count once for each type. `ProjectClasses`, `anyMultiTypedResourceQuery` and
   `multiTypedQuery` are removed. The current code keeps a most-specific-class filter, so that a resource with a class
   and its subclass counts once (Scaladoc of `ProjectClasses.resClassPatterns`). That filter needs a `subClassOf+`
   probe of up to 27.3 s on LHTT, and R and C5 have no resource IRI to remove duplicates with. None of the three
   measured projects has a multi-typed resource, so the filter costs time and changes no count.
3. **REGEX.** The REGEX is replaced in this plan.
4. **Link item type.** A link value counts as item type `Value`. There is no new `Link` item type, so the API and
   dsp-app do not change.
5. **Structural links.** `isPartOf`, `isRegionOf`, `isAnnotationOf` and `hasRepresentation` count as user links. Only
   standoff links are excluded.
6. **Batched value counts.** The value counts of a class are read in batches of resources, so each query stays under
   the 40 s timeout as projects grow. Paging the grouped result rows with `LIMIT`/`OFFSET` would not bound the query
   time, because each page must scan and aggregate the whole class. The project-wide resource counts stay one query.

## Technical Considerations

### Classification

The classification is a set of pure functions and stateless data. No class holds state.

- **Location.** The types and pure functions go in the slice domain (`slice/admin/domain/service/`):
    - `ValueKind` with `ValueKind.of(info: ReadPropertyInfoV2): Option[ValueKind]`.
    - `ProjectOntologyIndex` with the pure builder `ProjectOntologyIndex.from(properties, classes)`.
    - `object ValueCountFold` with the fold functions.

  The repo does SPARQL and row parsing only. `ValueCountRow` is a repo output, so it goes in the
  `ViewRestrictionsRepo` companion, next to `PermissionCountRow`.
- **Shared visibility.** `classify` and `foldRows` are duplicated today (`ViewRestrictionsService.scala:71,94`,
  `ViewRestrictionsByPropertyService.scala:96,115`). Move them into `ValueCountFold`, and let both services use the one
  copy.

```scala
enum ValueKind { case Plain, File, Link }

final case class ProjectOntologyIndex(
  resourceClasses: Set[String],
  valueProperties: Map[String, ValueKind], // hasStandoffLinkToValue is absent
)

// ViewRestrictionsRepo companion
final case class ValueCountRow(
  classIri: String,
  propertyIri: String,
  permissions: String,
  deleted: Boolean,
  count: Int,
  commented: Int,
)
```

- **Index.** `ProjectOntologyIndex` comes from `OntologyRepo.findAll` (all cached ontologies, knora-base and shared
  ontologies included). Only that call is an effect, and it runs in the service. Property and class IRIs are unique
  across all ontologies, so one index for every ontology is correct. With it, values of a property from a shared or
  other-project ontology still count, as they do today through `subPropertyOf*`. `/properties` keeps its list from the
  project ontologies plus knora-base.
- **Class list.** `/classes` lists the resource classes that have one or more non-deleted resources (from R). A class
  whose resources are all deleted is no longer listed. Today it is listed with population 0.
- **totalValues.** The total for each item type is the sum of the rows of that type: `All` gives the value count, `File`
  the File rows, `Value` the Plain and Link rows, and `Comment` the sum of `commented`.
- **Folding.** The folding uses the index:
    - It drops deleted rows.
    - It drops properties that are not in the index: link targets, metadata and standoff links.
    - It maps each row to item types: `All` gives `count`, `File` gives File rows, `Value` gives Plain and Link rows,
      and `Comment` gives `commented`.
    - It reports unknown property IRIs with one `ZIO.logWarning` line for each fold, not one line for each row.
    - It is total: no `.get`, no `unsafeFrom`.

### Cache

The cache uses `zio.cache.Cache` 0.2.4. Concurrent `get` calls for one missing key share one lookup (single-flight).
This matters because the app sends `/property-values` requests in parallel.

- **Structure.** A new `final case class ViewRestrictionsRowCache` holds two caches, with a `ZLayer.scoped` layer. It
  follows `AssetPermissionsCache.scala:34-36,76-82,99-110`: a testable `makeCache(capacity, ttl)(resolve)` built with
  `Cache.makeWith`, and a `CacheKey` case class in the companion. The loaders come from the repo. The repo layer then
  needs only `TriplestoreService`, and the EhCache `viewRestrictionsProjectClasses` and its `CacheManager` wiring go
  away. Both services take `ViewRestrictionsRowCache` as a constructor argument.
- **Keys.** One cache holds the R rows for a project (key `ProjectIri`). One cache holds the C5 rows for a class (key
  `C5Key(projectIri: ProjectIri, classIri: String)`).
- **Settings.** TTL 10 minutes and capacity are companion `val`s, as `ProjectClassesTtl` is today. A failed lookup is
  kept for `Duration.Zero`, so a timeout does not block a key for 10 minutes.
- **Removal.** `zio-cache` 0.2.4 has no way to list keys (only `invalidate(key)` and `invalidateAll`). On `/classes`,
  the service reads the current R rows of the project and calls `invalidate` for each `C5Key` of those classes. Then it
  calls `invalidate` for the R key and loads R again. An entry for a class that is gone from R stays until its TTL ends.
  A load that runs at the same time can insert an entry again after the removal. The TTL limits this.
- **Lifetime.** Only `/classes` removes the entries of its project, because it starts a class-report run.
  `/properties` does not remove entries, so a change from the class tab to the property tab reuses the rows. A
  reviewer sees a permission change at the next class-report page load, or after 10 minutes at most.
- **Drill-down from a new cache.** `/property-items` needs the literals of the property, so on a new cache it also loads
  all classes first. The app normally opens the drill-down after the counts are loaded, so the cache is usually ready. A
  deep link on a new cache waits for the same load as `/property-values`.
- **Multi-typed resources, by property.** A multi-typed resource counts once for each type, also in `/property-values`.
  The property drill-down keeps `DISTINCT` values. For such a resource, the property total and the drill-down can
  therefore differ. This follows from decision 2, and the Scaladoc documents it.
- **By-property load.** On a new cache, `/property-values` loads every class with
  `ZIO.foreachPar(classes)(…).withParallelism(4)`. On LIMC, the slowest class takes 22 s and all classes take 64 s in
  sequence. The wall time is therefore about 22–25 s, below the 40 s query timeout. This number is not measured yet;
  Phase 6 measures it.

### Batched value counts

The value counts of one class come from two steps:

1. **List.** One query lists the IRIs of the class's live resources: `?resource a $resourceClass ;
   knora-base:attachedToProject $project ; knora-base:isDeleted false`. It has no value join and no aggregate.
2. **Count.** The value-count query runs once for each batch of `ValueCountBatchSize` resource IRIs, with
   `VALUES ?resource { … }` as the first pattern, so `?resource` is bound before `?resource ?prop ?value`. The other
   patterns and the pattern-order rule stay as they are.

Scala sums the batch rows by `(property, permissions, deleted)`. The batches are disjoint, because a value belongs to
one resource, so the sum equals the unbatched result. The cache keeps one row set for each class; only its loader
changes. Batches of one class run with a parallelism limit, so one class does not take all triplestore capacity.

Limits that stay:

- **Listing.** The listing query still grows with the class size. It reads one index and returns only IRIs. If the
  measurement shows that it does not stay well under the timeout for the largest measured class, it needs its own
  paging (follow-up).
- **Total time.** The total time for each class still grows linearly with its size. The 40 s timeout applies to each
  query, not to each request.
- **Batch size.** `ValueCountBatchSize` is a measured constant, not a guess. The measurement in Phase 3 sets it so
  that the slowest batch query on LIMC stays under 10 s, which leaves room for growth.

### Drill-down

The drill-down keeps its current shape and its paging in SPARQL. It changes in four places:

- **Standoff links.** `FILTER NOT EXISTS { ?value a knora-base:LinkValue . }` changes to `FILTER (?prop !=
  knora-base:hasStandoffLinkToValue)` in the class repo. This compares a bound variable and needs no index lookup. Jena
  does not rewrite `!=` by default (`TransformFilterInequality` is off). In the property repo, the filter goes away,
  because the property is bound.
- **REGEX.** `FILTER (!REGEX(?permissions, …))` changes to `FILTER (STR(?permissions) IN (…))`. The list holds the
  literals that are restricted for one or more audiences:
    - The resource branch uses the restricted literals of the R rows of the class.
    - The value branch uses the restricted literals of the C5 rows of the class.
    - The property drill-down uses the restricted literals of the C5 rows for the property.

  The `All` page and count queries thus carry two lists, one for each `UNION` branch. The drill-down and the counts
  agree by construction.
- **Why `STR()`.** Jena 6.2.0 rewrites `?permissions IN ("a", "b")` with plain string literals by default:
  `TransformExpandOneOf` makes a disjunction, and `TransformFilterDisjunction` with `TransformFilterEquality` makes a
  `UNION` with the literal in the triple pattern. Then `?value knora-base:hasPermissions "a"` has two bound terms and
  scans the whole store. `STR(?permissions)` is not a variable, so the equality transform does not apply. A unit test
  runs `Algebra.optimize(Algebra.compile(query))` on each rendered drill-down query. It asserts that the optimized
  algebra has no `knora-base:hasPermissions` triple with a constant object. The test proves only that the filter is not
  rewritten. It cannot show the final BGP order of TDB2.
- **Literals.** The literals go into the query through `Literal.string`, which escapes `\`, `"` and `'`
  (`modules/sparql-builder/src/main/scala/org/knora/sparqlbuilder/types.scala:122,151-159`).
- **Empty list.** If a list is empty, its branch has no rows. If all lists are empty, the result is an empty page with
  a total of 0, and no query runs.
- **Class binding.** `${classes.valuesClause}`, `resClassPatterns` and `FILTER (?resClass = $groupClass)` change to
  `?resource a $groupClass` plus `BIND($groupClass AS ?resClass)`. The row parsers read `?resClass`
  (`ViewRestrictionsRepo.scala:195,225`), so the variable must stay bound.

### Golden tests

`ViewRestrictionsQuerySpec` does not use `GoldenTest` today. It compares the queries with
`ViewRestrictionsLegacyFixtures` only. The files `ViewRestrictionsQuerySpec__*.txt` in
`modules/webapi/src/test/resources/org/knora/webapi/slice/admin/repo/` are not read by any spec. The plan removes
those orphan files, and adds `GoldenTest.assertGolden` tests for R, C5 and each drill-down query (pattern:
`ValueHasXmlBackfillQuerySpec.scala`). New golden content is captured through `TEST_UNDECLARED_OUTPUTS_DIR`, because
`rewrite = true` cannot write files under Bazel.

### Test seams

- **Pure tests.** `ValueKind`, the index builder and the fold need no layers.
- **Cache tests.** They use `makeCache` with a counting `Ref` and no triplestore, as
  `AssetPermissionsCacheSpec.scala` does.
- **Service tests.** They use `ViewRestrictionsServiceSpec` and `ViewRestrictionsByPropertyServiceSpec` with
  `OntologyCacheFake` plus `OntologyRepoLive` (pattern: `CardinalityServiceLiveSpec.scala:173-182`). The fixture
  ontology data includes the knora-base properties that the test uses.
- **Log tests.** They assert log lines with `ZTestLogger.logOutput` (pattern: `ValueHasXmlBackfillServiceSpec.scala:94`).

### Unchanged

`/properties`, the endpoints, the DTOs and dsp-app stay as they are.

## Implementation Phases

All test steps use `bazel test //modules/webapi:test --test_filter='.*ViewRestrictions.*'` (the filter is a regex, not a
glob).

### Phase 1: Classification of values in Scala

- [x] Write failing unit tests for `ValueKind`: a user link value property gives `Link`.
- [x] Write failing unit tests for `ValueKind`: `hasStandoffLinkToValue`, a link property, a standoff internal reference
      and a non-resource property give no kind.
- [x] Write a failing unit test for `ValueKind`: a file value property gives `File`.
- [x] Add `ValueKind` and `ProjectOntologyIndex` with its pure builder in `slice/admin/domain/service/`.
- [x] Move `classify` and `foldRows` from both services into `object ValueCountFold`, and let both services use it.
- [x] Replace `isValueProperty` with the `ValueKind` classification, so that `/properties` lists user link value
      properties and omits `hasStandoffLinkToValue`.
- [x] Update `ViewRestrictionsByPropertyRepoSpec`: "rejects the reified link-value property" becomes "accepts", and a
      new test rejects `hasStandoffLinkToValue`.
- [x] Write failing unit tests for the fold: deleted rows dropped, unknown properties dropped, and link targets (an
      `isPartOf` object with `hasPermissions`) dropped.
- [x] Write failing unit tests for the fold for each item type: `All`, `File`, `Value` (with a link value), `Comment`,
      and a link value with a comment.
- [x] Write a failing unit test: a value with a comment counts once under `All`.
- [x] Implement the fold.
- [x] Log one warning line for each fold that lists the unknown property IRIs, and assert the line with `ZTestLogger`.
- [x] Run the tests; they pass.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: R and C5 for the class report

- [x] Add a failing service test (in-memory Turtle in `ViewRestrictionsServiceSpec`): a restricted user link value
      counts under `/values`.
- [x] Add a failing service test: a restricted standoff link value does not count under `/values`.
- [x] Add a failing service test: a value property from a second ontology counts.
- [x] Add a failing service test: a class whose resources are all deleted is not listed by `/classes`.
- [x] Give `ViewRestrictionsService` an `OntologyRepo` as the index source (`ProjectOntologyIndex.load`).
- [x] Provide `OntologyCacheFake` plus `OntologyRepoLive` in `ViewRestrictionsServiceSpec`, with fixture ontology data.
- [x] Add the R query and its row parser to `ViewRestrictionsRepo`.
- [x] Add `GoldenTest` to `ViewRestrictionsQuerySpec`, and add a golden test for R.
- [x] Add the C5 query and its row parser.
- [x] Add a golden test for C5.
- [x] Add a Scaladoc comment on C5 that explains why `?value knora-base:isDeleted` keeps an unbound object.
- [x] Change `classSummaries` to R plus the resource-class filter of the index.
- [x] Change `valueCounts` to C5 plus the fold.
- [x] Remove `resourceCountsByClassAndPermissionQuery` and its legacy fixtures.
- [x] Remove `valueCountsByPermissionQuery` and its legacy fixtures.
- [x] Remove the orphan files `ViewRestrictionsQuerySpec__*.txt`, which no spec reads.
- [x] Change the multi-typed count tests: a multi-typed resource counts once for each type.
- [x] Run the tests; they pass.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Row cache and batched value counts

- [x] Write a failing cache test (`makeCache` with a counting `Ref`): two concurrent lookups for the same key run one
      load.
- [x] Write a failing cache test: a failed load is not kept, so the next lookup runs the load again.
- [x] Write a failing cache test: the project removal invalidates only the `C5Key`s of its own project.
- [x] Write a failing cache test for the cross-tab sequence `/classes`, `/values`, `/properties`, `/values`: the
      second `/values` runs no load.
- [x] Add `ViewRestrictionsRowCache` with `makeCache(capacity, ttl)(resolve)` (`Cache.makeWith`, 10-minute TTL on
      success, `Duration.Zero` on failure) and a `ZLayer.scoped` layer.
- [x] Add the R cache (key `ProjectIri`) to `ViewRestrictionsRowCache`.
- [x] Add the C5 cache (key `C5Key(projectIri, classIri)`) to `ViewRestrictionsRowCache`.
- [x] Wire `ViewRestrictionsRowCache` into the admin layers and the test layers.
- [x] Use the cache in the class report.
- [x] On `/classes`, invalidate the `C5Key` of each class in the current R rows, then the R key.
- [x] Add `assets/bench_batch.py`, which renders the listing query and the batched value-count query (same patterns as
      `valueCountsQuery`, with `VALUES ?resource { … }` first).
- [x] Run `bench_batch.py` on stage for the largest LIMC class (`Monument`) and one BEOL class, with batch sizes 500,
      2000 and 5000, and record each listing time and the slowest batch time in `assets/results/`.
- [x] Check with the measurement that the summed batch rows equal the unbatched value-count rows (0 differences).
- [x] Set `ValueCountBatchSize` from the measurement (2000; see the journal deviation on the form-size limit).
- [x] Add a short `assets/` note with the measured numbers and the chosen batch size, linked from the design doc.
- [x] Write a failing repo test: the batched loader returns the same rows as one unbatched query on the in-memory fixture,
      with a batch size smaller than the class.
- [x] Write a failing unit test: summing batch rows adds counts and `commented` per `(property, permissions, deleted)`.
- [x] Add the resource listing query and its golden test to `ViewRestrictionsRepo`.
- [x] Add the batched value-count query (`VALUES ?resource` first) and its golden test; keep the pattern-order Scaladoc.
- [x] Change `ViewRestrictionsRepo.valueCounts`, which the cache loader calls, to list, batch and sum, with a
      parallelism limit for the batches of one class.
- [x] Replace the unbatched `valueCountsQuery` and its golden file with the batched query.
- [x] Run the tests; they pass.
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 4: By-property counts from the cached rows

- [ ] Give `ViewRestrictionsByPropertyService` an `OntologyRepo` as the index source (`ProjectOntologyIndex.load`).
- [ ] Provide `OntologyCacheFake` plus `OntologyRepoLive` in `ViewRestrictionsByPropertyServiceSpec`, with fixture
      ontology data.
- [ ] Write a failing test: `/property-values` sums the C5 rows of all classes for the property.
- [ ] Write a failing test: `/property-values` for `hasStandoffLinkToValue` gives zero counts.
- [ ] Change `propertyValues` to load all classes through the cache with
      `ZIO.foreachPar(classes)(…).withParallelism(4)`.
- [ ] Change `propertyValues` to fold the rows for the requested property.
- [ ] Return zero counts for a property that is not in the index.
- [ ] Remove `valueCountsQuery` and its legacy fixtures from `ViewRestrictionsByPropertyRepo`.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 5: Drill-down: link fix and exact permission filter

- [ ] Add a failing service test: a restricted user link value appears in `/items`.
- [ ] Add a failing service test: a restricted user link value appears in `/property-items`.
- [ ] Add a failing service test: a restricted standoff link value appears in neither drill-down.
- [ ] Add a failing service test: a resource with `V knora-admin:UnknownUser|RV knora-admin:KnownUser` appears in
      `/items`.
- [ ] Add a failing service test: `resourceClassIri` is set on every `/items` row.
- [ ] Add a failing service test: for one fixture, the `/values` restricted total equals the number of value rows in
      `/items`.
- [ ] Add a pure function that selects the restricted literals from classified rows (an audience below View).
- [ ] Add a unit test: a literal with a quote and a backslash renders correctly in the `IN` list.
- [ ] Add a unit test: an `All` page query carries the R list in the resource branch and the C5 list in the value
      branch.
- [ ] In the class drill-down queries, replace the `LinkValue` filter with `FILTER (?prop !=
      knora-base:hasStandoffLinkToValue)`.
- [ ] In the class drill-down queries, replace the REGEX with `FILTER (STR(?permissions) IN (…))`, one list for
      each branch.
- [ ] In the class drill-down queries, replace the `ProjectClasses` holes with `?resource a $groupClass` and
      `BIND($groupClass AS ?resClass)`.
- [ ] In the property drill-down queries, remove the `LinkValue` filter.
- [ ] In the property drill-down queries, replace the REGEX with `FILTER (STR(?permissions) IN (…))`.
- [ ] Leave out a branch whose literal list is empty.
- [ ] Return an empty page with total 0, and run no query, when all literal lists are empty or the property is not in
      the index.
- [ ] Add a unit test that optimizes each rendered drill-down query with `Algebra.optimize(Algebra.compile(query))`
      and asserts that no `knora-base:hasPermissions` triple has a constant object.
- [ ] Remove `grantsViewToAnonymousRegex` from both repos.
- [ ] Remove the EhCache `viewRestrictionsProjectClasses` and the `CacheManager` dependency of the repo layer.
- [ ] Remove `ProjectClasses`, `projectClasses`, `projectClassesCache`, `projectClassesQuery`,
      `anyMultiTypedResourceQuery` and `multiTypedQuery`.
- [ ] Remove the legacy fixtures of the removed queries.
- [ ] Add golden tests for each drill-down query (capture through `TEST_UNDECLARED_OUTPUTS_DIR`).
- [ ] Remove the legacy-fixture suites, because the old builder output no longer applies.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 6: End-to-end tests, measurement, docs

- [ ] Extend `AdminViewRestrictionsE2ESpec`: `/items` for `anything:Thing` includes
      `http://rdfh.ch/0001/project-thing-1/values/0` (restricted `hasOtherThingValue`, `CR knora-admin:Creator`).
- [ ] Extend `AdminViewRestrictionsByPropertyE2ESpec`: `/properties` includes `anything:hasOtherThingValue`.
- [ ] Extend `AdminViewRestrictionsByPropertyE2ESpec`: `/properties` omits `knora-base:hasStandoffLinkToValue`.
- [ ] Extend `AdminViewRestrictionsByPropertyE2ESpec`: `/property-values` for `anything:hasOtherThingValue` reports
      restricted values.
- [ ] Run `just test-e2e`; it passes.
- [ ] Add `assets/bench_fix.py`, which renders the final R, C5 and drill-down queries and writes the rows and timings to
      `assets/results/`.
- [ ] Run `bench_fix.py` against stage for drawings-gods, BEOL and LIMC.
- [ ] Compare the R and C5 rows (link values removed) with the design-doc rows; the result is 0 differences.
- [ ] Measure the LIMC by-property wall time on a new cache; it is below 40 s.
- [ ] Measure each drill-down page and count query; each is below 40 s.
- [ ] Update the Scaladoc of both repos and both services: R/C5 shapes, pattern-order rule, cache, multi-typed counting.
- [ ] Update `ARCH-MAP.md` for the webapi-admin component with `/dune:dune-map` (new `ViewRestrictionsAudience`,
      `ValueCountFold`, `ViewRestrictionsClassification`, `ViewRestrictionsRowCache`; the admin-domain import of
      `ViewRestrictionsEndpoints` DTOs in the violation count).
- [ ] Add the pattern-order rule (no `?value` pattern with two bound terms before `?resource ?prop ?value`) to
      `REVIEW.md`.
- [ ] Run `just fmt`.
- [ ] Run `just check`; it passes.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Make the stage Fuseki credentials available to the bench scripts, the same way as for the design measurement | Nora Ammann | before start | Credential |
| H2 | Remove `permissions_sparql.md` from the repo root, or move it into the spec folder | Nora Ammann | before start | Personal working file |

## Acceptance Criteria

- [ ] A restricted user link value appears in `/values`, `/property-values`, `/items` and `/property-items`.
- [ ] A restricted standoff link value appears in none of them.
- [ ] `/properties` lists user link value properties and does not list `hasStandoffLinkToValue`.
- [ ] The drill-down and the counts use the same permission classification. The `V knora-admin:UnknownUser|RV
      knora-admin:KnownUser` case appears in both.
- [ ] On stage, R and C5 counts with the link fix applied match the design-doc rows (0 differences).
- [ ] No query on stage goes over the 40 s view-restrictions timeout.
- [ ] The slowest batched value-count query on LIMC stays under 10 s, and the summed batch rows equal the unbatched rows.
- [ ] No `REGEX`, `FILTER NOT EXISTS { ?value a knora-base:LinkValue }`, or `subClassOf+` probe remains in the
      restrictions repos.
- [ ] The API contract and dsp-app stay unchanged.

## Dependencies & Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| A later edit adds a `?value` pattern with two bound terms and causes a store-wide scan | Medium | Timeout | Golden tests pin R and C5; Scaladoc and `REVIEW.md` rule |
| Jena turns the permission `IN` list into a constant in the triple pattern (confirmed for plain `?permissions IN`) | High without `STR()` | Store-wide scan | `STR(?permissions) IN`; algebra unit test in Phase 5 |
| First by-property request, or a deep link to `/property-items`, on LIMC with a new cache is over the timeout | Low | Empty column or failed drill-down | Parallelism 4, one lookup for concurrent requests, measured in Phase 6 |
| A batch query or the listing query of a very large class goes over the timeout | Medium as projects grow | Empty `/values` row | Batches sized from the stage measurement (slowest LIMC batch under 10 s); listing measured in Phase 3, paged in a follow-up if needed |
| A failed lookup stays in the cache | Low | Key blocked for 10 min | `Duration.Zero` on failure; tested in Phase 3 |
| The cache shows old counts after a permission change | Low | Wrong review | Removal on `/classes`; 10-minute TTL |
| Ontology cache misses a property or class, and its rows drop | Low | Count too low | Index over all ontologies; warning log for each unknown property IRI, tested |
| Multi-typed resource counts twice (class and property totals) | Low (0 of 3 projects) | Count too high; property total and drill-down differ | Accepted; documented in Scaladoc |
| `/classes` no longer lists a class whose resources are all deleted | Low | Row missing (population was 0) | Stated rule; tested in Phase 2 |

## Success Metrics

Baseline from the stage measurement (`01-…-design.md`), medians:

| Project | Step 1 today → R | Step 2 today → C5 (sum) | By-property loop today |
| --- | --- | --- | --- |
| drawings-gods | 2.5 s → 0.5 s | 12.9 s → 12.6 s | 76.3 s |
| BEOL | 3.0 s → 0.4 s | 10.1 s → 9.0 s | 22.9 s |
| LIMC | 16.4 s → 2.8 s | 90.6 s → 64.1 s | 155.8 s |

Target: these times or better, plus the LIMC by-property wall time on a new cache below 40 s.

## References

- `docs/specs/2026-10-15-restrictions-count-queries/01-restrictions-count-queries-measurements-design.md`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/repo/ViewRestrictionsRepo.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/repo/ViewRestrictionsByPropertyRepo.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/domain/service/ViewRestrictionsService.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/domain/service/ViewRestrictionsByPropertyService.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/responders/admin/AssetPermissionsCache.scala` (zio-cache pattern)
- dsp-app `libs/vre/pages/project/project/src/lib/project-settings/view-restrictions/view-restrictions-page.service.ts`
  (stepped load, DEV-6778)
- Learnings: `dasch-specs/learnings/performance/sparql-filter-not-exists-for-rare-predicates.md`,
  `dasch-specs/learnings/performance/sparql-rdfs-subclassof-traversal-timeout-fuseki.md`,
  `dasch-specs/learnings/logic-errors/csv-export-missing-standoff-parameter.md`
- Linear: DEV-7390, DEV-6778
