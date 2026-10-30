---
title: "Still image file values say where their IIIF image service is"
date: 2026-10-28
author: "Dominique Steinbach"
status: reviewed
linear: DEV-7678 (parent context: DEV-7660)
repositories:
  - dsp-api
---

# Still image file values say where their IIIF image service is

## Context

Sipi v10 (dsp-api#4404, on `main` since `cafe3c1ca`) serves a size-restricted
image as an image of its own at the restricted size RW × RH. `info.json`
reports that size (`width`/`height`, `maxWidth`/`maxHeight`, `sizes`), and any
output larger than RW × RH is a 400.

The cross-repo picture is in
[DEV-7660](https://linear.app/dasch/issue/DEV-7660/still-image-contract-with-sipi-v10-state-issues-and-streamlining):
its ADR draft "The IIIF image service is the rendering contract for still
images" (issue comment) and the Linear PRD
[Still images rendered from the IIIF image service](https://linear.app/dasch/document/prd-still-images-rendered-from-the-iiif-image-service-sipi-v10-e0007021dcb7).
This PRD covers only the dsp-api part: A1 and A3 there. A2, the IIIF manifest,
is out of scope because no client in our stack uses the manifest. A4, the Sipi
v10 bump and `fileValueAsUrl` as `full/max`, is done in #4404.

Today no API property states which description a client should draw an image
from. The API hands out pieces, and the client assembles the service URL by
convention:

| Property (complex schema) | Value today | Problem |
| --- | --- | --- |
| `knora-api:stillImageFileValueHasIIIFBaseUrl` | `{sipi.externalBaseUrl}/{shortcode}` | Only half a service id; clients append `fileValueHasFilename` themselves |
| `knora-api:fileValueHasFilename` | `x.jp2` | Same |
| `knora-api:stillImageFileValueHasDimX/DimY` | Native size, e.g. 2613×3505 | Looks like drawing data. It isn't: a restricted user is served 382×512 |

Clients that draw from the stored native size request tiles and regions that
don't exist for a restricted user, and get 400s or misaligned images. That is
the failure DEV-7660 describes for dsp-app.

## Goals

- **#G0** — A client draws a still image from the IIIF image service the API
  names, without building the service URL or using native dimensions.
- **#G1** (↑G0) — Every still image file value names its IIIF image service in
  one explicit property. (US-1, US-2)
- **#G2** (↑G0) — Schema and docs say that `dimX`/`dimY` are native-size
  metadata, and that the property names a service but grants no access. (US-3,
  US-4)

## Decisions

- **D1 — The value is the IIIF image service id**, i.e. the base URI, not the
  `info.json` URL and not a nested service object. The service id is what IIIF
  Presentation 3 manifests and established IIIF publishers reference; clients
  append `/info.json`, and other IIIF requests (e.g. `{id}/full/!w,h/0/default.jpg`)
  build on the same value. (Open question 1 of the draft, settled 2026-10-28.)

## Core Features

### A1 — Image service id on the file value

A new property on `StillImageFileValue` in the complex schema,
`knora-api:stillImageFileValueHasIIIFImageService` (`xsd:anyURI`):

- **Value:** `{sipi.externalBaseUrl}/{shortcode}/{internalFilename}`, where the
  shortcode is that of the project the resource belongs to.
- **Canonical form:** it equals the `id` Sipi reports in that image's
  `info.json`: no trailing slash and no `/info.json` suffix. Like every other
  Sipi URL dsp-api builds, it assumes `sipi.externalBaseUrl` has no trailing
  slash; a path prefix in it is kept.
- **Optional:** the property is absent when the file value has a placeholder
  asset, as there is no service to point at. The ontology declares it
  optional (zero or one).
- **Wherever the file value appears:** every complex-schema response that
  contains a `StillImageFileValue` carries it: resource reads, single-value
  reads, value versions, full-text and Gravsearch results. Resource previews
  (`/v2/resourcespreview`) carry no values, so they have nothing to carry it on.
- **Computed, not stored:** like `stillImageFileValueHasIIIFBaseUrl`. No data
  migration.
- **Not queryable:** like `stillImageFileValueHasIIIFBaseUrl`, Gravsearch rejects
  it in a WHERE clause.
- **Simple schema:** unchanged. The value there is still `fileValueAsUrl`, and the
  simple `knora-api` ontology gets no new property.
- **Meaning:** the property names the service. It grants no access: Sipi decides
  per request what the user gets, may answer 401/403, and may serve a smaller
  image than `dimX` × `dimY`. Its `rdfs:comment` says so. A client learns the
  size it is served from `info.json` (`width`/`height`, `maxWidth`/`maxHeight`,
  `sizes`); the API promises no other signal, and this PRD relies on no IIIF
  Authorization services.

### A3 — `dimX`/`dimY` are metadata

- The `rdfs:comment` of `stillImageFileValueHasDimX`/`DimY` says: the native
  pixel size of the stored image file; a user may be served a smaller image, so
  do not size requests, tiles or regions from it; take the served size from the
  `info.json` of the image service, when present.
- `stillImageFileValueHasIIIFBaseUrl` stays, because published clients use it.
  Its comment points to the A1 property as the preferred source. It is not
  marked `owl:deprecated` in this change; removal is a separate decision.
- The Sipi and v2 docs describe the A1 property and drop the wording that tells
  clients to construct IIIF URLs themselves. The release notes announce the
  new property and the `dimX`/`dimY` clarification.

## Example: the file value before and after

Source: `GET /v2/values/{resourceIri}/{valueUuid}` for `http://rdfh.ch/0001/a-thing-picture`
(fixture `test_data/generated_test_data/valuesE2EV2/get-still-image-file-value-response.jsonld`).
The fixture's `IIIF_BASE_URL` placeholder is shown as `https://iiif.dasch.swiss`;
the ARK URLs and the enclosing resource are omitted.

**Today:**

```json
"knora-api:hasStillImageFileValue": {
  "@id": "http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q",
  "@type": "knora-api:StillImageFileValue",
  "knora-api:valueHasUUID": "goZ7JFRNSeqF-dNxsqAS7Q",
  "knora-api:valueCreationDate": { "@value": "2012-11-21T16:50:38Z", "@type": "xsd:dateTimeStamp" },
  "knora-api:attachedToUser": { "@id": "http://rdfh.ch/users/91e19f1e01" },
  "knora-api:hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|RV knora-admin:UnknownUser",
  "knora-api:userHasPermission": "M",
  "knora-api:fileValueHasFilename": "B1D0OkEgfFp-Cew2Seur7Wi.jp2",
  "knora-api:fileValueAsUrl": {
    "@value": "https://iiif.dasch.swiss/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2/full/max/0/default.jpg",
    "@type": "xsd:anyURI"
  },
  "knora-api:stillImageFileValueHasDimX": 512,
  "knora-api:stillImageFileValueHasDimY": 256,
  "knora-api:stillImageFileValueHasIIIFBaseUrl": {
    "@value": "https://iiif.dasch.swiss/0001",
    "@type": "xsd:anyURI"
  }
}
```

A client today concatenates `stillImageFileValueHasIIIFBaseUrl`, `/`,
`fileValueHasFilename` and `/info.json`, and is tempted to size the viewer from
`dimX`/`dimY`.

**After this PRD:**

```json
"knora-api:hasStillImageFileValue": {
  "@id": "http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q",
  "@type": "knora-api:StillImageFileValue",
  "knora-api:valueHasUUID": "goZ7JFRNSeqF-dNxsqAS7Q",
  "knora-api:valueCreationDate": { "@value": "2012-11-21T16:50:38Z", "@type": "xsd:dateTimeStamp" },
  "knora-api:attachedToUser": { "@id": "http://rdfh.ch/users/91e19f1e01" },
  "knora-api:hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|RV knora-admin:UnknownUser",
  "knora-api:userHasPermission": "M",
  "knora-api:fileValueHasFilename": "B1D0OkEgfFp-Cew2Seur7Wi.jp2",
  "knora-api:fileValueAsUrl": {
    "@value": "https://iiif.dasch.swiss/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2/full/max/0/default.jpg",
    "@type": "xsd:anyURI"
  },
  "knora-api:stillImageFileValueHasIIIFImageService": {
    "@value": "https://iiif.dasch.swiss/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2",
    "@type": "xsd:anyURI"
  },
  "knora-api:stillImageFileValueHasDimX": 512,
  "knora-api:stillImageFileValueHasDimY": 256,
  "knora-api:stillImageFileValueHasIIIFBaseUrl": {
    "@value": "https://iiif.dasch.swiss/0001",
    "@type": "xsd:anyURI"
  }
}
```

What changes:

- **New:** `stillImageFileValueHasIIIFImageService`. The client opens
  `{value}/info.json` and draws what it describes.
- **Unchanged values:** `dimX`/`dimY` and `stillImageFileValueHasIIIFBaseUrl` keep
  their values; only their ontology comments change (A3).
- **Placeholder asset:** `stillImageFileValueHasIIIFImageService` is absent.

## User Stories

### US-1: A still image names its service

- WHEN a client reads a resource with a still image in the complex schema, THEN
  the file value SHALL carry `stillImageFileValueHasIIIFImageService` with value
  `{sipi.externalBaseUrl}/{shortcode}/{internalFilename}`.
- WHEN `sipi.externalBaseUrl` contains a path prefix, THEN the value SHALL keep
  the prefix.
- WHEN the value is requested with `/info.json` appended against the local stack
  with Sipi v10, THEN the `id` in the response SHALL equal the property's value
  (integration test).
- WHEN the same file value is read as a single value, as a past version, in a
  preview, or in full-text or Gravsearch results, THEN it SHALL carry the same
  property.
- WHEN the file value has a placeholder asset, THEN the property SHALL be
  absent.

### US-2: Schema and query rules

- WHEN a client reads the complex `knora-api` ontology, THEN
  `StillImageFileValue` SHALL have the property with cardinality zero-or-one.
- WHEN a client reads the simple `knora-api` ontology or a simple-schema value,
  THEN neither SHALL contain the property.
- WHEN a Gravsearch query uses the property in its WHERE clause, THEN the API
  SHALL reject it as it rejects `stillImageFileValueHasIIIFBaseUrl`.

### US-3: `dimX`/`dimY` say what they are

- WHEN a client reads the complex `knora-api` ontology, THEN
  `stillImageFileValueHasDimX` and `DimY` SHALL carry the A3 comment, and
  `stillImageFileValueHasIIIFBaseUrl` SHALL point to the A1 property.

### US-4: Docs say what clients do

- The Sipi and v2 docs SHALL describe the A1 property, its "names, does not
  grant" meaning, and `info.json` as the source of the served size, and SHALL
  NOT tell clients to construct IIIF URLs from `iiifBaseUrl` and the filename.
- The release notes SHALL announce the property.

## Constraints

- **Non-breaking:** adding an optional property is non-breaking for JSON-LD
  clients. `stillImageFileValueHasIIIFBaseUrl` and existing values stay.
- **One owner of what is served:** the API does not compute or embed the served
  size or `info.json` (rejected in the ADR draft).
- **The API and Sipi share one public base URL:** the value is right only while
  `sipi.externalBaseUrl` is the host and prefix Sipi itself reports in
  `info.json`. US-1's integration test pins that for the local stack.
- **Release coupling:** none. A1 and A3 can ship independently of Sipi and
  dsp-app.

## Success Criteria

- The US-1 to US-3 criteria are covered by automated tests; US-4 is verified
  in review.

## Out of Scope

- The IIIF manifest (`GET /v2/resources/iiifmanifest/{resourceIri}`): no client
  in our stack uses it. Its invalid image service (bare base URL as `id`,
  `profile: level1`) belongs with the external-IIIF topic.
- `StillImageExternalFileValue` and external IIIF URLs.
- Region preview URLs.
- Sipi, dsp-js and dsp-app changes, including IIIF Authorization services.
- Removing or deprecating `stillImageFileValueHasIIIFBaseUrl`.

## Open Questions

None.

## Appendix: where the change lands (for the plan)

| Area | File |
| --- | --- |
| File value serialisation | `messages/v2/responder/valuemessages/ValueMessagesV2.scala` (`StillImageFileValueContentV2`) |
| Ontology constant | `messages/OntologyConstants.scala` |
| knora-api ontology, comments, cardinality | `messages/v2/responder/ontologymessages/KnoraBaseToApiV2ComplexTransformationRules.scala` |
| Gravsearch computed properties | `messages/util/search/gravsearch/GravsearchQueryChecker.scala` |
| Fixtures | `test_data/generated_test_data/**` with still image file values, incl. `ontologyR2RV2/knoraApiOntologyWithValueObjects.jsonld` |
| Integration test | `modules/test-e2e/.../KnoraSipiIntegrationV2ITSpec.scala` |
| Docs | `docs/06-sipi/sipi-and-dsp-api.md`, `docs/03-endpoints/api-v2/` |
