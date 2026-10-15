---
title: "fix: restrictions overview counts user link values, with count queries classified in Scala"
type: fix
date: 2026-10-15
author: "Nora Ammann"
status: draft
repositories: []
linear: DEV-7390
---

# fix: restrictions overview counts user link values, with count queries classified in Scala

## Overview

The fix has two parts:

1. Include user link values in every count and every drill-down. Exclude only standoff link values
   (`knora-base:hasStandoffLinkToValue`).
2. Use the fastest measured query shapes, from the stage measurement in `01-…-design.md`:
   - Step 1 uses R. Step 2 uses C5, one query for each class.
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
| `/values` | `valueCountsByPermissionQuery` (2 templates) | **C5** for the class, from the cache, folded in Scala for each item type |
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
   `multiTypedQuery` are removed. None of the three measured projects has a multi-typed resource.
3. **REGEX.** The REGEX is replaced in this plan.
4. **Link item type.** A link value counts as item type `Value`. There is no new `Link` item type, so the API and
   dsp-app do not change.
5. **Structural links.** `isPartOf`, `isRegionOf`, `isAnnotationOf` and `hasRepresentation` count as user links. Only
   standoff links are excluded.

## Technical Considerations

### Classification

The classification is a set of pure functions and stateless data, with no class that holds state.

```scala
enum ValueKind { case Plain, File, Link }

final case class ProjectOntologyIndex(
  resourceClasses: Set[String],
  valueProperties: Map[String, ValueKind], // hasStandoffLinkToValue is absent
)

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
  ontologies included). Property and class IRIs are unique across all ontologies, so one index for every ontology is
  correct. With it, values of a property from a shared or other-project ontology still count, as they do today through
  `subPropertyOf*`. `/properties` keeps its list from the project ontologies plus knora-base.
- **Class list.** `/classes` lists the resource classes that have one or more non-deleted resources (from R). A class
  whose resources are all deleted is no longer listed. Today it is listed with population 0.
- **totalValues.** The total for each item type is the sum of the rows of that type: `All` gives the value count, `File`
  the File rows, `Value` the Plain and Link rows, and `Comment` the sum of `commented`.
- **Folding.** The folding uses the index:
    - It drops deleted rows.
    - It drops properties that are not in the index: link targets, metadata and standoff links.
    - It maps each row to item types: `All` gives `count`, `File` gives File rows, `Value` gives Plain and Link rows,
      and `Comment` gives `commented`.
- **Visibility.** `classify` and `foldRows` in the services stay as they are.

### Cache

The cache uses `zio.cache.Cache` (pattern: `AssetPermissionsCache.scala`). It sends only one lookup when several
requests ask for the same key at the same time. This matters because the app sends `/property-values` requests in
parallel.

- **Keys.** One key holds the R rows for a project (`ProjectIri`). One key holds the C5 rows for a class (`(ProjectIri,
  classIri)`).
- **Lifetime.** Each entry lives for 10 minutes at most (TTL). Only `/classes` removes the entries of its project,
  because it starts a class-report run. `/properties` does not remove entries, so a change from the class tab to the
  property tab reuses the rows. A reviewer sees a permission change at the next class-report page load, or after 10
  minutes at most.
- **Failures.** Build the cache with `Cache.makeWith`, and keep a failed lookup for `Duration.Zero`, as
  `AssetPermissionsCache.scala:76-82` does. A timeout then does not block a key for 10 minutes. Set a fixed capacity.
  Remove the entries of a project by filtering `cache.keys` and calling `invalidate` on each key.
- **Drill-down from a new cache.** `/property-items` needs the literals of the property, so on a new cache it also loads
  all classes first. The app normally opens the drill-down after the counts are loaded, so the cache is usually ready. A
  deep link on a new cache waits for the same load as `/property-values`.
- **Multi-typed resources, by property.** A multi-typed resource counts once for each type, also in `/property-values`.
  The property drill-down keeps `DISTINCT` values. For such a resource, the property total and the drill-down can
  therefore differ. This follows from decision 2, and the Scaladoc documents it.
- **By-property load.** On a new cache, `/property-values` loads every class with `ZIO.foreachPar` and a parallelism
  limit of 4. On LIMC, the slowest class takes 22 s and all classes take 64 s in sequence. The wall time is therefore
  about 22–25 s, below the 40 s query timeout. This number is not measured yet; Phase 5 measures it.

### Drill-down

The drill-down keeps its current shape and its paging in SPARQL. It changes in four places:

- **Standoff links.** `FILTER NOT EXISTS { ?value a knora-base:LinkValue . }` changes to `FILTER (?prop !=
  knora-base:hasStandoffLinkToValue)` in the class repo. This compares a bound variable and needs no index lookup. In
  the property repo, the filter goes away, because the property is bound.
- **REGEX.** `FILTER (!REGEX(?permissions, …))` changes to `FILTER (?permissions IN (…))`. The list holds the literals
  that are restricted for one or more audiences. Scala classifies them from the R or C5 rows of the same scope, so the
  drill-down and the counts agree by construction.
- **Empty list.** If the list is empty, the result is an empty page with a total of 0, and no query runs.
- **Class binding.** `${classes.valuesClause}`, `resClassPatterns` and `FILTER (?resClass = $groupClass)` change to
  `?resource a $groupClass` plus `BIND($groupClass AS ?resClass)`. The row parsers read `?resClass`
  (`ViewRestrictionsRepo.scala:195,225`), so the variable must stay bound.

Jena can turn a `FILTER IN` or `=` into a constant inside the triple pattern (filter-equality transform). Then `?value
knora-base:hasPermissions "lit"` has two bound terms and scans the whole store. A unit test runs
`Algebra.optimize(Algebra.compile(query))` on the rendered drill-down queries. It checks that no basic graph pattern has
a constant object for `knora-base:hasPermissions`. If the transform occurs, wrap the expression as `STR(?permissions) IN
(…)`, which blocks the transform. The literals go into the query through `Literal.string`, which escapes quotes and
backslashes.

### Unchanged

`/properties`, the endpoints, the DTOs and dsp-app stay as they are.

## Implementation Phases

All test steps use `bazel test //modules/webapi:test --test_filter='.*ViewRestrictions.*'` (the filter is a regex, not a
glob).

### Phase 1: Classification of values in Scala

- [ ] Write failing unit tests for `ValueKind`: a user link value property gives `Link`.
- [ ] Write failing unit tests for `ValueKind`: `hasStandoffLinkToValue`, a link property, a standoff internal reference
      and a non-resource property give no kind.
- [ ] Write a failing unit test for `ValueKind`: a file value property gives `File`.
- [ ] Add `ValueKind`, `ProjectOntologyIndex` and a pure builder from `ReadPropertyInfoV2` / `ReadClassInfoV2`.
- [ ] Replace `isValueProperty` with the `ValueKind` classification, so that `/properties` lists user link value
      properties and omits `hasStandoffLinkToValue`.
- [ ] Update `ViewRestrictionsByPropertyRepoSpec`: "rejects the reified link-value property" becomes "accepts", and a
      new test rejects `hasStandoffLinkToValue`.
- [ ] Write failing unit tests for the fold: deleted rows dropped, unknown properties dropped, and link targets (an
      `isPartOf` object with `hasPermissions`) dropped.
- [ ] Write failing unit tests for the fold for each item type: `All`, `File`, `Value` (with a link value), `Comment`,
      and a link value with a comment.
- [ ] Write a failing unit test: a value with a comment counts once under `All`.
- [ ] Implement the fold.
- [ ] Log a warning for each unknown property IRI in the fold, and assert the log line in a test.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: R and C5 for the class report

- [ ] Add a failing service test (in-memory Turtle in `ViewRestrictionsServiceSpec`): a restricted user link value
      counts under `/values`.
- [ ] Add a failing service test: a restricted standoff link value does not count under `/values`.
- [ ] Add a failing service test: a value property from a second ontology counts.
- [ ] Add a failing service test: a class whose resources are all deleted is not listed by `/classes`.
- [ ] Give both services an `OntologyRepo` as the index source.
- [ ] Use the existing ontology test double in both service specs.
- [ ] Add the R query and its row parser to `ViewRestrictionsRepo`.
- [ ] Add a golden test for R.
- [ ] Add the C5 query and its row parser.
- [ ] Add a golden test for C5.
- [ ] Add a Scaladoc comment on C5 that explains why `?value knora-base:isDeleted` keeps an unbound object.
- [ ] Change `classSummaries` to R plus the resource-class filter of the index.
- [ ] Change `valueCounts` to C5 plus the fold.
- [ ] Remove `resourceCountsByClassAndPermissionQuery` with its golden files and legacy fixtures.
- [ ] Remove `valueCountsByPermissionQuery` with its golden files and legacy fixtures.
- [ ] Change the multi-typed count tests: a multi-typed resource counts once for each type.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Project cache and by-property counts from C5

- [ ] Write a failing test: two concurrent lookups for the same key run one query.
- [ ] Write a failing test: a failed lookup is not kept, so the next lookup runs the query again.
- [ ] Write a failing test: `/classes` removes only the entries of its own project.
- [ ] Write a failing test for the cross-tab sequence `/classes`, `/values`, `/properties`, `/values`: the second
      `/values` runs no query.
- [ ] Write a failing test: `/property-values` sums the C5 rows of all classes for the property.
- [ ] Write a failing test: `/property-values` for `hasStandoffLinkToValue` gives zero counts.
- [ ] Add the `zio.cache.Cache` for R rows (key `ProjectIri`): `Cache.makeWith`, 10-minute TTL on success,
      `Duration.Zero` on failure, fixed capacity.
- [ ] Add the `zio.cache.Cache` for C5 rows (key `(ProjectIri, classIri)`), with the same settings.
- [ ] Use both caches in the class report.
- [ ] Remove the entries of a project on `/classes`.
- [ ] Change `propertyValues` to load all classes through the cache (`ZIO.foreachPar`, parallelism 4).
- [ ] Change `propertyValues` to fold the rows for the requested property.
- [ ] Return zero counts for a property that is not in the index.
- [ ] Remove `valueCountsQuery` from `ViewRestrictionsByPropertyRepo` with its golden files and fixtures.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 4: Drill-down: link fix and exact permission filter

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
- [ ] In the class drill-down queries, replace the `LinkValue` filter with `FILTER (?prop !=
      knora-base:hasStandoffLinkToValue)`.
- [ ] In the class drill-down queries, replace the REGEX with `FILTER (?permissions IN (…))`.
- [ ] In the class drill-down queries, replace the `ProjectClasses` holes with `?resource a $groupClass` and
      `BIND($groupClass AS ?resClass)`.
- [ ] In the property drill-down queries, remove the `LinkValue` filter.
- [ ] In the property drill-down queries, replace the REGEX with `FILTER (?permissions IN (…))`.
- [ ] Return an empty page with total 0, and run no query, when the literal list is empty or the property is not in the
      index.
- [ ] Add a unit test that optimizes the rendered drill-down queries with `Algebra.optimize(Algebra.compile(query))` and
      asserts that no basic graph pattern has a constant object for `knora-base:hasPermissions`.
- [ ] If that test fails, change the filter to `STR(?permissions) IN (…)` until it passes.
- [ ] Remove `grantsViewToAnonymousRegex` from both repos.
- [ ] Remove `ProjectClasses`, `projectClasses`, `projectClassesCache`, `projectClassesQuery`,
      `anyMultiTypedResourceQuery` and `multiTypedQuery`.
- [ ] Remove the golden files and legacy fixtures of the removed queries.
- [ ] Update the drill-down golden files (capture through `TEST_UNDECLARED_OUTPUTS_DIR`).
- [ ] Remove the legacy-fixture suites, because the old builder output no longer applies.
- [ ] Run the tests; they pass.
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 5: End-to-end tests, measurement, docs

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
- [ ] No `REGEX`, `FILTER NOT EXISTS { ?value a knora-base:LinkValue }`, or `subClassOf+` probe remains in the
      restrictions repos.
- [ ] The API contract and dsp-app stay unchanged.

## Dependencies & Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| A later edit adds a `?value` pattern with two bound terms and causes a store-wide scan | Medium | Timeout | Golden tests pin R and C5; Scaladoc and `REVIEW.md` rule |
| Jena turns `?permissions IN (…)` into a constant in the triple pattern | Medium | Store-wide scan | Algebra unit test in Phase 4; `STR()` wrap as fallback |
| First by-property request, or a deep link to `/property-items`, on LIMC with a new cache is over the timeout | Low | Empty column or failed drill-down | Parallelism 4, one lookup for concurrent requests, measured in Phase 5 |
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
