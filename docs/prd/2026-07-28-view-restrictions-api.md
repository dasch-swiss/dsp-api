# PRD — View Restrictions API (backend)

- **Status:** Draft
- **Date:** 2026-07-28
- **Author:** Julien Schneider
- **Area:** dsp-api · admin API · slice architecture
- **Related design:** "View restrictions" project-admin screen, iteration **1h** (combined view, with a group-by toggle)

---

## 0. Implementation status (2026-07-28)

Implemented on `feature/dev-6778` (`ViewRestrictionsEndpoints` / `…ServerEndpoints` /
`ViewRestrictionsRestService` / `ViewRestrictionsService` / `ViewRestrictionsRepo`), with a
service+repo spec (`ViewRestrictionsServiceSpec`):

- Both routes, both group-by modes (`resourceClass`, `property`), and all item types
  (`resource`, `file`, `value`, `comment`, `all`).
- Visibility resolved via `PermissionUtilADM` for the three audiences; summary counts hidden
  only (code 0), restricted-view surfaced on drill-down items.
- **Large-project guardrail (Open Q #1) resolved:** each SPARQL query is capped at
  `ViewRestrictionsRepo.ScanCap` rows; when a query hits the cap the summary carries
  `approximate = true` (counts are then a lower bound). A configurable cap / cache remains the
  performance follow-up.
- **Comment semantics clarified:** a comment is a `valueHasComment` literal on a value, not an
  independently-permissioned object, so it shares its value's permissions. Under `itemType=all`
  a commented value is counted once (not double-counted as value + comment); `itemType=comment`
  reports comment-bearing restricted values; the drill-down lists the comment as its own row.

Still open: OpenAPI is served live at `/docs`; a committed spec is dumped via `just dump-openapi`.
Label/ontology lookup is a lightweight IRI-localname derivation pending proper ontology-label
resolution.

---

## 1. Context

DSP stores resources whose individual parts — the whole resource, its file value
(image/video/document/…), its ordinary values, and the comments on those values —
each carry object-access permissions. Depending on those permissions, a given part
may be **hidden**, served in **restricted view** (watermarked / reduced-size, images
only), or **fully visible** to a particular audience.

Today a project admin has no way to see, project-wide, *what is restricted from whom*.
Visibility is only ever resolved for one object at a time, on read
(`PermissionUtilADM.getUserPermissionADM`, and for files
`AssetPermissionsResponder`). There is no aggregate report.

The **1h** design gives project admins exactly that: a per-project matrix of restricted
items, broken down by three audiences and drillable down to the individual resource,
file value, value or comment — with a link to open each item in the data browser.

This PRD covers **only the backend routes** needed to power 1h. No Angular / UI work.

### Audiences

The screen reports against three fixed audiences, which map directly onto DSP's
built-in groups (`KnoraGroupRepo.builtIn`) and onto the group-set logic already in
`PermissionUtilADM.getUserPermissionADM`:

| Screen label | Audience | Group set used to resolve permissions |
| --- | --- | --- |
| **Logged out** | anonymous | `{ UnknownUser }` |
| **Logged in** | authenticated non-member | `{ KnownUser }` |
| **Project member** | member of this project | `{ KnownUser, ProjectMember }` |

> Note: `ProjectAdmin` / `Creator` / `SystemAdmin` are **not** modelled as reportable
> audiences — the report is about who is *restricted*, and admins are never restricted.

### Permission codes → visibility states

Every object resolves, per audience, to an object-access permission code
(`Permission.ObjectAccess`):

| Code | Level | Screen eye-state |
| --- | --- | --- |
| `0` | no access | **hidden** (`visibility_off`) |
| `1` | RestrictedView (RV) | **restricted view** (`blur_on`) — image file values only |
| `≥ 2` | View / Modify / … | **visible** (`visibility`) |

**Count semantics (decided):** the summary *number* counts **hidden only** (code `0`).
Restricted-view (code `1`) is surfaced as its own eye-state on drill-down rows but is
**not** added to any summary count. Because RV only applies to image file values, the
RV state can only appear on file-value rows.

---

## 2. Goals

1. Let a **project admin** retrieve, for a project, **how many items are hidden** from
   each audience, broken down by item type (whole resource / file value / value /
   comment) and grouped by **resource class** or by **property**.
2. Let them **drill down** into any one group (class or property) and page through the
   **affected resources and items**, each carrying its **per-audience visibility state**
   (visible / hidden / restricted-view).
3. Report visibility that faithfully reflects DSP's real permission model — reusing
   `PermissionUtilADM` semantics rather than inventing a parallel notion of "hidden".
4. Keep the contract stable and cache-friendly so the count strategy can evolve
   (see §7) without breaking clients.

---

## 3. Core Features

Two secured admin route families, plus the shared response vocabulary.

### 3.1 Summary route — the matrix

Fills the top-level 1h matrix: one row per group (class or property), one cell per
audience, plus a footer total row.

```
GET /admin/projects/{projectIri}/view-restrictions/summary
      ?groupBy=resourceClass | property     (default resourceClass)
      &itemType=all | resource | file | value | comment   (default all)
```

- `groupBy` — drives the 1h **group-by toggle**. Both modes are in v1.
    - `resourceClass`: rows are resource classes; sub-typing by item type applies.
    - `property`: rows are the properties that carry the restriction. Whole-resource
      restrictions are out of scope in property mode (a whole-resource restriction is not
      carried by a property), so `itemType=resource` is not meaningful there and is
    treated as `all`.
- `itemType` — drives the 1h **filter chips**. `comment` counts restricted
  `knora-base:valueHasComment` values.
- **Response** (shape, JSON):

```jsonc
{
  "projectIri": "http://rdfh.ch/projects/0001",
  "groupBy": "resourceClass",
  "itemType": "all",
  "audiences": ["anonymous", "authenticated", "projectMember"],
  "groups": [
    {
      "id": "http://.../ontology/0001/anything#Thing",   // class IRI, or property IRI in property mode
      "label": "Thing",
      "ontology": "anything",                             // short ontology label
      "kind": "image",                                    // resource-kind hint for the row icon (class mode)
      "propertyName": "anything:hasPicture",              // property mode only
      "counts": {                                         // HIDDEN counts (code 0) per audience
        "anonymous": 7,
        "authenticated": 2,
        "projectMember": 0
      }
    }
    // …one entry per class/property that has ≥1 restricted item under the filter
  ],
  "totals": { "anonymous": 41, "authenticated": 12, "projectMember": 3 }
}
```

- Counts are **cumulative in the same sense the model is**: an item hidden from
  authenticated users is necessarily hidden from anonymous users, so
  `anonymous ≥ authenticated ≥ projectMember` holds for any group.
- Groups with all-zero counts under the active filter are omitted.

### 3.2 Drill-down route — affected items, paginated

Fills the expanded rows under one group. Returns the affected resources and, nested,
the restricted file values / values / comments, each with its per-audience visibility.

```
GET /admin/projects/{projectIri}/view-restrictions/items
      ?groupBy=resourceClass | property
      &group={class-or-property-IRI}          (required — the expanded row)
      &itemType=all | resource | file | value | comment
      &page=1&page-size=25                     (PageAndSize; max size 100)
```

- **Response** uses the existing `PagedResponse[A]` envelope
  (`slice/api/api.Pagination`), so pagination metadata is consistent with the rest of
  the admin API:

```jsonc
{
  "data": [
    {
      "resourceIri": "http://rdfh.ch/0001/abc",
      "label": "C_014 · Queen of Hearts",
      "resourceClassIri": "http://.../anything#Thing",
      "kind": "image",
      "resourceVisibility": {                     // whole-resource visibility per audience
        "anonymous": "hidden",
        "authenticated": "visible",
        "projectMember": "visible"
      },
      "items": [                                  // restricted parts nested under the resource
        {
          "type": "file",                         // file | value | comment
          "propertyIri": "http://.../knora-api#hasStillImageFileValue",
          "propertyLabel": "Still image file",
          "valueIri": "http://rdfh.ch/0001/abc/values/xyz",
          "visibility": {
            "anonymous": "restrictedView",
            "authenticated": "visible",
            "projectMember": "visible"
          }
        }
      ]
    }
  ],
  "pagination": { "pageSize": 25, "totalItems": 42, "totalPages": 2, "currentPage": 1 }
}
```

- Each `visibility` value is one of `"visible" | "hidden" | "restrictedView"`, derived
  from the resolved permission code (`≥2` / `0` / `1`).
- `restrictedView` can only appear on `type: "file"` items for image file values.
- In **property mode** the drill-down returns the same shape, filtered to items carried
  by the requested property; whole-resource entries are omitted (see §3.1).
- The client provides the `group` value from the summary response's `groups[].id`; the
  contract is that the same `groupBy` + `group` pair used in the summary yields the
  matching drill-down.

### 3.3 Shared vocabulary

- `Audience` enum: `anonymous`, `authenticated`, `projectMember`.
- `Visibility` enum: `visible`, `hidden`, `restrictedView`.
- `ItemType` enum: `all`, `resource`, `file`, `value`, `comment`.
- `GroupBy` enum: `resourceClass`, `property`.

These are backed by, and computed from, existing building blocks (see §6) — they are a
reporting projection, not a new permission concept.

---

## 4. User Stories & Acceptance Criteria

### Story A — see the restriction matrix

*As a project admin, I want a per-audience count of hidden items grouped by resource
class, so I can see at a glance where my project restricts data.*

- **AC1** `GET …/view-restrictions/summary` on a project I administer returns 200 with
  one group per class that has ≥1 hidden item, each with hidden counts for the three
  audiences and a totals row.
- **AC2** `anonymous ≥ authenticated ≥ projectMember` for every group and for totals.
- **AC3** `itemType=file` returns counts of hidden **file values only**; `value`,
  `comment`, `resource` likewise; `all` sums resource + file + value + comment.
- **AC4** A caller who is not project admin (nor system admin) on the target project
  gets 403; an unauthenticated caller gets 401.
- **AC5** A malformed `projectIri` returns 400; an unknown project returns 404.

### Story B — switch to property grouping

*As a project admin, I want to group the same matrix by the property that carries the
restriction.*

- **AC6** `groupBy=property` returns groups keyed by property IRI with `propertyName`,
  counting file/value/comment restrictions carried by that property.
- **AC7** In property mode, whole-resource restrictions are not reported, and
  `itemType=resource` behaves as `all`.

### Story C — drill into a group

*As a project admin, I want to page through the affected resources and their restricted
parts under a class/property, each showing what each audience can see.*

- **AC8** `GET …/view-restrictions/items?group=<IRI>` returns a `PagedResponse` of
  affected resources, each with `resourceVisibility` and nested restricted `items`.
- **AC9** Every `visibility` value is exactly one of `visible | hidden |
  restrictedView`, consistent with the resolved permission code.
- **AC10** `restrictedView` appears only on image file-value items; never on
  resource/value/comment items.
- **AC11** Pagination honours `page` / `page-size` (`PageAndSize`, max 100) and returns
  correct `totalItems` / `totalPages`.
- **AC12** The count of resources+items surfaced for a group under a filter is
  consistent with that group's summary counts for the same filter.

### Story D — faithful to the permission model

- **AC13** For any object, the reported per-audience visibility equals what
  `PermissionUtilADM.getUserPermissionADM` would grant a synthetic user in that
  audience's group set (`{UnknownUser}` / `{KnownUser}` / `{KnownUser, ProjectMember}`),
  including the anonymous fallback behaviour.
- **AC14** Deleted resources/values (`knora-base:isDeleted = true`) and non-current
  value versions (`previousValue*` history) are excluded from all counts and drill-downs.

---

## 5. Constraints

- **Stack:** Scala 3, ZIO, Tapir endpoints, Fuseki triplestore; admin API under the
  slice architecture.
- **Layering (actual paths — the design's `slice/admin/api` does not exist):**
    - HTTP endpoints / rest-services: `modules/webapi/…/slice/api/admin/`
    - Admin domain / repo: `modules/webapi/…/slice/admin/{domain,repo}/`
    - Follow the projects example: `ProjectsEndpoints` → `ProjectsServerEndpoints`
      → `ProjectRestService` → `KnoraProjectService`/`KnoraProjectRepo`, wired in
      `AdminApiModule` + aggregated in `AdminApiServerEndpoints`.
- **Authorization:** secured endpoints; require **ProjectAdmin on the target project or
  SystemAdmin** (`BaseEndpoints.securedEndpoint` + the project-admin check used by
  existing project-scoped admin routes).
- **SPARQL:** no string concatenation — use the rdf4j sparqlbuilder DSL via
  `QueryBuilderHelper` and `Vocabulary.KnoraBase`, following
  `CountPropertyUsedWithClassQuery` (COUNT + GROUP BY) and `FileValuePermissionsQuery`
  (creator/project/permissions selection). Exclude `isDeleted` and `previousValue`.
- **IRI handling:** `projectIri` is a simple typed IRI (`ProjectIri`); class/property
  group IRIs are SmartIri-backed → accept as `IriDto`, convert via `IriConverter` in the
  RestService (per `docs/development/dsp-api-v3-iri-handling.md`; the same two-category
  rule applies here).
- **Pagination:** reuse `PageAndSize` / `Pagination` / `PagedResponse` — do not invent a
  new pagination shape.
- **Testing:** endpoint tests mirroring the projects admin tests; verify AC2/AC12
  cumulative + consistency invariants against a known fixture project. Prefer a
  self-contained fixture over adding rows to shared datasets (per CLAUDE.md testing
  guidelines).

---

## 6. How it grounds in existing code (reuse map)

> From codebase research — cite these when implementing.

- **Per-object visibility:** `PermissionUtilADM.getUserPermissionADM(entityCreator,
  entityProject, permissionLiteral, requestingUser)` → `Option[Permission.ObjectAccess]`.
  Build a synthetic requesting user per audience from the group sets in §1.
- **Permission codes:** `Permission.ObjectAccess` — `RestrictedView`=1, `View`=2,
  `Modify`=6, `Delete`=7, `ChangeRights`=8. Visibility mapping: code 0 → hidden, 1 → RV,
  ≥2 → visible.
- **Groups:** `KnoraGroupRepo.builtIn` — `UnknownUser`, `KnownUser`, `ProjectMember`.
- **Restricted view:** `RestrictedView` (`Watermark`, `Size`) on `KnoraProject`;
  enforcement precedent in `AssetPermissionsResponder`
  (code `1` → project restricted-view settings). The report does **not** need to serve
  RV settings — only to label an item's state as `restrictedView`.
- **Value / file / comment structure:** `ValueContentV2` (carries `.comment`),
  `FileValueContentV2` (file marker), `StillImageFileValueContentV2` (image → RV
  eligible); RDF constants in `OntologyConstants.KnoraBase` (`hasStillImageFileValue`,
  `valueHasComment`, `attachedToUser`, `attachedToProject`, `hasPermissions`,
  `isDeleted`, `previousValue`).
- **SPARQL patterns:** `CountPropertyUsedWithClassQuery` (COUNT+GROUP BY, `minus`
  isDeleted), `FileValuePermissionsQuery` (creator/project/permissions select),
  `QueryBuilderHelper`, `Vocabulary.KnoraBase`, executed via `TriplestoreService.query`.
- **API surface / pagination:** `slice/api/admin` 5-file layering; `PageAndSize` /
  `Pagination` / `PagedResponse` (see `ProjectsLegalInfoEndpoints` for a paged +
  filtered admin endpoint to copy).

---

## 7. Count strategy (decided, with rationale)

**Primary approach: per-group count queries (strategy B).** The summary handler issues
**one count query per resource class** (and per property in property mode) rather than
one monolithic aggregate. Rationale:

- It matches how the UI reveals data (expand one group at a time), and it makes each
  unit independently **cacheable** and **cancellable** in future.
- A slow/huge class does not block counts for the rest of the matrix.
- The natural cache key is `(project, groupBy, group, audience, itemType)`.

**Caching: none in v1 (decided).** Counts are computed fresh on every request in v1.
Per-group query shaping is therefore a *design affordance for a future cache*, not a v1
performance win — this is called out honestly so nobody assumes v1 is fast on large
projects. Adding a cache (with write-path invalidation) is the first performance
follow-up (§8).

**Known cost.** Even with strategy B, the initial summary render needs a count for every
class, so total DB work is O(restricted-bearing objects in project) regardless. On large
projects (e.g. classes with thousands of instances) the summary request can be slow.
See Open Questions for the guardrail decision.

---

## 8. Out of Scope (v1)

- **Any UI / Angular work.** The 1h screen itself lives in dsp-app (separate repo).
- **Caching and cache invalidation** of counts (documented as the first perf follow-up).
- **Precomputed / materialized summaries** (strategy C).
- **Serving restricted-view rendering settings** through these routes (already available
  via the project restricted-view settings endpoint + `AssetPermissionsResponder`).
- **Editing permissions / restrictions.** This feature is read-only reporting.
- **Reporting admin/creator/system audiences** as restricted audiences.
- **Whole-resource restrictions in property mode.**
- **Counting deleted objects or historical value versions.**
- **Cross-project / instance-wide roll-ups.** Everything is scoped to one project.
- **A single-object "why is this hidden?" explanation endpoint** (possible later).

---

## 9. Open Questions

1. **Large-project guardrail.** ~~With no cache in v1, what protects the summary request…~~
   **Resolved (see §0):** each query is capped at `ScanCap` rows and the summary reports
   `approximate = true` when the cap is hit. A configurable cap and a cached summary remain the
   performance follow-up.
2. **Definition of "affected resource" in the drill-down** when only a nested part is
   restricted but the resource itself is fully visible — confirmed included (resource is
   listed with `resourceVisibility` all-visible and the restricted parts nested). Confirm
   this is the desired reading for property mode too.
3. **Property-mode counting of comments.** A comment is attached to a value of some
   property; in property mode, should a restricted comment be grouped under that value's
   property, or under a synthetic "comment" grouping? (Assumed: under the value's
   property.)
4. **Ontology/label localisation.** Which language are `label` / `propertyLabel`
   returned in — the requesting user's preferred language, or the project default?
5. **Stable group ordering.** Should groups be ordered by descending anonymous-hidden
   count (as 1h shows), and is that ordering part of the contract or a UI concern?
6. **Exact project-admin authorization predicate** to reuse (the same check as other
   project-scoped admin write endpoints) — confirm against `BaseEndpoints` /
   `ProjectRestService`.

---

## 10. Next Steps

1. Review & resolve the Open Questions (esp. #1 guardrail and #6 auth predicate).
2. Run a **perf spike**: benchmark a single per-class count query on the largest
   available fixture/dev project to size the guardrail decision.
3. Turn this PRD into an implementation plan (`/plan`) — scaffold the 5-file admin slice
   (`ViewRestrictionsEndpoints` → `…ServerEndpoints` → `…RestService` → domain service →
   repo query), wire into `AdminApiModule` + `AdminApiServerEndpoints`.
4. Add endpoint tests mirroring the projects admin suite, asserting AC2/AC9/AC12
   invariants against a self-contained fixture.
