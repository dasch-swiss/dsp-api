---
title: "feat: view restrictions grouped by property"
type: feat
date: 2026-08-26
author: "Julien Schneider"
status: draft
prd: 01-view-restrictions-by-property-PRD.md
branch: feat/view-restrictions-by-property
repositories:
  - name: dsp-api
    path: /Users/julien/WebstormProjects/dsp-api
  - name: dsp-das
    path: /Users/julien/WebstormProjects/dsp-das
---

# feat: view restrictions grouped by property

## Overview

Three new admin routes reporting view restrictions grouped by property rather than by
resource class, with their own service, repo, endpoints and frontend state. The class
report is not touched.

## Problem Statement / Motivation

A property can be used by many resource classes, so a misconfigured property is scattered
across many rows of the class report and its pattern is invisible. Property grouping
existed as `?groupBy=Property` and was deleted in `457f8bd44` — it was a flag threaded
through roughly a dozen signatures, and it inherited the all-in-one-request shape that
timed out on large projects.

## Proposed Solution

Mirror the stepped shape of the class report with entirely separate code.

```
GET .../view-restrictions/properties
GET .../view-restrictions/property-values?property={iri}&itemType={t}
GET .../view-restrictions/property-items?property={iri}&itemType={t}&page=&page-size=
```

Everything below is grounded in measurements against a local copy of LHTT (project
`0820`: 105,983 resources, 859,723 values).

### Step 1 reads the ontology cache, not the triplestore

| Source of the property list | Time |
| --- | --- |
| `DISTINCT ?prop` from the data | 14.0s |
| Ontology cache | no SPARQL |

`OntologyRepo.findByProject(projectIri)` returns `List[ReadOntologyV2]`, each carrying a
`properties` map of `ReadPropertyInfoV2`. `knora-base` is **not** among a project's own
ontologies, so its built-in file-value and comment properties — used by project data
without being declared locally — must be fetched separately via `findById`.

Labels come from the same objects; `OntologyRestServiceV3.scala:32` shows the extraction
pattern (`languageString(info, Rdfs.Label.toSmartIri)`).

### Step 2 binds the property, and joins no class

Two measurements decided this shape. On `hasTitle` (66,484 values):

| Query shape | Time | Total |
| --- | --- | --- |
| `FILTER (?prop = <iri>)` | 3,060ms | — |
| IRI bound directly in the pattern | **1,053ms** | — |
| With `?resource a ?resClass` | 2,380ms | 66,484 |
| Without the class join | **1,128ms** | **66,484** |

So the query is:

```sparql
SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
WHERE {
  ?resource knora-base:attachedToProject <projectIri> ;
            knora-base:isDeleted false ;
            <thePropertyIri> ?value .          # bound, not filtered
  ?value knora-base:hasPermissions ?permissions ;
         knora-base:isDeleted false .
  FILTER NOT EXISTS { ?value a knora-base:LinkValue }
}
GROUP BY ?permissions
```

No `?resClass`, no `VALUES` clause, no most-specific-class filter, no `projectClasses`
call — and therefore no exposure to the 27s multi-typed probe that broke the class report
on LHTT. The class report needs those because it groups by class and must pin each
resource to exactly one; this counts `DISTINCT ?value` under a bound property and cannot
double-count.

`totalValues` is the sum of the group's counts over every literal, exactly as the class
report derives `totalResources`: no permission filter is applied, so the fully visible
literals are in the row set too.

### Step 3, the drill-down

Measured on `hasTitle`: a page of 25 ordered resources 3.2s, its `COUNT DISTINCT` total
1.1s, a deep page (OFFSET 2500) 1.7s. Issued in parallel as the class drill-down does, so
~3.2s to open a row on the worst-case property. Acceptable: user-initiated, one at a time.

## Technical Considerations

### What is duplicated, deliberately

`audienceUser`, `visibilityOf`, the synthetic-creator placeholder, and the fold of
permission-grouped rows into per-audience counts are copied rather than shared. The
soundness argument they rest on is subtle — the creator only matters when it equals the
requesting user, which a synthetic audience user never is — so **both** copies must have
a spec pinning the equivalence (PRD REQ-6.3). A divergence should fail a test, not
silently change counts.

### What must not be copied

- `FILTER (?prop = …)` — 3× slower than binding (measured above).
- `ProjectClasses`, its `VALUES` clause, `mostSpecificClass`, and the cached multi-typed
  probe. Not needed, and copying them would reintroduce the 27s cost for nothing.
- `ItemType` as the filter enum. The step-2 filter is value-only, so it takes
  `ValueItemType` (`All | File | Value | Comment`), as `/values` already does.

### Frontend: the toggle must not destroy state

The screen keeps one grouping toggle and mounts one table or the other. An Angular
service provided on a component is destroyed with that component, so the two page
services must be provided **above** the tables — otherwise every toggle clears the
per-property cache and PRD REQ-5.3 cannot hold.

### Conventions that apply

`CONVENTIONS.md`: the three-tier API split is mandatory (`*Endpoints` / `*ServerEndpoints`
/ `*RestService`); DTOs live in a sibling object inside the endpoints file; never add
variants to the shared `BaseEndpoints.errorOutputs`; build SPARQL with the rdf4j builder,
never string concatenation.

## Implementation Phases

### dsp-api

#### Phase 1: property list from the ontology cache

- [x] Add `ViewRestrictionsByPropertyRepo` with a `projectValueProperties(projectIri)` returning IRI + label + ontology name
- [x] Source it from `OntologyRepo.findByProject`, plus `findById` for `knora-base`
- [x] Decide and document the value-property predicate. `ReadPropertyInfoV2` exposes `isResourceProp`, `isLinkProp`, `isLinkValueProp`, `isFileValueProp` and `isStandoffInternalReferenceProperty`, and there is **no** existing "is a value property" helper in the ontology slice — the abandoned branch's `findAllValuePropertyIris` is not on main. Candidate: `isResourceProp && !isLinkProp && !isLinkValueProp` (unverified — confirm against a real ontology)
- [x] Add a test pinning that predicate against a project ontology with a link property, a link-value property and a file-value property, since it alone determines the row set
- [ ] Extract labels via the `Rdfs.Label` pattern used in `OntologyRestServiceV3.scala:32`
- [x] Confirm the method issues no SPARQL (PRD REQ-1.2)

#### Phase 2: the two counting queries

- [x] Add `propertyValueCountsQuery(projectIri, propertyIri, itemType)` grouped by `?permissions`, with the property IRI bound directly in the triple pattern
- [x] Assert by test that it emits no `?resClass`, no `VALUES`, and no `FILTER (?prop`
- [x] Add `propertyDrillDownPageQuery` and its matching `COUNT DISTINCT` total query
- [x] Project each resource's own class in the drill-down row (REQ-4.2) — a property spans classes, so the class is per row here rather than a property of the whole table
- [x] Add repo methods returning `Seq[PermissionCountRow]` and the drill-down rows
- [x] Copy the `File`/`Value`/`Comment` constraint into this repo rather than reusing `ViewRestrictionsRepo.itemTypeConstraint`. Reusing it is the seam this whole design avoids, and the fragment is a few lines of pattern-building; a test pins each branch

#### Phase 3: service layer

- [x] Add `ViewRestrictionsByPropertyService` with its own `audienceUser`, `visibilityOf` and synthetic-creator placeholder
- [x] Add `classify` resolving each distinct literal once per audience
- [x] Add `foldRows` producing per-audience counts plus `totalValues`
- [x] Add `properties(projectIri)`, `propertyValues(projectIri, property, itemType)` and `propertyItems(...)`
- [x] Take no `KnoraProjectRepo` and no `ViewRestrictionsRepo` dependency

#### Phase 4: API layer

- [x] Define the three endpoints in `ViewRestrictionsByPropertyEndpoints` with their DTOs
- [x] `/properties` takes no filter (REQ-1.6); `/property-values` and `/property-items` take `ValueItemType`
- [x] Import `ValueItemType` from `ViewRestrictionsEndpoints` rather than redefining it. It carries no grouping discriminator (PRD REQ-6.2), the two reports genuinely mean the same four value types, and a second identical enum would surface in the generated client as a confusing near-duplicate
- [x] Add `ViewRestrictionsByPropertyRestService` with the project-admin-or-sysadmin check on all three (REQ-1.5, REQ-2.8, REQ-4.5)
- [x] Validate `property` as a well-formed IRI, failing `BadRequestException` (REQ-2.7, REQ-4.6)
- [x] Wire `ViewRestrictionsByPropertyServerEndpoints`
- [x] Register in `AdminApiServerEndpoints` and `AdminApiModule`
- [x] Wire the service and repo in `AdminDomainModule`, adding them to its `Provided` type list
- [x] Add the new endpoints and rest service to `AdminApiModule`'s `Provided` list, and any new dependency to its `Dependencies` list

#### Phase 5: dsp-api tests

- [x] Query spec: the counting query binds the property and emits no `?resClass`, no `VALUES`, no `FILTER (?prop`
- [x] Query spec: the drill-down orders deterministically and windows in SPARQL
- [x] Service spec: pin the bare-literal / real-object equivalence for this service (REQ-6.3)
- [ ] Service spec: `totalValues` is the sum over every literal, visible ones included
- [ ] Service spec: a property no resource uses yields zero counts rather than an error
- [x] E2E: all three routes return 200 for an admin, 401 unauthenticated, 403 for a non-admin
- [x] E2E: 400 for a malformed `property` on both parameterised routes
- [x] E2E: each audience's counts do not exceed `totalValues` (REQ-2.6)
- [x] E2E: assert the class routes still answer with their current paths, parameters and status codes (REQ-6.1)
- [ ] Before merging, capture `/classes` and `/values` responses for one project on the parent branch and diff them against this branch — the "byte-identical" success metric is a manual gate, not something a single test run can assert

### dsp-das

#### Phase 6: client and state

- [x] Rebuild and redeploy the API image so the new routes are live (`just docker-build-dsp-api-image` then `docker compose up -d api`)
- [x] Regenerate the OpenAPI client from the running API at `localhost:3339/docs/docs.yaml` — note the docs are served by the instrumentation server on 3339, not the API port
- [x] Verify the regenerated client exposes all three new operations before touching any component
- [x] Add `ViewRestrictionsByPropertyPageService` with its own step-1 stream and bounded step-2 fan-out at concurrency 4
- [x] Accumulate per-property results with `scan`, `catchError` on the inner request
- [x] Cache per `(itemType, propertyIri)`; add `retryProperty`
- [x] Expose `progress$` and `anyFailed$`
- [x] Add `loadPropertyItems` for the drill-down

#### Phase 7: component and toggle

- [x] Add `ViewRestrictionsByPropertyTableComponent` with its own rows, progress, per-row error and retry
- [x] Add the grouping toggle to `view-restrictions.component`
- [x] Provide both page services above the tables so neither is destroyed on toggle (REQ-5.4)
- [x] Mount one table at a time (REQ-5.1)
- [x] Show `totalValues` as the property row's denominator
- [x] Add an empty state for a project with no value properties (REQ-1.7)
- [ ] Update the component spec and stories

#### Phase 8: i18n

- [x] Add grouping-toggle, property-column and `totalValues` keys to `en.json`
- [x] Mirror them in `de.json`
- [x] Mirror them in `fr.json`
- [x] Mirror them in `it.json`

## Verified against LHTT after Phases 1-4

Deployed locally and called against the real project (`0820`, 105,983 resources):

| | Result |
|---|---|
| `/properties` | **182 properties in 552ms** — exactly the 182 SPARQL reports for `subPropertyOf* knora-base:hasValue`, so the `isValueProperty` predicate is confirmed rather than assumed. Both sources present: `lhtt:*` from the project's ontology and `knora-base:hasArchiveFileValue` from the separately fetched built-ins |
| `/property-values` for `hasTitle` | 2,267ms, `totalValues` 66,484, all hidden from anonymous and authenticated. Cross-checked in SPARQL: 66,484 values, 66,484 distinct resources, and exactly **one** distinct permission literal — so "all hidden" is correct, not a bug |
| `/property-items` for `hasTitle` | 6,827ms, `totalItems` 66,484, each row carrying its own class (`PhotographyArchaeology`) |

**Correction to the earlier estimate:** the drill-down was measured at 3.2s during discovery, but that
query omitted the `OPTIONAL` label / fileClass / comment joins and the creator join the real one carries.
The true figure is ~6.8s on the worst-case property. Still user-initiated and one at a time, so viable —
but it is the slowest thing in this report and the first place to look if the drill-down feels sluggish.

## Acceptance Criteria

- [ ] REQ-1.1 to REQ-1.7 — the property list, cache-sourced, unfiltered, admin-only, with an empty state
- [ ] REQ-2.1 to REQ-2.10 — one property per request, bound not filtered, counts plus `totalValues`, no class join, admin-only, 400 on a malformed IRI
- [ ] REQ-3.1 to REQ-3.5 — concurrency 4, progress, per-row fill-in, per-row failure and retry, page-level step-1 failure
- [ ] REQ-4.1 to REQ-4.6 — paginated drill-down reporting each resource's own class, deterministic order, `/items` untouched, admin-only
- [ ] REQ-5.1 to REQ-5.4 — toggle mounts one table, separate services, cached across a switch, services outlive their tables
- [ ] REQ-6.1 to REQ-6.3 — class endpoints unchanged, no shared discriminator, both services pin the equivalence
- [ ] `just check` and `bazel test //modules/webapi:test` pass
- [ ] `bazel test //modules/test-e2e:test` passes
- [ ] `nx build dsp-app` passes

## Dependencies & Risks

The dsp-das branch sits on `feat/view-restrictions-by-property`, itself on the DEV-6778
branch, which depends on `feature/dev-6868-view-restrictions-screen` merging first.

The OpenAPI client must be regenerated **after** the API routes exist and are running, not
before — the reverse order already caused a `resourceClass (missing)` failure on the
previous branch.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Duplicated permission logic diverges from the class service | M | H | REQ-6.3: both services pin the bare-literal equivalence with their own spec, so a divergence fails a test |
| ~182 rows makes an unusable table | M | M | Open question in the PRD; the fix is UI-side (collapse zero rows, sort by density), not a fetch change |
| Toggle destroys the cache | M | M | REQ-5.4 and Phase 7: provide both services above the tables |
| `knora-base` properties missed, understating the report | M | H | Phase 1 fetches `knora-base` separately from the project's own ontologies; a service test covers a built-in file-value property |
| Copying `FILTER (?prop = …)` from the class route | L | M | Phase 2 asserts by test that the query contains no `FILTER (?prop` |
| Client regenerated before the API changes | M | M | Sequenced explicitly in Phase 6, after Phases 1-5 |

## Success Metrics

- Step 1 issues no SPARQL and the table paints immediately.
- Step 2 stays near the measured ~1.05s per property on LHTT.
- Opening a drill-down row stays near the measured ~3.2s worst case.
- `hasTitle` on LHTT reports `totalValues` of 66,484.
- The class report's responses are byte-identical before and after.

## References

- Parent PRD: `01-view-restrictions-by-property-PRD.md`
- Deleted property mode, for what not to repeat: commit `457f8bd44`
- Class report to mirror in shape: `ViewRestrictionsEndpoints.scala`, `ViewRestrictionsService.scala`, `ViewRestrictionsRepo.scala`
- Ontology lookups: `OntologyRepo.findByProject`, `OntologyRepo.findById`, `OntologyRepo.findProperty`
- Label extraction pattern: `slice/api/v3/ontology/OntologyRestServiceV3.scala:32`
- Conventions: `CONVENTIONS.md` § API layer, § SPARQL
- Frontend to extend: `dsp-das/libs/vre/pages/project/project/src/lib/project-settings/view-restrictions/`
