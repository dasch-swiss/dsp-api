---
title: "perf: graph-scoped restriction counts in one query per project"
date: 2026-10-16
author: "Nora Ammann"
status: approved
repositories: []
linear: DEV-7390
---

# perf: move the restriction counts back into the triplestore (PR #4392, DEV-7390)

## Context

A reviewer handoff (`~/Downloads/handoff/HANDOFF.md`, measured on stage 2026-10-16) shows that one
GRAPH-scoped query per project returns the same counts as the PR's batching, about 2× faster and with
about 4× less Fuseki CPU (LIMC: 27.9 s vs 53.9 s; checksums identical). The two key changes:

1. Scope the scan to the project's enumerated data graphs and order the terms so that
   `?value knora-base:isDeleted false` is the only constant-object pattern (it becomes a project-sized GPOS scan).
2. Count comments in a separate `UNION` branch driven by `valueHasComment`, not with a per-row `OPTIONAL` (about 35 s on LIMC).

As a result, the Scala batching (listing query, 2000-IRI `VALUES` batches, semaphore, `sumBatches`, per-class
cache keys) and the Scala property classification for counts go away. The drill-down becomes literal-driven inside
the graph (LIMC `Photo` page: 8.7 s to 0.55 s).

Decisions (user, 2026-10-16):

- Keep the `Comment` item type. Use query F2 (comments through the `UNION` branch).
- Classify in SPARQL after `GROUP BY` (less code).
- Add a 120 s timeout tier for the value-count query only. Drill-down and R stay on 40 s.
- Cache TTL 60 minutes (was 10).
- Deliver as new commits on this branch, same PR #4392.

Keep the bug fix: user link values counted, standoff link values excluded, exact literals instead of REGEX.

## Steps (TDD: failing test first in each step; one commit per step)

### 0. Spec file

Copy this plan to `docs/specs/2026-10-15-restrictions-count-queries/03-perf-restrictions-graph-scoped-counts-plan.md`
(frontmatter: `status: approved`, `repositories: []`). Keep a journal next to it.

### 1. Config: count timeout tier and cache TTL

- `TriplestoreService.scala:222`: add `SparqlTimeout.ViewRestrictionsCounts`, with a comment.
- `TriplestoreServiceLive.scala:479`: map it to `triplestoreConfig.viewRestrictionsCountTimeout`.
- `AppConfig.scala:204,290-297`: add the field and the two validations (positive, `<= gravsearch-timeout`).
- `application.conf:221-227`: `view-restrictions-count-timeout = 120 seconds`, env override
  `KNORA_WEBAPI_TRIPLESTORE_VIEW_RESTRICTIONS_COUNT_TIMEOUT`.
- `ViewRestrictionsRowCache.scala:76`: `Ttl = 60.minutes`. Update every "ten minutes" / "10 minutes" Scaladoc
  (`RowCache:22`, `ViewRestrictionsService:97`, `ViewRestrictionsByPropertyService:78`).
- Tests: `AppConfigSpec` (default, validation).

### 2. Repo: graph enumeration and one value-count query

`modules/webapi/src/main/scala/org/knora/webapi/slice/admin/repo/ViewRestrictionsRepo.scala`:

- Add `projectDataGraphsQuery(projectIri)` (handoff 5.3) and `dataGraphs(projectIri): Task[Seq[Iri]]`.
- Replace `valueCountsQuery` by handoff 5.1 (F2) with `VALUES ?g { … }` built by `Fragments.values`, on
  `SparqlTimeout.ViewRestrictionsCounts`. Add `valueCounts(graphs): Task[Seq[ValueCountRow]]`; it returns
  `Seq.empty` without a query when `graphs` is empty.
- `ValueCountRow(classIri, propertyIri, permissions, isFile: Boolean, count, commented)`; `deleted` goes.
- Remove `resourceListingQuery`, `parseResourceIri`, `ValueCountBatchSize`, `ValueCountBatchParallelism`,
  `ValueQueryPermits`, the `Semaphore`, `sumBatches`. The layer becomes `ZLayer.derive`.
- Keep `resourceCountsQuery` (R) unchanged.
- Replace the "graph scoping does NOT apply" block (`:434-440`) and the `valueCountsQuery` Scaladoc
  (`:496-500`) with the rules: enumerated graphs, one driver, no `?x <P> <O>` or `?x a <C>` on an unbound
  variable, no `OPTIONAL`/`NOT EXISTS` in the hot block.
- Tests (`ViewRestrictionsQuerySpec`): golden files `valueCounts` (rewritten) and `projectDataGraphs`
  (new); delete `__resourceListing.txt`, the `sumBatches` suite, `ViewRestrictionsCountQueriesSpec`
  (batching equality). Algebra assertions on `Algebra.optimize(Algebra.compile(q))`: (a) the only quad with a
  constant object on `?value`/`?resource` in the counting sub-selects is the `isDeleted false` driver;
  (b) no `conditional` inside them. Golden files are rewritten through `TEST_UNDECLARED_OUTPUTS_DIR` (see memory
  `golden-test-rewrite-under-bazel`).

### 3. Cache: one entry per project

`ViewRestrictionsRowCache.scala`:

- Three caches keyed by `ProjectIri`: resource rows (R), data graphs, value rows (loader: graphs from the graph
  cache, then `repo.valueCounts`).
- Remove `ValueKey`, `ClassCapacity`, `ProjectClassParallelism`, `valueRowsOfProject`, the per-class
  invalidation loop. `refreshProject` invalidates all three keys and reloads the resource rows.
- Tests (`ViewRestrictionsRowCacheSpec`): single load per key, refresh drops value rows and graphs,
  failure not retained.

### 4. Services and fold

- `ValueCountFold.permissionCounts(rows, itemType)`: no index. `File` = `isFile` rows, `Value` = not `isFile`,
  `Comment` = `commented`, `All` = `count`. Remove the "unknown properties" warning in `fold`.
- `ViewRestrictionsClassification.ProjectOntologyIndex`: keep only `resourceClasses` (guards `/classes`,
  `/values`, `/items`). Remove `valueProperties`, `knownProperties`. Keep `ValueKind` for `/properties`.
- `ViewRestrictionsService`: `/values` = project value rows filtered by class; drill-down value literals from
  the same rows.
- `ViewRestrictionsByPropertyService`: `/property-values` and `/property-items` filter the project value rows
  by property and sum over classes; remove the `index.valueProperties` guards (an uncounted property has no
  rows, so the result is zero / an empty page).
- Tests: `ValueCountFoldSpec`, `ViewRestrictionsClassificationSpec`, both service specs. Rewrite the two-graph
  TriG suite (`ViewRestrictionsServiceSpec:848-906`) as the multi-graph positive test: every enumerated graph
  is counted and summed. Keep the parity test (counts equal drill-down totals per item type and audience).

### 5. Class drill-down queries

`ViewRestrictionsRepo.resourcePageQuery` and `resourceCountForDrillDownQuery`, all three branches, per handoff 5.2:

- Values: `VALUES ?permissions { … }` + `GRAPH ?g { ?value hasPermissions ?permissions . ?resource ?prop ?value }`
  inside an inner sub-select (barrier), then the bound checks (`?resource a <C>`, both `isDeleted false`), the
  `subPropertyOf*` path, the standoff filter, then the item-type constraint.
- Resources: `VALUES ?permissions` + `GRAPH ?g { ?resource hasPermissions ?permissions . ?resource a <C> . }` +
  `FILTER NOT EXISTS { ?resource isDeleted true }` (no tie on document order).
- Both: `UNION` of the two.
- The queries take the graphs from the cache (`findRestrictedObjects`, `countRestrictedResources` get a
  `graphs` parameter).
- `resourceQuery` / `valueQuery` stay unchanged and keep `permissionFilter` (bounded by `VALUES ?resource`).
- Tests: golden files for every branch; invert "no view-restrictions query uses GRAPH scoping" to "every
  drill-down page and count query is GRAPH-scoped"; algebra assertion (c): the inner sub-select exists.
  Restrict the "no constant hasPermissions triple" suite to the row queries.

### 6. Property drill-down queries

`ViewRestrictionsByPropertyRepo.drillDownResourcePageQuery` / `drillDownCountQuery`: same shape as step 5 with
`?resource <prop> ?value`, `hasPermissions` first, barrier kept, no path. `drillDownRowsQuery` unchanged.
Update the trailing comment block (`:386-397`). Tests: `ViewRestrictionsByPropertyQuerySpec` golden files.

### 7. Docs

- `docs/development/dsp-api-fuseki-query-execution.md`: new fact with the `ReorderFixed` weight table; Fact 1
  corollary (fix = barrier *after* the fan-out, or variable object + filter); per-row cost of
  `OPTIONAL`/`NOT EXISTS`; Fact 5: `GRAPH` over the *enumerated* project graphs replaces `attachedToProject`.
- `docs/development/dsp-api-sparql-queries.md` (three sections from this PR) and `REVIEW.md` (three rules):
  rule 1 adds "or `rdf:type` with a bound class"; rule 2 adds the `VALUES`-in-`GRAPH` alternative; rule 3
  becomes "scope the scan to the project's graphs first; batch only if still over the timeout".
- Design doc `01-…-design.md`: follow-up section with the handoff numbers and the C3 correction.
- `ARCH-MAP.md`: check the view-restrictions entry for batching mentions.
- Run `just check`.

## Verification

1. Per step: `bazel test //modules/webapi:test --test_filter='.*ViewRestrictions.*|.*ValueCountFold.*|.*AppConfig.*'`.
   Output must be clean.
2. `just fmt`, `just check`, `just test-unit`.
3. `just test-e2e` filtered to `AdminViewRestrictionsE2ESpec` and `AdminViewRestrictionsByPropertyE2ESpec`;
   expectations stay unchanged.
4. Local join order: set up `explain.sh` (handoff section 2) and run it on every new query. The F2 `Execute`
   block must start with `?value isDeleted false`.
5. Stage check (only after your go-ahead, the queries run on shared stage): `bench.sh` with the rendered
   queries on LIMC; expect md5 `c8e8ecbe`, totals 1,669,898 values and 40,849 file values, drill-down md5
   `e01ea3c8`.

## Risks

- Prod ingress idle timeout is 60 s. A first `/property-values` request on a project above about 60 s of count
  time is cut, even with the 120 s tier. LIMC is at 28 s now.
- A project whose values sit in another graph than their resources is undercounted. Stage shows none; the
  multi-graph test covers the summing.
- After commit, update the PR description (no push without your request). Mention there that the Linear project
  "GRAPH does not replace attachedToProject" bullet needs the same qualification; do not edit Linear.
