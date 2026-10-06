---
title: "S2: The resource graph and the representation writers"
type: feat
date: 2026-10-06
author: "Raitis Veinbahs"
status: draft
repositories: []
linear: DEV-7420
---

# S2: The resource graph and the representation writers

Part of the [plan](01-feat-resource-fair-landing-metadata-plan.md); design in
[02](02-resource-fair-landing-metadata-design.md) (Field mapping, schema.org shape).

**Needs:** S1. **Visible change:** none.

The link-set builder and the Dublin Core writer are not here: they land in S5 and S6, the slices that first
emit them.

## Tasks

- [ ] Add `ResourceFairGraph` and its builder from the public view, per the field-mapping table
- [ ] In the builder, omit every file-derived fact when the resource has more than one file value
- [ ] In the builder, emit no `DataDownload` for an external IIIF file value
- [ ] Add the schema.org JSON-LD writer (zio-json AST) with `identifier`, `license`, `distribution` and
    `prov:wasAttributedTo` in the DPE shapes
- [ ] Add the Turtle writer by parsing the emitted JSON-LD with Jena
- [ ] Add the DataCite JSON writer (kernel 4: creators with fallback, titles, dates, rights with SPDX,
    `IsPartOf` project ARK, formats, `resourceTypeGeneral` from `generalType`)
- [ ] Agreement test: for every incunabula fixture resource, JSON-LD, Turtle and DataCite agree on ARK,
    title, license, creators and the file pointer
- [ ] Golden tests for the JSON-LD and DataCite of one open, one restricted-view and one versioned
    incunabula resource
- [ ] Shape test: `identifier` (exactly two entries) and `license` (`{"@id": …}`) match the examples in
    dsp-repository's `docs/src/dpe/machine-readable-metadata.md`
- [ ] Test: no `license` key when the file value has no license, even when the project has a `dataLicense`
- [ ] Test: a resource with two file values emits no file-derived fact
- [ ] Test: an external IIIF file value never yields a dsp-ingest URL
- [ ] Test: no output ever contains a placeholder (`MISSING`, `CALCULATED`) or a file URL for a non-open file
- [ ] Validate the DataCite JSON output against DataCite's kernel-4 JSON schema in a test
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Slice review: adversarial review of this slice's commits; verified findings fixed before merge
