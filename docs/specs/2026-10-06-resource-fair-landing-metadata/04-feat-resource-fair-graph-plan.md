---
title: "S2: The resource graph and the representation writers"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: implemented
repositories: []
linear: DEV-7420
---

# S2: The resource graph and the representation writers

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Field mapping, schema.org shape).

**Needs:** S1. **Visible change:** none.

The link-set builder and the Dublin Core writer are not here: they land in S5 and S6, the slices that first emit
them.

## Implementation Phases

### Phase 1: The resource graph and the representation writers

- [x] Read the resource through `ReadResourcesService.getResourcesWithDeletedResource` as
    `KnoraSystemInstances.Users.AnonymousUser` (whatever the request carries, never `skipRetrievalChecks`); only
    `ForbiddenException` / `NotFoundException`, a deleted resource, and a version before creation mean "publish
    nothing" — every other failure propagates
- [x] Extract the dsp-ingest download link and sidecar lookup (`fileLinkOf`, `findAssetInfo`) out of
    `ExportService` into a small helper both use, built for a given file value, with `ExportService` output
    unchanged
- [x] Add `ResourceFairGraph` and its builder from that read, reading only the allow-listed facts of the
    field-mapping table (never serialising `values` wholesale: they can carry restricted file details and, via
    region previews read as the system user, facts about images anonymous cannot see): root `license`
    and `copyrightHolder` from the project's `dataLicense` / `dataCopyrightHolder`, creators from
    `resourceAuthorship` else the project's `defaultDataAuthorship`
- [x] Derive `generalType` from the single file value's class only, never from a text value
- [x] In the builder, omit every file-derived fact when the resource has more than one file value
- [x] Advertise a file only for a single file value whose `AssetPermissionsResponder.getAssetAccess(AnonymousUser)`
    grants the original (the existing `AssetAccess.from` policy); emit no `DataDownload` for an external IIIF file
    value; the access level counts the resource and every file value
- [x] Tests (moved from S1): forbidden/not found/deleted/before-creation publish nothing; a triplestore failure
    propagates; the requesting user is anonymous even with credentials; `RV` file → restricted, no download; two
    file values, any `RV` → restricted, none advertised; two `V` file values → full open, none advertised
- [x] Add the schema.org JSON-LD writer (zio-json AST) with `identifier`, `license`, `distribution` (carrying the
    file value's own license) and `prov:wasAttributedTo` in the DPE shapes; repeated properties always arrays
- [x] Add the Turtle writer by parsing the emitted JSON-LD with Jena, with the `@context` inlined so no remote
    fetch happens
- [x] Add the DataCite JSON writer (kernel 4: creators with fallback, titles, dates, rights from the project's
    data license, `IsPartOf` project ARK, formats, `resourceTypeGeneral` from `generalType`)
- [x] Add a JSON Schema validator as a test dependency in `MODULE.bazel` (e.g. `com.networknt:json-schema-validator`)
    and vendor DataCite's kernel-4 JSON schema under the test resources
- [x] Agreement test: JSON-LD, Turtle and DataCite agree on ARK, title, license, creators and the file pointer, on
    constructed graphs (unit tests have no triplestore)
- [ ] (deferred to S3) The same agreement on real incunabula resources
- [x] Golden tests (`GoldenTest`) for the JSON-LD and DataCite of one open, one restricted-view and one versioned
    incunabula resource
- [x] Shape test: `identifier` (exactly two entries) and `license` (`{"@id": …}`) match the examples in
    dsp-repository's `docs/src/dpe/machine-readable-metadata.md`
- [x] Test: the root license is the project's data license, the download's license is the file value's, and a
    project without a data license emits no root `license`
- [x] Test: a resource with two file values emits no file-derived fact
- [x] Test: an external IIIF file value never yields a dsp-ingest URL
- [x] Test: a resource with a text value and no file value gets `generalType` `Dataset`, not Text
- [x] Test: no output ever contains a placeholder (`MISSING`, `CALCULATED`), the internal app URL, or a file URL
    for a non-open file
- [x] Test: the Turtle writer runs with no network access
- [x] Validate the DataCite JSON output against the kernel-4 schema in a test
- [x] Run `bazel test //modules/webapi:test`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Outcome

S2 landed in commits `3f2611a56`..`eb04b9aeb` plus the agreement test. The review (security, Scala/ZIO, simplicity,
consistency) found no critical issue; fixed in `eb04b9aeb`: a file-carrying resource whose file anonymous cannot see,
or whose original is withheld, is restricted rather than full open; a versioned request describes no file; licenses
come from the `License` model instead of a second table; creators get no guessed type; Turtle uses schema.org's real
(vendored) context. Deviations from the tasks above: the Turtle writer resolves the vendored real context instead of
an inline one; DataCite keeps `resourceType` (the 4.3 schema requires it); `FairGraphSources` stays as the unit-test
seam (the project, legal-info and asset-permission services have no in-memory doubles); an external IIIF file makes
the resource restricted, since no asset decision exists for it. Deferred to S3: agreement on real incunabula data.
