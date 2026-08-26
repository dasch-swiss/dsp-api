---
title: "feat: stepped per-class view-restrictions report"
type: feat
date: 2026-08-26
author: "Julien Schneider"
status: reviewed
prd: 01-view-restrictions-stepped-fetch-PRD.md
linear: DEV-6778
branch: feat/dev-6778-view-restrictions-stepped-fetch
repositories:
  - name: dsp-api
    path: /Users/julien/WebstormProjects/dsp-api
  - name: dsp-das
    path: /Users/julien/WebstormProjects/dsp-das
---

# feat: stepped per-class view-restrictions report

## Overview

Replace the single all-or-nothing `/view-restrictions/summary` request with two endpoints
that the frontend drives in sequence: one query that paints the whole table, then one
query per resource class that fills in value-level counts with visible progress.

The enabling change is a query rewrite, not an API change: grouping by
`knora-base:hasPermissions` instead of pre-filtering to a permission subset returns every
audience's numbers in one pass. That collapses 12 queries per class to 2 and lets three
existing subsystems be deleted outright.

## Problem Statement / Motivation

`GET /admin/projects/iri/{iri}/view-restrictions/summary` returns HTTP 500 on large
projects. One request must finish six `(audience × state)` grouped-count fan-outs
(`ViewRestrictionsService.scala:200-209`, capped at `MaxConcurrentCountQueries = 4`,
`:378`), each fanning out over class chunks (`ViewRestrictionsRepo.scala:471`, capped at
`:478`), plus `projectClasses`, `distinctPermissions` and `totalResourcesByClass` — all
inside `view-restrictions-timeout = 60 seconds`.

One slow class produces an empty screen. The admin gets no partial data, no progress
signal, and no indication of which class was the problem.

## Proposed Solution

### The query rewrite

`distinctPermissions` already establishes the invariant this rests on: for the three
synthetic audiences, visibility is a pure function of the object's `hasPermissions`
literal, because `entityProject` is constant, none of the three users is an admin, and none
is ever the creator. `ViewRestrictionsServiceSpec` pins that equivalence and must keep
pinning it.

`resourceCountQuery` (`:820`) and `resourceTotalByClassQuery` (`:849`) are already the same
scan, differing only by `.filter(permissionsIn(permissions, statePermissions))`. Drop the
filter, add the literal to the grouping key, and one query answers everything:

```scala
private[repo] def resourceCountsByClassAndPermissionQuery(
  projectIri: ProjectIri,
  classes: ProjectClasses,
): SelectQuery = {
  val (resource, resClass)   = (variable("resource"), variable("resClass"))
  val (creator, permissions) = (variable("creator"), variable("permissions"))
  val cnt                    = variable("cnt")

  Queries
    .SELECT(resClass, permissions, Expressions.count(resource).distinct().as(cnt))
    .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
    .where(
      GraphPatterns.and(
        // bindCreator = false: the creator cannot change the decision for a synthetic
        // audience, so `attachedToUser ?creator` is an unconstrained join that only
        // multiplies intermediate rows. The current resourceCountQuery (:834) still binds
        // it; the same reasoning valueCore's scaladoc gives for its own probes applies here.
        resourceCore(projectIri, resource, resClass, creator, permissions, classes, bindCreator = false),
      ),
    )
    .groupBy(resClass, permissions)
}
```

The service then classifies the returned literals — a handful, since they come from
project-level default-permission templates rather than per-object authoring — and derives
every reported number from one row set:

```scala
/** Fold (literal, count) rows into per-audience counts, and the class population. */
private def foldRows(rows: Seq[PermissionCountRow], projectIri: ProjectIri): (AudienceCounts, Int) =
  rows.foldLeft((AudienceCounts.zero, 0)) { case ((counts, total), row) =>
    val next = Audience.ordered.foldLeft(counts) { (acc, audience) =>
      visibilityOf(
        PermissionUtilADM.getUserPermissionADM(
          entityCreator = syntheticCreatorPlaceholder,
          entityProject = projectIri.value,
          entityPermissionLiteral = row.permissions,
          requestingUser = audienceUser(audience, projectIri),
        ),
      ) match {
        case Visibility.Hidden         => add(acc, audience, hidden = row.count)
        case Visibility.RestrictedView => add(acc, audience, restrictedView = row.count)
        case Visibility.Visible        => acc // visible is not a restriction — but still counts toward total
      }
    }
    // Every literal contributes to the population, including the fully-visible ones.
    // This is what replaces totalResourcesByClass.
    (next, total + row.count)
  }
```

### The two endpoints

```
GET /admin/projects/iri/{projectIri}/view-restrictions/classes
GET /admin/projects/iri/{projectIri}/view-restrictions/values?resourceClass={iri}&itemType={t}
```

Step 1 is one query and takes no filter. Step 2 is one query scoped to a single class. The
frontend runs step 2 at concurrency 4 with per-class error isolation.

`/items` keeps its shape; `groupBy` is dropped and `group` becomes `resourceClass`.

## Alternative Approaches Considered

**Keep the `(audience, state)` fan-out, only add the stepped routes.** Ships the progress
UI without touching query logic, so lower risk per PR. Rejected: it leaves 12 queries per
class request, which is *more* round-trips per request than today's 3-class chunks, so the
first release would keep the exact timeout exposure this work exists to remove.

**Keep chunking as an internal detail of the per-class endpoint.** Protects the
one-huge-class case. Rejected by explicit decision: with the query collapse the per-class
work is small enough that chunking's complexity is no longer justified, and a failing class
now degrades to one error row instead of a 500.

**Return all item types in one per-class response** so filtering is pure frontend work.
Rejected: ~5× the query work per request, which worsens the risk the no-chunking decision
already accepted.

## Technical Considerations

### SPARQL hazards from `CONVENTIONS.md`

Three project conventions bear directly on these queries.

**The unbound property path is a documented hazard, and it is the top risk here.**
`valueCore` (`:667-669`) contains `?prop rdfs:subPropertyOf* knora-base:hasValue` with
nothing binding `?prop`. `CONVENTIONS.md` states: "A `*`/`+` path whose variables are not
bound by *preceding* patterns cross-joins everything matched so far against the whole
closure (audit DEV-6803: >60s vs 0.4s)." That is exactly this shape. The PRD accepts it and
declines to port the `VALUES ?prop` binding from the abandoned branch — but the same
convention also warns "Don't inline large closures as `VALUES` either (measured: 2s →
>60s)", which is why that binding was non-trivial to get right. **Measure step 2 before
assuming it is acceptable** (PRD OQ-2).

**`GRAPH` scoping is a measured win the PRD did not account for, applied narrowly.**
`CONVENTIONS.md`: "`GRAPH <projectDataGraph>` **replaces** `attachedToProject` — drop the
join (DEV-6827: 5.4×)." Both `resourceCore` (`:597`) and `valueCore` (`:663`) join on
`attachedToProject`, and it is the cheapest available mitigation for the property-path risk
above.

But those two skeletons are **shared**: `resourceCore` has six call sites (`:768`, `:834`,
`:859`, `:987`, `:997`, `:1028`) and `valueCore` four (`:799`, `:887`, `:972`, `:1059`).
Only two of the ten are the queries being replaced — the rest serve `findRestrictedObjects`,
`countRestrictedResources` and `distinctPermissions`, i.e. the `/items` drill-down this
rework otherwise leaves alone. Rewriting them outright would silently change the drill-down.

**Decision: graph scoping applies to the two new queries only.** It is introduced as an
opt-in `graph: Option[InternalIri] = None` parameter on both skeletons, defaulting to the
current `attachedToProject` join, so all eight existing call sites keep their exact query
shape and no regression surface opens. An opt-in default is preferred over duplicating the
skeletons, which would let the two copies drift.

**The graph IRI has to be threaded in.** `ProjectService.projectDataNamedGraphV2`
(`ProjectService.scala:106`/`:115`) derives the graph from a project's shortcode and
shortname, so it needs a `Project`/`KnoraProject` — while the repo only ever receives a
`ProjectIri`. The service resolves it via `KnoraProjectService.findById`
(`KnoraProjectService.scala:36`) and passes the resulting `InternalIri` to the repo. This
adds a `KnoraProjectService` dependency to `ViewRestrictionsService`; wire it in
`AdminDomainModule` (`:69`).

**`COUNT(DISTINCT ?resource)` may be a no-op.** `CONVENTIONS.md`: "Drop `DISTINCT` that
provably can't dedup (always a no-op over `GROUP BY`; verify by byte-comparing outputs)."
With the most-specific-class filter in place each resource yields one `?resClass` binding,
so `DISTINCT` is likely redundant in the resource query. Verify by byte-comparing outputs
before dropping — do not assume.

### Invariants that must survive

- **One class per resource.** `classes.resClassPatterns(...)` → `mostSpecificClass`
  (`:618`), emitted only when `needsMostSpecificClassFilter`. Without it a multi-typed
  resource is counted once per class in its hierarchy and `totalResources` overstates.
  This is load-bearing for grouping by `?resClass ?permissions`.
- **The literal-purity equivalence.** `ViewRestrictionsServiceSpec` pins that classifying a
  bare literal against `syntheticCreatorPlaceholder` equals classifying a real object. The
  whole design depends on it; the spec must be kept, not rewritten away with the fan-out.
- **The counted universe.** `hasPermissions` is a required pattern in both `resourceCore`
  (`:602`) and `valueCore` (`:658`), so objects without a permission literal are outside
  the report. Unchanged from today, but now visible because `totalResources` is derived
  from the same rows.

### API layer conventions

Per `CONVENTIONS.md` the three-tier split is mandatory: `ViewRestrictionsEndpoints` holds
Tapir definitions and DTOs, `ViewRestrictionsServerEndpoints` wires them,
`ViewRestrictionsRestService` does auth-then-delegate only. Register both new endpoints in
`AdminApiServerEndpoints`.

**`UnitCounts` does not survive.** It pairs `resources` with `items`, but step 1 carries
only the resource unit and step 2 only the value unit, so either response would have to
return it half-empty — reintroducing exactly the unit confusion the type was created to
prevent. Both new DTOs carry `RestrictionCounts` (hidden / restrictedView) per audience
directly, and the unit is implied by which endpoint answered. `AudienceCounts` is
re-parameterised over `RestrictionCounts` instead of `UnitCounts`.

For `resourceClass`, follow the existing `getItems` pattern — validate at the boundary and
fail `BadRequestException` (REQ-2.7). A typed `ResourceClassIri` exists
(`KnoraIris.scala:72`) but is SmartIri-backed, so using it requires an `IriConverter` in
the RestService; that is the v3 convention, not the admin one. Keep the validated-string
approach unless the conversion is wanted for its own sake.

Do **not** add variants to `BaseEndpoints.errorOutputs` — it is attached to every endpoint
in the API. Declare error variants on the endpoint with `errorOutVariantsPrepend`.

### Frontend considerations

`ViewRestrictionsPageService` (104 lines) is a clean `BehaviorSubject` + `combineLatest` +
`switchMap` pipeline. The stepped flow needs a different shape: step 1 as a single stream,
then a bounded-concurrency fan-out (`mergeMap` with concurrency 4) whose per-class results
accumulate into a map, with `catchError` per inner request so one failure cannot terminate
the outer stream — the existing service already uses inner-`catchError` for exactly this
reason and that instinct carries over.

`ITEM_TYPE_SLUG` in `view-restrictions.component.ts:41` is a `Record<ItemType, string>`
over all five values. Narrowing the enum makes TypeScript flag the stale `Resource` key,
which is the desired failure mode.

**The OpenAPI client cannot be regenerated until the API change is reachable.**
`npm run update-openapi` curls `https://api.dev.dasch.swiss/api/docs/docs.yaml`. Either
point it at a locally running API or land and deploy the dsp-api side first. `openapiDir`
in the dsp-api justfile (`:1`) is a dead variable — `docs/03-endpoints/generated-openapi/`
is empty and no recipe references it, so there is no working local spec-generation target
to lean on today.

Four locale files carry these keys: `en.json`, `de.json`, `fr.json`, `it.json`.

## Implementation Phases

Phases 1-5 are dsp-api; phases 6-8 are dsp-das. Repo headings below carry the tasks so
cross-repo tooling can attribute them.

### dsp-api

#### Phase 1: grouped-count queries

- [ ] Add a `PermissionCountRow` row type (group key, permission literal, count) to `ViewRestrictionsRepo`
- [ ] Add `resourceCountsByClassAndPermissionQuery`, grouped by `?resClass ?permissions`, with `bindCreator = false`
- [ ] Add `valueCountsByPermissionQuery(projectIri, resourceClass, itemType)`, grouped by `?permissions`
- [ ] Add an opt-in `graph: Option[InternalIri] = None` parameter to `resourceCore`, defaulting to the current `attachedToProject` join
- [ ] Add the same opt-in `graph` parameter to `valueCore`
- [ ] Pass `Some(graph)` from the two new queries only, leaving all eight existing call sites unchanged
- [ ] Resolve the project's data graph in `ViewRestrictionsService` via `KnoraProjectService.findById` + `ProjectService.projectDataNamedGraphV2`
- [ ] Add the `KnoraProjectService` dependency to `ViewRestrictionsService` and wire it in `AdminDomainModule`
- [ ] Verify whether `COUNT(DISTINCT ?resource)` can drop `DISTINCT`, by byte-comparing query outputs
- [ ] Add repo methods `resourceCountsByClass` and `valueCountsForClass` returning `Seq[PermissionCountRow]`
- [ ] Confirm `mostSpecificClass` / `needsMostSpecificClassFilter` still applies to both new queries
- [ ] **Gate before Phase 5:** byte-compare the new queries' counts against the current implementation on a real project, while the old queries still exist

#### Phase 2: service layer

- [ ] Add `classSummaries(projectIri)`: classify literals, fold into `AudienceCounts` plus derived `totalResources`
- [ ] Add `valueCounts(projectIri, resourceClass, itemType)`: classify literals, fold into `AudienceCounts`
- [ ] Keep `audienceUser`, `visibilityOf` and `syntheticCreatorPlaceholder` unchanged
- [ ] Remove the `(audience, state)` fan-out and `MaxConcurrentCountQueries`
- [ ] Remove `orderKey` and the server-side `totals` computation

#### Phase 3: API layer

- [ ] Define `getViewRestrictionsClasses` in `ViewRestrictionsEndpoints` with its response DTO
- [ ] Define `getViewRestrictionsValues` in `ViewRestrictionsEndpoints` with its response DTO
- [ ] Narrow `ItemType` to `All | File | Value | Comment`
- [ ] Delete the `GroupBy` enum
- [ ] Change `/items` to take `resourceClass` and drop `groupBy`
- [ ] Add `getClasses` and `getValues` to `ViewRestrictionsRestService` with the project-admin-or-sysadmin check
- [ ] Validate `resourceClass` at the boundary, failing `BadRequestException` (REQ-2.7)
- [ ] Wire both endpoints in `ViewRestrictionsServerEndpoints`
- [ ] Register both in `AdminApiServerEndpoints`
- [ ] Delete the `/summary` endpoint, its handler and its DTO

#### Phase 4: tests

Runs **before** the deletions in Phase 5, so the old queries are still available as the
comparison baseline.

- [ ] Rewrite `ViewRestrictionsQuerySpec` for the two grouped queries; delete chunk-summation tests
- [ ] Add a query test pinning the graph-scoped shape of the two new queries
- [ ] Add a query test pinning that the eight pre-existing call sites still emit the `attachedToProject` join
- [ ] Keep the literal-equivalence pin in `ViewRestrictionsServiceSpec`
- [ ] Replace the five `CountUnit` assertions at `ViewRestrictionsServiceSpec:1161-1196` with equivalents over `PermissionCountRow`
- [ ] Add a service test for `totalResources` derived as the sum over all literals, including visible ones
- [ ] Add a service test covering a multi-typed resource counted exactly once
- [ ] Rewrite `AdminViewRestrictionsE2ESpec` for the two new routes
- [ ] Add an E2E test asserting 403 for a non-admin on each new route
- [ ] Add an E2E test asserting 400 for a malformed `resourceClass`
- [ ] Add an E2E test asserting an empty class list on a project with no resource classes
- [ ] Confirm the existing `/items` E2E coverage still passes unchanged

#### Phase 5: deletions and cleanup

- [ ] Delete `distinctPermissions` and `runDistinctPermissions`
- [ ] Delete `totalResourcesByClass` and `resourceTotalByClassQuery`
- [ ] Delete `resourceCountQuery` and `valueCountQuery`
- [ ] Delete `countByGroup`, `runCountByGroup` and `mergeCounts`
- [ ] Delete `ClassChunkSize`, `MaxConcurrentChunkQueries` and `ProjectClasses.chunked` (single caller, `:203`)
- [ ] Delete `GroupCountRow` and `CountUnit`, now orphaned (last uses: `ViewRestrictionsService.scala:18-19`, `:256`, `:259`)
- [ ] Delete `UnitCounts` and re-parameterise `AudienceCounts` over `RestrictionCounts`
- [ ] Review whether `view-restrictions-timeout = 60 seconds` should return to the default `query-timeout` (PRD OQ-1)

### dsp-das

#### Phase 6: client and state

- [ ] Regenerate the OpenAPI client against the new spec
- [ ] Rewrite `ViewRestrictionsPageService`: step-1 stream, then bounded fan-out at concurrency 4
- [ ] Add per-class result state distinguishing pending, loaded and failed
- [ ] Add a per-`(class, itemType)` cache so revisiting a filter does not refetch
- [ ] Add a per-class retry action that refetches only that class
- [ ] Expose gathered-count and total for the progress indicator
- [ ] Remove `groupBy$` and the `GroupBy` import
- [ ] Change `loadItems` to pass `resourceClass` and drop `groupBy`

#### Phase 7: component and UI

- [ ] Render all class rows from step 1, including classes with no restrictions
- [ ] Add the two-step progress display ("fetching resource classes", then "gathering k/N")
- [ ] Add the per-row error state with its retry control
- [ ] Show value totals as a lower bound, flagged incomplete, when any class failed
- [ ] Keep resource totals unflagged, since they come wholly from step 1
- [ ] Add a page-level error with retry for a step-1 failure, issuing no step-2 requests
- [ ] Add an explicit empty state for a project with no resource classes
- [ ] Remove the group-by toggle and its template branch
- [ ] Remove the stale `Resource` key from `ITEM_TYPE_SLUG`
- [ ] Update `view-restrictions.component.spec.ts` for the stepped flow
- [ ] Add a service test asserting no more than 4 step-2 requests are ever in flight
- [ ] Update `view-restrictions.component.stories.ts` with progress, partial-failure and empty states

#### Phase 8: i18n

- [ ] Add progress, retry, incomplete-totals and empty-project keys to `en.json`
- [ ] Mirror the new keys in `de.json`
- [ ] Mirror the new keys in `fr.json`
- [ ] Mirror the new keys in `it.json`
- [ ] Remove the obsolete `groupBy`, `byClass`, `byProperty`, `groupByAria` and `itemType.resource` keys from all four locales

## Acceptance Criteria

- [ ] REQ-1.1 to REQ-1.7 — `/classes` returns every class with label, ontology, `totalResources` and resource-level counts, takes no filter, requires admin, handles the empty project
- [ ] REQ-2.1 to REQ-2.7 — `/values` answers one class per request with one grouped query, requires admin, rejects a malformed `resourceClass`; the frontend fans out at concurrency 4 with progress
- [ ] REQ-3.1 to REQ-3.6 — per-class failure isolation with retry; step-1 failure shows a page error and issues no step-2 requests
- [ ] REQ-4.1 to REQ-4.4 — `itemType` of `All | File | Value | Comment` on `/values` only, with per-`(class, itemType)` caching
- [ ] REQ-5.1 to REQ-5.3 — `/items` paginated, keyed by `resourceClass`, no `groupBy`, ordered by label then IRI
- [ ] REQ-6.1 to REQ-6.7 — no `GroupBy`, no chunking, no `distinctPermissions`, no `/summary`, no debug scaffolding, no property-set discovery, most-specific-class filter retained
- [ ] `just check` and `bazel test //modules/webapi:test` pass
- [ ] `bazel test //modules/test-e2e:test` passes
- [ ] The report completes against the dev DB with no HTTP 500

## Dependencies & Risks

The frontend work depends on the API change being reachable by
`npm run update-openapi`, which pulls from `api.dev.dasch.swiss`. Either point it at a
local API or land dsp-api first. Phases 6-8 are otherwise blocked on Phases 1-5.

PRD OQ-2 — the largest class on LHTT by value count — should be answered before Phase 1.
A bad answer reopens the chunking decision, and finding that out after Phase 5 wastes the
whole backend rewrite.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Unbound `subPropertyOf*` path makes step 2 time out (DEV-6803 shape: >60s vs 0.4s) | H | H | Answer OQ-2 first; `GRAPH` scoping in Phase 1; reopen the `VALUES ?prop` decision if measurement demands it |
| One class too large for a single query | M | M | Accepted by decision — surfaces as an error row (US-3), not a 500 |
| Graph scoping changes the counted universe | M | H | Opt-in `graph` parameter defaults to current behaviour, so only the two new queries change; Phase 4 pins both shapes and byte-compares counts while the old queries still exist (Phase 1 gate) |
| Graph scoping regresses the `/items` drill-down | L | H | Shared skeletons keep their default behaviour; Phase 4 pins the `attachedToProject` join on the eight untouched call sites and re-runs existing `/items` E2E coverage |
| Dropping `DISTINCT` silently undercounts | M | H | Byte-compare outputs before dropping; keep `DISTINCT` if not provably redundant |
| Deleting the fan-out also deletes the literal-purity spec coverage | M | H | Phase 5 keeps the `ViewRestrictionsServiceSpec` equivalence pin as an explicit deliverable |
| Deploy skew — `/summary` removed while an old frontend is live | M | M | Release both repos together, or the frontend first against a transitional API (PRD Constraints) |
| Multi-typed resources double-counted under the new grouping | L | H | Phase 1 confirms `mostSpecificClass` applies; Phase 5 adds a multi-typed test |

## Success Metrics

- Queries per full report drop from `6 × chunks × 2 units + 3 fixed` to `1 + N(classes)`.
- Step 1 latency measured against the unchunked single-query baseline (~8.1s on the
  46k-resource / 265k-value project cited at `ViewRestrictionsRepo.scala:182`).
- No HTTP 500 on the largest dev-DB project.
- One failing class leaves every other row populated and readable.
- `ViewRestrictionsRepo.scala` no longer contains `countByGroup`, `distinctPermissions` or any chunking symbol.

## References

- Parent PRD: `01-view-restrictions-stepped-fetch-PRD.md`
- Query skeletons to rewrite: `modules/webapi/src/main/scala/org/knora/webapi/slice/admin/repo/ViewRestrictionsRepo.scala:820` (`resourceCountQuery`), `:849` (`resourceTotalByClassQuery`)
- Patterns to preserve: `ViewRestrictionsRepo.scala:602` (`resourceCore` permissions), `:618` (`mostSpecificClass`), `:658` (`valueCore` permissions), `:667-669` (property path)
- Chunking to delete: `ViewRestrictionsRepo.scala:471` (`ClassChunkSize`), `:478` (`MaxConcurrentChunkQueries`)
- Fan-out to delete: `ViewRestrictionsService.scala:200-209`, `:378` (`MaxConcurrentCountQueries`)
- Chunking rationale and the 8.1s measurement: `ViewRestrictionsRepo.scala:182`
- Typed class IRI, if wanted: `modules/webapi/src/main/scala/org/knora/webapi/slice/common/KnoraIris.scala:72`
- SPARQL conventions (property-path anchoring, `GRAPH` scoping, redundant `DISTINCT`): `CONVENTIONS.md` § SPARQL, and `docs/development/dsp-api-sparql-queries.md`
- API three-tier split and error-variant rules: `CONVENTIONS.md` § API layer, § Error handling
- Frontend state to rewrite: `dsp-das/libs/vre/pages/project/project/src/lib/project-settings/view-restrictions/view-restrictions-page.service.ts`
- Frontend component and stale enum map: `.../view-restrictions/view-restrictions.component.ts:41`
- OpenAPI regeneration: `dsp-das/package.json:20-22`
