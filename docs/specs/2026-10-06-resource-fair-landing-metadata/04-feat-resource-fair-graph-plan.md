---
title: "S2: The resource graph and the representation writers"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: reviewed
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

- [ ] Add `ResourceFairGraph` and its builder from the public view, per the field-mapping table: root `license`
    and `copyrightHolder` from the project's `dataLicense` / `dataCopyrightHolder`, creators from
    `resourceAuthorship` else the project's `defaultDataAuthorship`
- [ ] Derive `generalType` from the single file value's class only, never from a text value
- [ ] In the builder, omit every file-derived fact when the resource has more than one file value
- [ ] In the builder, emit no `DataDownload` for an external IIIF file value or a file that is not Full Open
- [ ] Add the schema.org JSON-LD writer (zio-json AST) with `identifier`, `license`, `distribution` (carrying the
    file value's own license) and `prov:wasAttributedTo` in the DPE shapes; repeated properties always arrays
- [ ] Add the Turtle writer by parsing the emitted JSON-LD with Jena, with the `@context` inlined so no remote
    fetch happens
- [ ] Add the DataCite JSON writer (kernel 4: creators with fallback, titles, dates, rights from the project's
    data license, `IsPartOf` project ARK, formats, `resourceTypeGeneral` from `generalType`)
- [ ] Add a JSON Schema validator as a test dependency in `MODULE.bazel` (e.g. `com.networknt:json-schema-validator`)
    and vendor DataCite's kernel-4 JSON schema under the test resources
- [ ] Agreement test: for every incunabula fixture resource, JSON-LD, Turtle and DataCite agree on ARK, title,
    license, creators and the file pointer
- [ ] Golden tests (`GoldenTest`) for the JSON-LD and DataCite of one open, one restricted-view and one versioned
    incunabula resource
- [ ] Shape test: `identifier` (exactly two entries) and `license` (`{"@id": …}`) match the examples in
    dsp-repository's `docs/src/dpe/machine-readable-metadata.md`
- [ ] Test: the root license is the project's data license, the download's license is the file value's, and a
    project without a data license emits no root `license`
- [ ] Test: a resource with two file values emits no file-derived fact
- [ ] Test: an external IIIF file value never yields a dsp-ingest URL
- [ ] Test: a resource with a text value and no file value gets `generalType` `Dataset`, not Text
- [ ] Test: no output ever contains a placeholder (`MISSING`, `CALCULATED`), the internal app URL, or a file URL
    for a non-open file
- [ ] Test: the Turtle writer runs with no network access
- [ ] Validate the DataCite JSON output against the kernel-4 schema in a test
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts
