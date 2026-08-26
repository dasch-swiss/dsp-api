---
title: View restrictions grouped by property
date: 2026-08-26
status: draft
repositories:
  - dsp-api
  - dsp-das
branch: feat/view-restrictions-by-property
---

# View restrictions grouped by property

## Context

The view-restrictions report answers "which resource classes hold restricted content".
A property, though, can be used by many resource classes — so a property that is
misconfigured shows up scattered across many class rows, and the pattern is invisible.
An admin who wants to ask "what is restricted on `hasTitle`, everywhere?" cannot.

Property grouping is not a new idea here: it existed as `GET
.../view-restrictions/summary?groupBy=Property` and was **deleted** in `457f8bd44`. It
went for two reasons. It was implemented as a flag threaded through roughly a dozen
signatures across the repo, service and endpoints, which made every change to the class
report a change to property mode as well. And it inherited the class report's
all-in-one-request shape, which timed out on large projects.

This PRD reintroduces the capability as its **own routes with its own code**, following
the stepped shape the class report now uses.

### What the numbers say

Measured on LHTT (project `0820`, 105,983 resources, 859,723 values) against a local
Fuseki holding a dev-DB copy:

| Query | Time |
| --- | --- |
| Whole project, `GROUP BY ?prop ?permissions` | **24.8s** |
| `DISTINCT ?prop` discovered from the data | **14.0s** |
| One property, narrowed by `FILTER (?prop = <iri>)` | 3.1s |
| One property, **bound directly** in the triple pattern | **1.05s** |

Three consequences, each load-bearing for the design below:

1. A single whole-project request is not viable — 24.8s, and that is before the report
   is rendered. The stepped shape is required, not merely preferred.
2. The property list must **not** be discovered from the data. 14s of blank screen is
   precisely what stepping exists to avoid. The ontology cache has it for free.
3. Binding the property IRI directly into the pattern is **3× faster** than filtering on
   it. The class route narrows with `FILTER (?resClass = <iri>)`; the property route must
   not copy that. This is a case where separate code is faster, not just tidier.

## Goals

1. Answer "what is restricted on this property, across every class that uses it".
2. Keep the stepped loading behaviour of the class report: paint immediately, fill in
   progressively, contain a failure to one row.
3. Share no backend code with the class report unless duplication would risk correctness.
4. Do not slow down or destabilise the class report.

## Core design

### Three new routes

```
GET .../view-restrictions/properties
GET .../view-restrictions/property-values?property={iri}&itemType={t}
GET .../view-restrictions/property-items?property={iri}&itemType={t}&page=&page-size=
```

Deliberately distinct paths rather than a parameter on the existing three. The class
routes keep their exact shapes.

### Step 1 — `/properties`

The project's value properties, from the **ontology cache**: the project's own ontologies
plus `knora-base` (whose built-in file-value and comment properties are used by project
data without being declared in the project's ontology). No SPARQL, so it is effectively
instant, and the table paints at once.

Returns IRI, label and ontology name per property — **no counts**. This differs from the
class route, whose step 1 also carries resource-level counts, and the reason is
structural: a class has two units (resources and the values inside them) so its step 1
can answer the first; a property only ever has values, so there is nothing for step 1 to
count that step 2 does not.

Every value property in the project's ontologies is listed, including ones no resource
uses. On LHTT that is ~182 rows against ~140 that carry permissioned values, so roughly
40 rows will settle at zero. That is honest — "this property exists and nothing on it is
restricted" — and it matches the class report, which lists unrestricted classes too.

### Step 2 — `/property-values`

One request per property, `?prop` **bound directly** in the triple pattern rather than
filtered. Returns, for that property:

- `totalValues` — how many values of this property the project holds, restricted or not
- per-audience `hidden` / `restrictedView` counts

Both come from one query grouped by permission literal, exactly as the class route
derives `totalResources`: with no permission filter applied, summing a group's counts
over every literal — the fully visible ones included — gives its whole population.

`totalValues` is the property analogue of `totalResources` and is a true denominator:
the counts are in the same unit, so "94 of 66,484 `hasTitle` values are hidden" is a
sound statement.

### Drill-down — `/property-items`

Paginated list of the resources carrying a restricted value of that property, each with
the affected values. Its own route and its own query: the existing `/items` is keyed by
`resourceClass` and stays that way.

Unlike the class drill-down, a row here can span classes, so each affected resource
reports its own class.

## Repository impact

### dsp-api

| File | Change |
| --- | --- |
| `slice/api/admin/ViewRestrictionsByPropertyEndpoints.scala` | New. Three endpoint definitions and their DTOs |
| `slice/api/admin/ViewRestrictionsByPropertyServerEndpoints.scala` | New. Wiring |
| `slice/api/admin/service/ViewRestrictionsByPropertyRestService.scala` | New. Auth and IRI validation |
| `slice/admin/domain/service/ViewRestrictionsByPropertyService.scala` | New. Literal classification and folding |
| `slice/admin/repo/ViewRestrictionsByPropertyRepo.scala` | New. Three queries |
| `slice/api/admin/AdminApiModule.scala`, `AdminApiServerEndpoints.scala` | Register the new endpoints |
| `slice/admin/domain/AdminDomainModule.scala` | Wire the new service and repo |
| `ontology/domain/service/OntologyRepo.scala` (+ `OntologyRepoLive`) | Add value-property lookup, if not already present |
| New specs mirroring the existing three | Query, service and E2E coverage |

### dsp-das

| File | Change |
| --- | --- |
| `.../view-restrictions/view-restrictions-by-property-page.service.ts` | New. Its own stepped fetch, cache and per-row error state |
| `.../view-restrictions/view-restrictions-by-property-table.component.ts` | New. The property table |
| `.../view-restrictions/view-restrictions.component.{ts,html}` | Add the grouping toggle; mount one table or the other |
| `libs/vre/3rd-party-services/open-api/dsp-api_spec.yaml` | Regenerate after the API lands |
| i18n `en/de/fr/it.json` | Toggle labels, property column, `totalValues` |

## Deliberate duplication, and its one exception

The permission-literal machinery is duplicated rather than shared: the audience users,
`visibilityOf`, the synthetic-creator placeholder, and the fold of grouped rows into
per-audience counts.

This is a real cost and worth naming. The soundness argument behind it is subtle — the
creator only matters when it equals the requesting user, which a synthetic audience user
never is, which is what makes classifying a bare literal equivalent to classifying every
object carrying it — and it is currently pinned by one spec. Duplicating it means two
places that must stay correct.

It is duplicated anyway, because the alternative is the shared seam that produced the
`groupBy` flag. The mitigation is that **both** copies get the equivalence pinned by
their own spec, so a divergence fails a test rather than silently changing counts.

The one exception: `projectClasses`, its 27s-probe gate and its cache are **reused as
is**. That is not report-shaped logic — it is a fact about the project, it is already
cached per project, and duplicating it would mean paying the probe twice per report.

## User stories and acceptance criteria

### US-1

As a project admin, I want to see every value property in my project with what is
restricted on it, so I can spot a property that is misconfigured across many classes.

- **REQ-1.1 (Event-driven):** When a project admin opens the property view, the API shall
  return every value property of the project's own ontologies and of `knora-base`, with
  its IRI, label and ontology name.
- **REQ-1.2 (Ubiquitous):** The properties endpoint shall source that list from the
  ontology cache and shall issue no SPARQL query.
- **REQ-1.3 (Ubiquitous):** The properties endpoint shall return no counts.
- **REQ-1.4 (Event-driven):** When the properties response arrives, the frontend shall
  render one row per property, including properties no resource uses.
- **REQ-1.5 (Ubiquitous):** The properties endpoint shall require the requesting user to
  be project admin on the target project or system admin.

### US-2

As a project admin, I want each property's restriction counts against a denominator, so
I can tell a deliberate policy from an accident.

- **REQ-2.1 (Ubiquitous):** The property-values endpoint shall answer for exactly one
  property per request.
- **REQ-2.2 (Ubiquitous):** The property-values endpoint shall bind the property IRI
  directly in the triple pattern and shall not narrow by `FILTER`.
- **REQ-2.3 (Ubiquitous):** The property-values endpoint shall report per-audience
  `hidden` and `restrictedView` counts and a `totalValues` population.
- **REQ-2.4 (Ubiquitous):** The property-values endpoint shall derive `totalValues` by
  summing the property's per-permission-literal counts, issuing no separate query.
- **REQ-2.5 (Ubiquitous):** The property-values endpoint shall compute its counts with a
  single SPARQL query grouped by permission literal.
- **REQ-2.6 (Ubiquitous):** Each audience's counts shall not exceed `totalValues`.
- **REQ-2.7 (Unwanted-behaviour):** If the `property` parameter is absent or is not a
  well-formed IRI, then the endpoint shall respond 400.

### US-3

As a project admin, I want the property table to load progressively and survive a
failure, as the class table does.

- **REQ-3.1 (Event-driven):** When the property list has rendered, the frontend shall
  request counts per property, at most 4 requests concurrently.
- **REQ-3.2 (State-driven):** While counts are being gathered, the frontend shall display
  the number of properties completed out of the total.
- **REQ-3.3 (Event-driven):** When a property's counts arrive, the frontend shall
  populate that row without waiting for other properties.
- **REQ-3.4 (Unwanted-behaviour):** If a counts request fails, then the frontend shall
  mark that property's row as not computed, offer a retry for it alone, and continue with
  the remaining properties.
- **REQ-3.5 (Unwanted-behaviour):** If the step-1 request fails, then the frontend shall
  show a page-level error and shall issue no step-2 requests.

### US-4

As a project admin, I want to drill into a property and see which resources are affected.

- **REQ-4.1 (Event-driven):** When the admin expands a property row, the API shall return
  a paginated list of the resources carrying a restricted value of that property.
- **REQ-4.2 (Ubiquitous):** Each returned resource shall report its own resource class,
  since a property spans classes.
- **REQ-4.3 (Ubiquitous):** The property drill-down shall order its results
  deterministically so that paging is stable.
- **REQ-4.4 (Ubiquitous):** The existing `/items` endpoint shall continue to be keyed by
  `resourceClass` and shall not gain a property parameter.

### US-5

As a project admin, I want to switch between the class and property views on one screen.

- **REQ-5.1 (Event-driven):** When the admin selects a grouping, the frontend shall mount
  the corresponding table and leave the other unmounted.
- **REQ-5.2 (Ubiquitous):** Each grouping shall have its own page service and its own
  request state.
- **REQ-5.3 (Event-driven):** When the admin switches grouping and switches back, the
  frontend shall serve counts it already holds rather than refetching.

### US-6

As a maintainer, I want the class report untouched by this work.

- **REQ-6.1 (Ubiquitous):** The class endpoints shall keep their current paths,
  parameters and response shapes.
- **REQ-6.2 (Ubiquitous):** No shared type shall gain a grouping discriminator.
- **REQ-6.3 (Ubiquitous):** Both the class service and the property service shall each
  have a spec pinning that classifying a bare permission literal against the synthetic
  creator equals classifying a real object.

## Constraints

Not EARS-shaped — pure constraints:

- Work happens on `feat/view-restrictions-by-property` in both repos, branched from the
  DEV-6778 branches.
- The dsp-das branch depends on `feature/dev-6868-view-restrictions-screen` merging first,
  as the DEV-6778 branch already does.
- The OpenAPI client is regenerated from a running API, not hand-authored.

## Success criteria

- The property table paints immediately and fills in progressively on LHTT.
- No single request exceeds a few seconds: step 1 is cache-only, step 2 measured at
  ~1.05s per property.
- `hasTitle` on LHTT reports 66,484 total values with its restriction counts.
- The class report's responses are byte-identical before and after this work.
- One failing property leaves every other row populated.

## Out of scope

- Changing the class report in any way beyond mounting a toggle above it.
- Sorting or ranking properties by restriction density. Rows arrive in ontology order.
- Cross-referencing the two views (e.g. "which classes contribute to this property's
  count") — the drill-down answers that per resource.
- Discovering the property list from data rather than the ontology.

## Open questions

1. ~182 properties at concurrency 4 is roughly 45s of progressive filling on LHTT,
   against 43 classes today. Is that acceptable, or should the frontend prioritise
   properties the class view already showed as restricted?
2. Should properties with `totalValues == 0` be hidden once known, or kept as explicit
   zero rows? Kept for now (REQ-1.4), but it is a UI judgement that only looks right or
   wrong with real data on screen.
3. Does the drill-down need an itemType filter, or is that only meaningful on the counts?
