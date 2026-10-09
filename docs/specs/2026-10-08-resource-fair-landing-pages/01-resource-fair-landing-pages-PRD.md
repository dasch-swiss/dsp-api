---
title: "Resource pages an ARK resolves to carry FAIR metadata and Signposting"
date: 2026-10-08
author: "Raitis Veinbahs"
status: draft
linear: DEV-7420
repositories:
  - dsp-app
---

# Resource pages an ARK resolves to carry FAIR metadata and Signposting

## Context

Project ARKs resolve to the DPE project page, which since DEV-7268 carries
machine-readable metadata and FAIR Signposting; F-UJI 3.5.0 moved from 3 of 24
to 16–21 of 24 on it. Resource and value ARKs resolve to dsp-app. All four ARK
registry templates (`DSPResourceRedirectUrl`, `DSPResourceVersionRedirectUrl`,
`DSPValueRedirectUrl`, `DSPValueVersionRedirectUrl`) end on one route:

```
https://$host/resource/$project_id/$resource_id[?version=$timestamp][&highlightValue=$value_id]
```

dsp-app is a client-rendered Angular SPA served as static files by nginx. Every
route returns the same `index.html` (`<title>DaSCH Service Platform</title>`),
with status 200 even when the resource is missing or forbidden, identical for
every `Accept`, with no JSON-LD, no Dublin Core `<meta>` and no `Link` header.
An assessor or harvester following a resource ARK finds nothing it can read, so
DaSCH's FAIR claim holds for projects only.

dsp-api serves no HTML and emits no `Link` headers today. It already holds every
fact the page needs, and `ExportService.exportResourcesOai` already derives a
per-resource `MetadataRecord` — the contract DPE's record mappings consume.

## Normative sources

In precedence order. Where a source is followed, this PRD does not restate it.

| # | Source | Governs | How closely |
|---|--------|---------|-------------|
| 1 | dsp-repository `docs/adr/0005-fair-landing-pages-in-the-access-area.md` | The obligations of a landing page: embedded metadata, Signposting, representation URLs, the one negotiation step, nothing invented | Fully, except the deviations this PRD names (representations on the API host; see Open Questions) |
| 2 | dsp-repository `docs/src/dpe/machine-readable-metadata.md` | Field shapes: `identifier` (ARK `PropertyValue` plus page URL), `license` as an `{"@id"}` node, `prov:wasAttributedTo`, `DataDownload`, the COAR access-level table, the Signposting table, the negotiation table | Closely, adapted from a project to a resource |
| 3 | dsp-repository `shared/fair/src/record_datacite.rs`, `record_dublin_core.rs` | The DataCite and Dublin Core field mapping for a record | Closely; parity is by contract, not by shared code (dsp-api is Scala) |
| 4 | *DaSCH Metadata 2.0 → DataCite 4.6 / OAI-PMH Mapping* v1.1 (2026-01-15), §2.3 Record | Resource type: `resourceTypeGeneral` from `typeOfData`, `resourceType` from the RDF class | Fully |
| 5 | dsp-api `ExportService.exportResourcesOai`, `MetadataRecord`, `FileLink` | Per-resource fact sources: dates, `typeOfData`, the file link, the description properties | For those facts only. Its hard-coded `accessRights` and `legalInfo` are **not** followed; this PRD derives both (US-4, Core Features) |

## Goals

Each goal has a stable ID; user stories and the implementation plan cite them.
G0 and G6 are the two top goals; every other goal, here and in the plan, descends from one of them.

- **G0** — Every page a DaSCH resource or value ARK resolves to is
  FAIR-assessable by machine, as the project page already is.

- **G1** (↑G0) — A machine following any resource or value ARK reads standards-shaped
  metadata and Signposting from the response, without executing JavaScript.
  (US-1, US-2, US-3)
- **G2** (↑G0) — Nothing is published beyond what an anonymous user may already see.
  (US-4)
- **G3** (↑G0) — The F-UJI score of a resource ARK rises measurably from its baseline,
  for resources of 0868 and 0803. (US-6)
- **G4** (↑G0) — A person's experience of the page is unchanged. (US-5)
- **G6** — The changes are maintainable within each repository's conventions.

## Core Features

### Where the response is produced

dsp-api answers `GET` and `HEAD` for the resource route. dsp-app's server
forwards `/resource/…` to dsp-api; dsp-api returns dsp-app's own `index.html`
with a block spliced into `<head>`, sets the response headers, and performs the
one negotiation step. If dsp-api fails or is slow, dsp-app's server serves its
own `index.html` unchanged. The ARK resolver and the registry templates do not
change.

### Query parameters change nothing

`?version=` and `?highlightValue=` do not change the metadata. `cite-as` is
always the unversioned resource ARK and the metadata describes the resource's
current state. A value ARK gets its resource's metadata.

### Representations

Two, served by dsp-api on the **API host** (not beside the page on the app
host), each at its own stable URL with its own media type:

- schema.org JSON-LD, `application/ld+json` — the same graph the page embeds.
- DataCite JSON, `application/vnd.datacite.datacite+json`.

No Turtle. For projects on a hard-coded list of those whose records DPE's
OAI-PMH holds, `describedby` also links DPE's `GetRecord` for `oai_datacite` and
`oai_dc`; these are linked, never redirected to.

### Whose view

All metadata is built as the anonymous user, whatever credentials the request
carries. A resource that is missing, deleted or not visible to anonymous users
emits nothing.

### What the metadata says

| Fact | Source | Notes |
|------|--------|-------|
| Identifier, `cite-as` | the resource's ARK (`arkUrl`), unversioned | Plus the page URL in `identifier`, as on the project page |
| Title | `rdfs:label` | |
| Description | the per-project description property the export uses (0803, 081C, 0868, 1612) | No generic property. Inconsistent across projects; accepted as the current state |
| Creators | resource `hasResourceAuthorship` and the file value's `hasAuthorship`, deduplicated → else the project's `defaultDataAuthorship` → else the project as an organisation | The last fallback in DataCite only (creator is mandatory there); schema.org then omits `creator`. Never `attachedToUser`. Authorship carries no ORCID, so no `author` link and no `prov:wasAttributedTo` creator entries |
| Licence | the file value's `hasLicense` → else the project's `dataLicense` → else none | The project's enabled-licence catalogue is never used. `PLACEHOLDER` on the file counts as no licence, so the chain falls through. `UNKNOWN`, `AI_GENERATED` and `PUBLIC_DOMAIN` are declarations without a licence URI: DataCite `rights` carries the label only, schema.org `license` and the `license` link are omitted, and there is no fallback. A project's custom licence uses its own recorded URI |
| Dates | created and published = `creationDate`; modified = `lastModificationDate` when present | DataCite `Created` / `Available` / `Updated`; `PublicationYear` from `creationDate` |
| Type | DataCite `resourceTypeGeneral` from `typeOfData` (Image, Text, Audiovisual, Sound; no file kind → `Dataset`), `resourceType` the RDF class; schema.org root always `Dataset` with the RDF class as `additionalType`; `dc:type` the DataCite general type | Mapping source §2.3 |
| Relations | `isPartOf` the project's ARK; `publisher` DaSCH | |
| File | one `DataDownload`: `contentUrl` the dsp-ingest `/original` URL the export emits; `encodingFormat`, `name`, `contentSize` from the asset sidecar, each only when recorded; `license` the resolved licence | Only when anonymous has V or higher on the file value. DataCite `formats`/`sizes` and `DC.format` from the same |
| Access level | Full Open Access when anonymous has V+ on the resource and on its file (or there is no file); otherwise Open Access with Restrictions | `isAccessibleForFree`, `conditionsOfAccess`, `DC.accessRights` with DPE's COAR URIs |

## Repository Impact

- **dsp-api** — the landing-page response (shell splice, `Link`, `Vary`, 303),
  the two representation endpoints, the per-resource metadata derivation under
  the anonymous view, and the tests.
- **dsp-app** — server configuration only: forward `GET`/`HEAD` `/resource/…` to
  dsp-api and fall back to the local `index.html` on failure. No application
  code changes.
- **ops-deploy** — possibly the upstream setting for that forwarding, per
  environment; to be confirmed in the plan.

## User Stories

"Landing route" is `GET`/`HEAD` on `/resource/{project}/{resource}` with any
query string. A resource is "visible" when it exists, is not deleted, and
anonymous users may see it.

### US-1: Embedded metadata

Serves: G1

As a harvester or FAIR assessor, I want the page an ARK resolves to to carry
the resource's metadata in its served HTML, so that I can read it without
executing JavaScript.

- REQ-1.1 (State-driven): While the resource is visible, the landing route shall
  return the dsp-app shell with exactly one `<script type="application/ld+json">`
  in `<head>`, holding a schema.org `Dataset` graph for the resource.
- REQ-1.2 (State-driven): While the resource is visible, the landing route shall
  include Dublin Core `<meta name="DC.*">` tags in `<head>`, `DC.accessRights`
  among them, stating the same facts as the DataCite representation.
- REQ-1.3 (State-driven): While the resource is visible, the landing route shall
  include `<link>` elements in `<head>` mirroring every Signposting relation of
  the `Link` header.
- REQ-1.4 (Ubiquitous): The JSON-LD shall give `identifier` as the ARK in a
  `PropertyValue` with `propertyID: "ARK"` plus the page URL, `license` as an
  `{"@id": …}` node, `isPartOf` as the project ARK, `publisher` as DaSCH, and
  `additionalType` as the resource's RDF class.
- REQ-1.5 (Ubiquitous): The landing route shall emit no key for a fact the data
  does not record, and no placeholder value.
- REQ-1.6 (Event-driven): When the request carries `?version=` or
  `?highlightValue=`, the landing route shall emit the same metadata as without
  them.

### US-2: Signposting

Serves: G1

As an assessor, I want typed links in the HTTP response, so that I find the
identifier, licence and descriptions from the headers alone.

- REQ-2.1 (State-driven): While the resource is visible, the landing route shall
  send a `Link` header with exactly one `cite-as`, the unversioned resource ARK.
- REQ-2.2 (State-driven): While the resource is visible, the `Link` header shall
  carry the `type` links `https://schema.org/Dataset` and
  `https://schema.org/AboutPage`.
- REQ-2.3 (State-driven): While the resource is visible, the `Link` header shall
  carry one `describedby` per representation, typed with its media type.
- REQ-2.4 (Optional): Where the resource's project is on the list of projects
  whose records DPE's OAI-PMH holds, the `Link` header shall also carry
  `describedby` links to DPE's `GetRecord` for `oai_datacite` and `oai_dc`,
  typed `application/xml`.
- REQ-2.5 (State-driven): While the resolved licence is exactly one URI, the
  `Link` header shall carry one `license` link to it.
- REQ-2.6 (Event-driven): When a `HEAD` request reaches the landing route, the
  response shall carry the same headers as the `GET` for the same URL.

### US-3: Representations and negotiation

Serves: G1

As a harvester, I want each machine-readable representation at its own stable
URL, and a redirect when I ask for one by `Accept`, so that I fetch exactly the
format I parse.

- REQ-3.1 (Event-driven): When a client requests the JSON-LD representation of a
  visible resource, dsp-api shall answer `200 application/ld+json` with the same
  graph the page embeds.
- REQ-3.2 (Event-driven): When a client requests the DataCite representation of
  a visible resource, dsp-api shall answer
  `200 application/vnd.datacite.datacite+json`, valid against DataCite's kernel-4
  JSON schema.
- REQ-3.3 (Ubiquitous): Each representation response shall carry
  `Link: <landing page>; rel="describes"`.
- REQ-3.4 (Event-driven): When the request's `Accept` prefers a supported
  representation's media type over HTML, the landing route shall answer
  `303 See Other` to that representation's URL.
- REQ-3.5 (Ubiquitous): Every response from the landing route shall carry
  `Vary: Accept`.
- REQ-3.6 (Ubiquitous): The landing route's HTML body shall not vary by
  `Accept`; the negotiation table in `machine-readable-metadata.md` is the
  acceptance specification, with "unknown shortcode" read as "resource not
  visible".

### US-4: Access rights

Serves: G2

As a data owner, I want restricted resources and files to stay unpublished, so
that the metadata leaks nothing anonymous users cannot see.

- REQ-4.1 (Ubiquitous): dsp-api shall build all landing-page and representation
  metadata as the anonymous user, regardless of the request's credentials.
- REQ-4.2 (Unwanted-behaviour): If the resource does not exist, is deleted, or
  is not visible to anonymous users, then the landing route shall return the
  unchanged shell with no metadata, no `Link` header and no 303.
- REQ-4.3 (Unwanted-behaviour): If the resource does not exist, is deleted, or
  is not visible to anonymous users, then the representation URLs shall answer
  `404 text/plain` with an empty body, identical in all three cases.
- REQ-4.4 (State-driven): While anonymous users hold less than V on the
  resource's file value, no representation shall contain a pointer to that file.
- REQ-4.5 (Ubiquitous): The access level shall be Full Open Access when
  anonymous users hold V or higher on the resource and on its file (or there is
  no file), and Open Access with Restrictions otherwise, emitted with DPE's COAR
  URIs.

### US-5: A person's page is unaffected

Serves: G4

As a person following an ARK, I want the page to load as it does today, so that
the metadata work never costs me the app.

- REQ-5.1 (Unwanted-behaviour): If dsp-api errors or does not answer within a
  short timeout, then dsp-app's server shall serve its own `index.html`
  unchanged.
- REQ-5.2 (Ubiquitous): The served shell shall be the currently deployed dsp-app
  build, differing only in the added `<head>` content.

### US-6: Measured

Serves: G3

As DaSCH, I want the change measured, so that the FAIR claim for resources
rests on assessor results rather than assertion.

- REQ-6.1 (process; fits no EARS pattern): F-UJI 3.5.0 is run against a resource
  ARK of 0868 (files with MIME types) and one of 0803, before the change
  (baseline) and after it on a deployment, each recorded with assessor version
  and target, as in the DPE assessment ledger. FAIR Champion is run by hand
  against the same resources.

## Constraints

- Access-rights rules apply from the first commit.
- No DOIs.
- The ARK resolver and the registry templates stay a plain redirect, unchanged.
- dsp-app stays a client-rendered SPA; its only change is server configuration.

## Success Criteria

- dsp-api tests cover: headers on `GET` and `HEAD`; the 303 and `Vary: Accept`
  table; a missing, deleted or restricted resource leaking nothing; a file
  anonymous may not fully access never being advertised; the DataCite JSON
  validating against DataCite's schema.
- F-UJI before/after recorded for resource ARKs of 0868 and 0803, and a FAIR
  Champion run by hand, as REQ-6.1 describes.

## Out of Scope

- Metadata of a specific version (`?version=`), and value-level descriptions.
- A Turtle representation, and linking dsp-api's knora-api RDF as `describedby`.
- A generic description property; ORCIDs for authorship.
- Changing the ARK resolver or registry templates; Angular SSR.
- DOIs and DataCite registration.
- Fixing `exportResourcesOai` (see Open Questions).

## Open Questions

1. **The record's two views disagree.** `exportResourcesOai` hard-codes
   `accessRights = "Full Open Access"` and the metadata's public-domain
   `legalInfo` (CC0, authorship "DaSCH") on every record, with retrieval checks
   skipped, so DPE's OAI record for a resource differs from this page in
   licence, access level and creators — and REQ-2.4 links that record.
   *Decided:* link it anyway, document the disagreement, and fix the export in a
   follow-up issue (same class of defect as DEV-7336).
2. **ADR placement.** This deviates from ADR-0005 — the representations live on
   the API host, and the page is served by dsp-api, outside the Access Area.
   *Decided:* no ADR; this PRD and its plan record the decision and the
   deviation.
3. **ARKs on non-production deployments.** DEV and STAGE emit the production
   resolver's ARK (`KNORA_WEBAPI_ARK_RESOLVER_URL`), so F-UJI's `F1-02D` fails
   there. *Decided:* measure on DEV and record `F1-02D` as a known residual, as
   DPE did; no resolver rewrite in this work.
