---
title: View restrictions report — stepped per-class fetch
date: 2026-08-26
status: reviewed
repositories:
  - dsp-api
  - dsp-das
linear: DEV-6778
branch: feat/dev-6778-view-restrictions-stepped-fetch
---

# View restrictions report — stepped per-class fetch

## Context

The view-restrictions report (DEV-6778) returns HTTP 500 on large projects. A single
`GET /admin/projects/iri/{iri}/view-restrictions/summary` request does all the work:

- six `(audience × state)` grouped-count fan-outs (`ViewRestrictionsService.scala:200-209`),
  capped at `MaxConcurrentCountQueries = 4` (`:378`);
- each fanning out again over class chunks — `ClassChunkSize = 3`
  (`ViewRestrictionsRepo.scala:471`), capped at `MaxConcurrentChunkQueries = 2` (`:478`);
- plus three fixed queries: `projectClasses`, `distinctPermissions`,
  `totalResourcesByClass`.

All of it must finish inside `view-restrictions-timeout = 60 seconds`. The report is
all-or-nothing: one slow class means an empty screen and a 500.

### Baseline warning: unmerged work on the old branch

This PRD baselines against **`origin/main`**, which is what
`feat/dev-6778-view-restrictions-stepped-fetch` was branched from. The earlier branch
`fix/dev-6778-view-restrictions-followup` carries roughly +365 unmerged lines on
`ViewRestrictionsRepo.scala` that are **not** on main, including:

- a second chunking axis over value properties (`projectProperties`, `PropertyChunkSize`,
  `ProjectProperties.propPatterns`);
- a `VALUES ?prop { … }` binding that constrains the value query's `subPropertyOf*`
  traversal, plus the `OntologyRepo.findAllValuePropertyIris` support it needs
  (+17/+20 lines on `OntologyRepo.scala` / `OntologyRepoLive.scala`);
- project-scoping of that property set.

**Decision: that branch is abandoned.** Nothing is ported from it. This design supersedes
it, and the branch is retained only as a historical record — it must not be merged.

The one consequence to carry forward deliberately: the `VALUES ?prop` binding is *not*
adopted, so the value query keeps its unbound `subPropertyOf*` traversal (see below). That
is an accepted risk, recorded in Constraints.

The frontend already exists at
`dsp-das/libs/vre/pages/project/project/src/lib/project-settings/view-restrictions/`
(`view-restrictions.component.ts`, `view-restrictions-page.service.ts`), so this is a
rework on both sides rather than a greenfield feature.

## Goals

1. No timeout-driven HTTP 500 in normal operation.
2. Legible progressive loading — the admin sees what is happening and how much remains.
3. Partial failure contained to a single table row.
4. Substantially less backend machinery to reason about.

## Core design

### The enabling change: `GROUP BY ?permissions`

`distinctPermissions` already establishes that, for the three synthetic audiences, an
object's visibility is a pure function of its `knora-base:hasPermissions` literal (the
creator only matters when it equals the requesting user, which a synthetic audience user
never is). `ViewRestrictionsServiceSpec` pins that equivalence.

Therefore a single query grouped by the permission literal returns every audience's
numbers at once:

```sparql
SELECT ?resClass ?permissions (COUNT(DISTINCT ?resource) AS ?cnt)
WHERE { ... }                 # the existing resourceCore skeleton, unchanged
GROUP BY ?resClass ?permissions
```

`ViewRestrictionsRepo.resourceCountQuery` (`:820`) and `resourceTotalByClassQuery`
(`:849`) are already the same scan differing only by a `.filter(permissionsIn(...))`,
so this costs no more than the population query the report runs today.

Consequences:

- 12 queries per class (6 audience×state pairs × 2 units) collapse to 2.
- `distinctPermissions` disappears — the grouped query's keys *are* the distinct literals.
- `totalResourcesByClass` disappears — a class's population is the sum of its
  per-literal counts.
- The `(audience, state)` fan-out and both concurrency caps disappear.

### The value query's unbound property traversal

`valueCore` (`ViewRestrictionsRepo.scala:667-669`) contains
`?prop rdfs:subPropertyOf* knora-base:hasValue`, and on main nothing constrains `?prop`.
That traversal is therefore evaluated against the project's whole value population on
every value count — the single most likely reason a value query is slow, and it survives
this rework untouched unless addressed.

The abandoned followup branch had addressed exactly this with a `VALUES ?prop { … }` clause
fed from the ontology cache. By decision it is **not** ported, so the traversal stays
unbound. This is the largest known unmitigated cost in step 2 and the most likely reason a
class fails (US-3). It is accepted knowingly, not overlooked — if measurement later shows
step 2 failing on real projects, constraining `?prop` is the first thing to revisit, and it
composes with this design without reintroducing chunking.

### Invariant preserved: one class per resource

Grouping by `?resClass ?permissions` is only sound because each resource yields exactly one
`?resClass` binding. That is enforced by `classes.resClassPatterns(...)` →
`mostSpecificClass` (`ViewRestrictionsRepo.scala:618`), emitted only when
`needsMostSpecificClassFilter` holds. Both queries keep it. Without it a multi-typed
resource is counted once per class in its hierarchy, and `totalResources` overstates.

### Step 1 — `GET /admin/projects/iri/{projectIri}/view-restrictions/classes`

One SPARQL query. Returns, per resource class in the project:

- class IRI, label, ontology name;
- `totalResources` (sum of its per-literal counts);
- resource-level `hidden` / `restrictedView` counts for all three audiences.

Every class is reported, including those with no restrictions. The table paints fully
from this one response. Takes no item-type filter.

### Step 2 — `GET /admin/projects/iri/{projectIri}/view-restrictions/values?resourceClass=X&itemType=Y`

One SPARQL query per call, grouped by permission literal, scoped to a single resource
class. Returns value-level `hidden` / `restrictedView` counts for all three audiences.

The frontend runs a pool of at most 4 concurrent requests and displays
`gathering k/N`.

### Drill-down — `GET .../view-restrictions/items`

Kept as-is except: `groupBy` removed, `group` renamed to `resourceClass`.

### Why the split is asymmetric

The resource-level scan is cheap — the same skeleton as the population count already run
today, measured at ~8.1s unchunked on a 46k-resource project. The value-level scan is the
expensive one (265k values on that same project; LHTT is 9.4M triples). Putting the
per-class stepping only where the cost actually is keeps the request count low while
bounding the dangerous query.

## Repository impact

### dsp-api

| File | Change |
| --- | --- |
| `slice/api/admin/ViewRestrictionsEndpoints.scala` | Replace `/summary` with `/classes` + `/values`; delete `GroupBy`; narrow `ItemType` to `All\|File\|Value\|Comment`; new response models |
| `slice/api/admin/ViewRestrictionsServerEndpoints.scala` | Rewire to the two new endpoints |
| `slice/api/admin/service/ViewRestrictionsRestService.scala` | Two methods instead of `getSummary`; `resourceClass` validation |
| `slice/admin/domain/service/ViewRestrictionsService.scala` | Classify literals returned by the grouped query; drop the fan-out, the caps, and the ordering |
| `slice/admin/repo/ViewRestrictionsRepo.scala` | Two grouped queries; delete class chunking (`ClassChunkSize:471`, `MaxConcurrentChunkQueries:478`), `distinctPermissions`, `totalResourcesByClass`. **Keep** `resClassPatterns`/`mostSpecificClass` (`:618`) and the unbound `?prop` traversal (`:667-669`) |
| `config/AppConfig.scala` + `application.conf` | Revisit whether `view-restrictions-timeout = 60s` is still warranted |
| `webapi/src/test/.../ViewRestrictionsQuerySpec.scala` | Rewrite for the grouped queries; chunk-summation tests obsolete |
| `webapi/src/test/.../ViewRestrictionsServiceSpec.scala` | Keep the literal-equivalence pin; rewrite classification tests |
| `test-e2e/.../AdminViewRestrictionsE2ESpec.scala` | New route shapes |

### dsp-das

| File | Change |
| --- | --- |
| `.../view-restrictions/view-restrictions-page.service.ts` | Stepped fetch orchestration, concurrency pool of 4, per-`(class, itemType)` cache, per-class error state |
| `.../view-restrictions/view-restrictions.component.{ts,html}` | Progress display, per-row error + retry, remove property-mode toggle and sorting, sum totals client-side |
| `libs/vre/3rd-party-services/open-api/src/generated/**` | Regenerate the OpenAPI client |
| `.../view-restrictions/*.spec.ts`, `*.stories.ts` | Update for the new flow |

## User stories and acceptance criteria

### US-1

As a project admin, I want the table to appear with class names and resource counts
immediately, so I can start reading before the whole project is scanned.

- **REQ-1.1 (Event-driven):** When a project admin opens the view-restrictions page, the
  API shall return in a single request every resource class in the project with its IRI,
  display label, ontology name and total resource count. The display label is derived from
  the IRI local name, unchanged from today's `localName(iri)`.
- **REQ-1.2 (Ubiquitous):** The classes endpoint shall report resource-level `hidden` and
  `restrictedView` counts for all three audiences.
- **REQ-1.3 (Ubiquitous):** The classes endpoint shall derive each class's total resource
  count as the sum of its per-permission-literal counts, issuing no separate population
  query. This counts the same universe as today's `totalResourcesByClass` — non-deleted,
  project-owned resources that carry a `knora-base:hasPermissions` triple, which
  `resourceCore` (`ViewRestrictionsRepo.scala:602`) binds as a required pattern, as does
`valueCore` (`:658`) for values. A resource
  with no permission literal is outside the report entirely, as it is today.
- **REQ-1.4 (Event-driven):** When the classes response arrives, the frontend shall render
  one table row per class, including classes with no restrictions.
- **REQ-1.5 (Ubiquitous):** The classes endpoint shall accept no item-type filter.
- **REQ-1.6 (Ubiquitous):** The classes endpoint shall require the requesting user to be
  project admin on the target project or system admin.
- **REQ-1.7 (Unwanted-behaviour):** If the project has no resource classes, then the
  classes endpoint shall return an empty list and the frontend shall show an explicit
  "no resource classes in this project" state rather than a perpetual loading indicator.

### US-2

As a project admin, I want visible progress while value counts are gathered, so I know
the report is working and how much remains.

- **REQ-2.1 (Event-driven):** When the class list has rendered, the frontend shall request
  value-level counts per class, at most 4 requests concurrently.
- **REQ-2.2 (State-driven):** While value counts are being gathered, the frontend shall
  display the number of classes completed out of the total.
- **REQ-2.3 (Event-driven):** When a class's value counts arrive, the frontend shall
  populate that row without waiting for other classes.
- **REQ-2.4 (Ubiquitous):** The values endpoint shall answer for exactly one resource class
  per request.
- **REQ-2.5 (Ubiquitous):** The values endpoint shall compute its counts with a single
  SPARQL query grouped by permission literal.
- **REQ-2.6 (Ubiquitous):** The values endpoint shall require the requesting user to be
  project admin on the target project or system admin.
- **REQ-2.7 (Unwanted-behaviour):** If the `resourceClass` parameter is absent or is not a
  well-formed IRI, then the values endpoint shall respond 400 rather than reaching the
  SPARQL builder.

### US-3

As a project admin, I want an uncomputable class marked visibly rather than breaking the
report.

- **REQ-3.1 (Unwanted-behaviour):** If a value-counts request fails, then the frontend
  shall mark that class's row as not computed and continue with the remaining classes.
- **REQ-3.2 (Unwanted-behaviour):** If one or more classes failed, then the frontend shall
  present the aggregate *value* totals as a lower bound and indicate that the data is
  incomplete. Resource-level totals come wholly from step 1 and shall not be marked
  incomplete on a step-2 failure.
- **REQ-3.3 (Unwanted-behaviour):** If a value-counts request fails, then the frontend
  shall offer a retry for that class alone.
- **REQ-3.4 (Unwanted-behaviour):** If a query exceeds the triplestore timeout, then the
  API shall respond with an error naming the requested resource class.
- **REQ-3.5 (Unwanted-behaviour):** If the step-1 classes request fails, then the frontend
  shall show a page-level error with a retry action and shall issue no step-2 requests.
- **REQ-3.6 (Unwanted-behaviour):** If the step-1 classes request fails, then the frontend
  shall render no partial table, since without the class list no row can be identified.

### US-4

As a project admin, I want to filter to a single item type.

- **REQ-4.1 (Ubiquitous):** The values endpoint shall accept an `itemType` of `All`,
  `File`, `Value` or `Comment`, where `All` means all *value* types. This is narrower than
  today's `ItemType.All`, which also spanned resources; resource-level counts now always
  arrive from step 1 and are never filtered.
- **REQ-4.2 (Event-driven):** When the admin selects an item type, the frontend shall
  re-run the value-counts requests for that item type.
- **REQ-4.3 (Event-driven):** When the admin returns to a previously selected item type,
  the frontend shall serve cached counts for the `(class, itemType)` pairs it already
  holds.
- **REQ-4.4 (State-driven):** While a filter change is in progress, the frontend shall
  display the gathering progress for the new filter.

### US-5

As a project admin, I want to drill into a class's affected resources.

- **REQ-5.1 (Event-driven):** When the admin selects a class row, the API shall return a
  paginated list of that class's affected resources.
- **REQ-5.2 (Ubiquitous):** The items endpoint shall identify its target class by a
  `resourceClass` parameter and shall not accept a `groupBy` parameter.
- **REQ-5.3 (Ubiquitous):** The items endpoint shall order its results by label then IRI.

### US-6

As a maintainer, I want the removed machinery actually gone, so the report is cheap to
reason about.

- **REQ-6.1 (Ubiquitous):** The view-restrictions repo, service and endpoints shall contain
  no `GroupBy` enum and no property-mode grouping.
- **REQ-6.2 (Ubiquitous):** The view-restrictions repo shall contain no class or property
  chunking.
- **REQ-6.6 (Ubiquitous):** The view-restrictions repo shall introduce no property-set
  discovery, neither by SPARQL nor from the ontology cache.
- **REQ-6.7 (Ubiquitous):** The view-restrictions repo shall retain the most-specific-class
  filter so that each resource yields exactly one `?resClass` binding.
- **REQ-6.3 (Ubiquitous):** The view-restrictions service shall issue no
  `distinctPermissions` query.
- **REQ-6.4 (Ubiquitous):** The `/summary` endpoint shall be removed.
- **REQ-6.5 (Ubiquitous):** The view-restrictions code shall contain no debug logging
  scaffolding.

## Constraints

Not EARS-shaped — pure constraints with no triggerable behaviour:

- Work happens on `feat/dev-6778-view-restrictions-stepped-fetch`, branched from `main`.
- The OpenAPI client in dsp-das is regenerated rather than hand-edited.
- The API contract break is accepted: the generated client is the sole consumer.
- Removing `/summary` breaks any still-deployed older frontend. The dsp-api and dsp-das
  changes must therefore be released together, or the frontend released first against a
  transitional API that serves both shapes. Choosing between those two is a release
  concern, not a design one, but it must not be discovered at deploy time.
- Chunking is removed deliberately. A single resource class too large for one query will
  fail, surfacing as an error row (US-3). This is an accepted risk, not an oversight.
- The value query's `?prop rdfs:subPropertyOf* knora-base:hasValue` traversal
  (`ViewRestrictionsRepo.scala:667-669`) stays unbound. The `VALUES ?prop` binding that
  would constrain it exists only on the abandoned branch and is deliberately not ported.
  Together with the previous point this is the whole of the residual risk to the "no HTTP
  500" goal: both are accepted, both are visible as error rows, and both have a known
  remedy if measurement forces the issue.

## Success criteria

- The full report completes on the largest dev-DB project with no HTTP 500.
- Queries per full report drop from `6 × chunks × 2 units + 4 fixed` to `1 + N(classes)`.
- Step 1 latency is measured against the **unchunked** single-query baseline (~8.1s on the
  46k-resource / 265k-value project cited at `ViewRestrictionsRepo.scala:182`), not against
  today's chunked parallel `totalResourcesByClass`, which is not a comparable shape.
- One failing class leaves every other row populated and readable.

## Out of scope

- Property-mode grouping in any form. Deleted, not deferred behind a flag; re-add later
  as its own route if the frontend needs it.
- Server-side ordering (`orderKey`) and server-computed `totals`.
- Server-side caching. The frontend's per-`(class, itemType)` cache is the only caching.
- Pagination within a single resource class.
- Porting anything from `fix/dev-6778-view-restrictions-followup`, including its
  `VALUES ?prop` binding and `OntologyRepo.findAllValuePropertyIris`. Decided, not deferred.
- Real `rdfs:label` values for classes. The current `localName(iri)` derivation is retained
  (REQ-1.1) so that label sourcing does not enlarge this rework; it is a separate change.

## Open questions

1. Is `view-restrictions-timeout = 60 seconds` still warranted once each query is a single
   grouped scan, or should it return to the default `query-timeout`?
2. What is the largest single resource class on LHTT, by value count? This determines
   whether the accepted no-chunking risk (see Constraints) is theoretical or will be hit on
   day one. Worth measuring against the dev DB **before** implementation starts, since a
   bad answer reopens the chunking decision.
3. This feature introduces new UI states — stepped progress, per-row error, per-row retry,
   incomplete-totals indication, and an empty-project state — and no Claude Design context
   exists in this spec folder. Should a Claude Design intent brief be prepared, or is the
   existing view-restrictions table treatment sufficient to extend by analogy?
