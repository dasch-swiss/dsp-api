---
title: "Design: FAIR metadata and Signposting for resource landing pages"
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories:
  - dsp-app
  - ops-deploy
linear: DEV-7420
---

# Design: FAIR metadata and Signposting for resource landing pages

Shared by every slice of the [plan](01-feat-resource-fair-landing-metadata-plan.md).

## Decisions taken (2026-10-06, owner)

- **`?version=`** changes `cite-as` to the versioned ARK and the metadata to that version's state (v2 reads
  already support `?version`). Representation URLs carry the same `?version=`.
- **`?highlightValue=`** gets the resource's metadata unchanged; `cite-as` stays the resource ARK (versioned
  when `?version=` is present). No value-level description is invented.
- **Representations:** schema.org JSON-LD, DataCite JSON and Turtle (the same schema.org graph). All three
  are `describedby` targets and `303` candidates.
- **License:** only the license recorded on the resource's file value. A resource without a file, or a file
  without a license, emits no `license` anywhere. The project's `dataLicense` is not used as a fallback.
- **Edge wiring is deferred** (H1). Both routing candidates (a dsp-app nginx `location /resource/` proxy, or a
  Traefik router on the app host) send the request to dsp-api, so S1–S3 do not depend on the choice.
- **Parity with DPE is by contract, not by code.** `shared-fair` is Rust; the shapes below are checked by
  tests against DPE's documented examples, not shared.

## Shape

A new slice `slice/fair/` (domain + service) with endpoints under `slice/api/v3/resources/fair/`:

1. **Resolve.** `{shortcode}/{resourceId}[?version]` is parsed into a typed `ResourceIri` + optional
   `VersionDate` at the boundary (`docs/development/dsp-api-iri-handling.md`). Malformed input is a `400`.
   `{resourceId}` is the plain resource id as dsp-app's URL carries it (`lklK7rVuVOmpBZYWrF8o-g`). The ARK
   resolver has already stripped the check digit and un-escaped `=`, so a check-digit form is rejected.
   `?highlightValue=` (landing page only) is accepted and never affects the metadata.
2. **Read as anonymous, always.** The resource is read through `ReadResourcesService` with the anonymous
   user, whatever credentials the request carries. The output therefore never depends on the requester, is
   cacheable, and cannot leak what a logged-in viewer may see. Not found, not visible to anonymous, and
   deleted all collapse into one `NotPublic` outcome with an identical response.
3. **Build one graph.** `ResourceFairGraph` holds the resolved facts once. Every output is a projection of
   it, mirroring `shared-fair`'s `RecordGraph`.
4. **Project.** Pure writers produce JSON-LD, Turtle (Jena parse of the emitted JSON-LD, so the two cannot
   disagree), DataCite JSON, Dublin Core meta and the link set. Each writer lands in the slice that first
   emits it.

## Field mapping

Source → graph, reviewed against `shared-fair` `record_datacite.rs` / `record_dublin_core.rs` and
`docs/src/dpe/machine-readable-metadata.md`:

| Graph fact | Source in dsp-api | Notes |
| --- | --- | --- |
| `ark` / `cite-as` | `StringFormatter.resourceIriToArkUrl(iri, version)` | versioned when `?version` |
| `pageUrl` | configured dsp-app public base + `/resource/{shortcode}/{resourceId}` | new config key; absolute |
| `title` | `ReadResourceV2.label` | |
| `description` | project description property (today `ExportService.findDescriptionProperty`) | extract and reuse; omit when absent |
| `creators` | `resourceAuthorship`, else the file value's `authorship` | `DaSCH` → Organization, else Person; DataCite-only fallback `DaSCH` as DPE does |
| `orcids` | an authorship string that *is* an ORCID URI | no ORCID field exists; nothing is parsed out of free text |
| `dateCreated` / `dateModified` | `creationDate` / `lastModificationDate` | `publicationYear` = creation year |
| `license` | file value `licenseIri`, mapped to its URI | none otherwise |
| `copyrightHolder` | file value `copyrightHolder` | |
| `generalType` | file value class (as `ExportService.typeOfDataOf`) | Image / Audiovisual / Sound / Text, else `Dataset` |
| `accessLevel` | anonymous `userPermission` on resource and file value | see Access below |
| `file` (DataDownload) | dsp-ingest original URL + sidecar (`ExportService.fileLinkOf`) | **only when fully open**; never for external IIIF |
| `isPartOf` | project ARK (`ark:/72163/1/{shortcode}`): the DPE project page | |
| `additionalType` | resource class IRI | recorded fact |

### File-value edge cases

The file-derived facts (`file`, `license`, `copyrightHolder`, `generalType`) come from exactly one file value:

- **More than one file value:** "at most one file value" is an assumption in `ExportService`, not an
  enforced invariant, and `collectFirst` over a `Map` is order-dependent. The builder fails closed: it logs
  and omits every file-derived fact.
- **External IIIF (`StillImageExternalFileValueContentV2`):** its `internalFilename` is a placeholder, so
  `fileLinkOf` as written would build an invented dsp-ingest URL. No `DataDownload` is emitted for it.
  License and `generalType` (Image) still apply.

`ExportService.exportResourcesOai` hardcodes `accessRights = "Full Open Access"` and `LegalInfo.publicDomain`.
Those are placeholders, and this work must not copy them: parity is with DPE's *shapes*, never with the
export's placeholder values.

## schema.org shape (JSON-LD)

Following the DPE contract:

- Root `@type: Dataset` (ADR-0005: assessors test `Dataset` properties; `distribution` is a `Dataset`
  property), with `additionalType` = resource class IRI.
- `identifier`: exactly two entries, `{"@type":"PropertyValue","propertyID":"ARK","value":<ark>}` plus the
  page URL as a bare string.
- `license`: `{"@id": <uri>}`, never a string; key absent when none.
- `creator` / `prov:wasAttributedTo` (DaSCH + creators with an ORCID), `publisher` DaSCH, `isPartOf` the
  project, `includedInDataCatalog` as DPE emits it.
- `distribution`: one `DataDownload` at the root (`contentUrl`, `name`, `encodingFormat`, `contentSize`,
  the file's own `license`), each field omitted when the source lacks it, never guessed.
- `isAccessibleForFree` / `conditionsOfAccess` / `DC.accessRights` (COAR) from the access level.

## Signposting

The `Link` header and the `<link>` mirror are built from one list, in DPE's order: `cite-as` (exactly once)
→ `type` `https://schema.org/Dataset`, `https://schema.org/AboutPage` → `describedby` JSON-LD
(`application/ld+json`), DataCite (`application/vnd.datacite.datacite+json`), Turtle (`text/turtle`) →
`license` (0 or 1) → `author` (one per ORCID). Delimiters in hrefs are percent-encoded at serialization;
CR/LF never reach a header. Each representation answers `Link: <page>; rel="describes"`.

## Access

Applies from the first slice; DEV-7336 is the same class of defect on the DPE side.

| Anonymous can see | Access level | File advertised |
| --- | --- | --- |
| resource `V` and file value `V`, or no file | Full Open Access (`c_abf2`) | yes |
| resource visible, file value `RV` | Open Access with Restrictions (`c_16ec`) | no |
| resource visible, file value not visible | Metadata only Access (`c_14cb`) | no |
| resource not visible / missing / deleted | `NotPublic` | no metadata, no links at all |

Only values the anonymous read returns can feed the graph, so nothing that is not already public can enter it.

## Technical Considerations

- **Representation endpoints (proposed; confirm in the H2 review):** `GET`/`HEAD`
  `/v3/resources/{shortcode}/{resourceId}/metadata.jsonld`, `…/metadata.datacite.json`, `…/metadata.ttl`,
  each with `?version=`, on the API's public host (`externalKnoraApiBaseUrl`). They stay stable whatever the
  edge decision is. They are public: use the v3 public base, with no shared-envelope error variant
  (learning: `shared-tapir-error-envelope-leaks-exception-messages.md`). Every non-public outcome returns
  one identical `404`.
- **New config:** `app.fair.dsp-app-base-url` (absolute page URLs; per env via ops-deploy, H3). The
  resource page lives on another host, so it cannot be derived from the API's own host.
- **New HTTP infrastructure:** no `Link`, `Vary`, `303` or HTML-serving endpoint exists in dsp-api today.
  Headers go through tapir `header[String]`. `HEAD` must return the same status and headers as `GET`
  (learning: `sipi-cache-auto-creation-head-request-empty-response.md`).
- **Head splicing:** S4 introduces one splice point before `</head>`. S5 and S6 each contribute their own
  part of the head fragment through it, so neither depends on the other.
- **JSON-LD in `<script>`:** `<`, `>` and `&` are written as JSON unicode escapes before splicing (ADR-0005
  consequence). JSON-LD is built as a zio-json AST, never by string concatenation.
- **Size:** a resource has at most one file, so no byte budget like DPE's 4 MB `hasPart` cap is needed.
- **Performance:** S4 puts dsp-api on the hot path of every resource deep link, without a resource read. S5
  and S6 add one single-resource read plus one sidecar lookup per request; it must stay that.
- **SPARQL:** none new expected. If any is needed, use the `sparql"…"` interpolator.

## Alternative Approaches Considered

- **Angular SSR in dsp-app**: rejected. dsp-app is pure CSR with no SSR scaffolding, and the headers and `303`
  would still need server code.
- **Point resource ARKs at a DPE record page**: rejected. It would give code-level parity with `shared-fair`,
  but DPE knows only the corpus export (3 of 85 committed projects carry records), is as fresh as its last
  deploy, and has no dsp-api permission model.
- **Read with the requester's credentials**: rejected. The output would vary by viewer, making shared
  caching unsafe and the "leaks nothing" guarantee depend on every caller.
- **Reuse the existing `/v2/resources` JSON-LD as the JSON-LD representation**: rejected as the primary
  representation, because it is knora-api vocabulary, not schema.org, and assessors do not read it.
- **Render representations inline on the page URL by `Accept`**: rejected by ADR-0005. Representations get
  their own URLs; the page negotiates only via `303`.
- **Ship the landing response as one phase**: rejected. Bundling the edge plumbing with all metadata puts the
  riskiest change (dsp-api on every resource page view) and every visible behaviour in one release, and
  nothing moves an assessor score until all of it lands.
