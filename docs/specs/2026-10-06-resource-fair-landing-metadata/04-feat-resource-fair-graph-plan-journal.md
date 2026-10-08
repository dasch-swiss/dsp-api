---
plan: docs/specs/2026-10-06-resource-fair-landing-metadata/04-feat-resource-fair-graph-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: fce4b249b
branch: feature/dev-7420-resource-fair-landing-metadata
started: 2026-10-07
problem: >
  S2 of DEV-7420: build one metadata graph per resource, read as the anonymous user, and project it into
  schema.org JSON-LD, Turtle and DataCite JSON in the shapes the DPE project page uses, so later slices can serve
  them as representations and embed them in the resource page.
status: complete
---

# Execution Journal: 04-feat-resource-fair-graph-plan

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `slice/export/api/service/ExportService.scala`, new `slice/export/fair/AssetDownloadLinks.scala`, `ExportApiModule.scala` | — | "Extract the dsp-ingest download link…" | helper builds the link for a given file value; `ExportServiceSpec` green unchanged | `ExportService` § fileLinkOf / findAssetInfo | — |
| 2 | new `slice/export/fair/{ResourceFairGraph,ResourceFairGraphBuilder}.scala`, `StringFormatter` (project ARK), `ExportApiModule.scala`, specs | 1 | "Read the resource…", "Add `ResourceFairGraph`…", "Derive `generalType`…", "omit every file-derived fact…", "Advertise a file only…", "Tests (moved from S1)…", license/multi-file/IIIF/text-value/placeholder tests | anonymous read; publish-nothing mapping; graph facts per the design's field mapping; file only via `AssetAccess` Grant | design § Field mapping, § Access; `AssetPermissionsResponder.getAssetAccess`; `KnoraProject.dataLicense`; `LicenseRepo` | — |
| 3 | new `slice/export/fair/SchemaOrgJsonLd.scala`, spec + goldens | 2 | "Add the schema.org JSON-LD writer…", "Shape test…", golden JSON-LD | DPE shapes for identifier/license/distribution/prov; arrays for repeated props | dsp-repository `docs/src/dpe/machine-readable-metadata.md` | — |
| 4 | new `slice/export/fair/Turtle…`, spec | 3 | "Add the Turtle writer…", "Turtle writer runs with no network" | Jena parse of the JSON-LD with an inline context | `slice/common/jena/ModelOps.scala` | — |
| 5 | new `slice/export/fair/DataCiteJson.scala`, spec + golden, maybe `MODULE.bazel` | 2 | "Add the DataCite JSON writer…", "JSON Schema validator…", "Validate the DataCite JSON…", agreement test | kernel-4 JSON; validated or, if a test dep can't be pinned cleanly, a required-fields test | dsp-repository `shared/fair/src/record_datacite.rs` | — |

## Chunks

| id | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- |
| 1 | complete | 3f2611a56 | `AssetDownloadLinks` extracted from `ExportService`, link per file value, export output unchanged | none |
| 2 | complete | 5ca20ad06 | `ResourceFairGraph` + builder: anonymous read, publish-nothing rules, facts per field mapping, file only on `AssetAccess` Grant; `projectIriToArkUrl` | none |
| 3 | complete | 7ee1a8507 | schema.org JSON-LD writer in DPE shapes, goldens | none |
| 4 | complete | dc993c8b9, 0ea819b2b | Turtle writer (Jena parse of the JSON-LD), deterministic no-network test | none |
| 5 | complete | 05cb96c41, 7c07852cc | DataCite JSON writer, schema-validated; validator declared as a direct test dep | none |
| 6 | complete | eb04b9aeb | review fixes: honest access level, no file for versions, `License`-based rights, untyped unknown creators, real schema.org context | none |
| 7 | complete | (agreement test commit) | cross-format agreement and no-placeholder test | none |

## Deferrals

- Agreement on real incunabula resources → S3 E2E (checkbox in `04-feat-resource-fair-graph-plan.md`, marked deferred;
  S3 already carries the real-data access cases)

## Side findings

- `LicenseRepo` is not in the app-level layer graph; `LegalInfoService.findAvailableLicenseByIdAndShortcode` resolves a
  license (all built-in licenses, placeholder hidden).

## Closeout

- root_cause: no dsp-api code assembled what may be published about a resource, as anonymous, in the vocabularies
  FAIR assessors read.
- investigation: the first S1 attempt re-derived the asset policy (`AssetAccess.from`) and a license table; both
  were replaced by the existing models after owner review. The security review found a resource with a hidden file
  reported as full open (the anonymous read drops invisible values), fixed by checking whether the class carries a
  file. The Scala review found the Turtle inline context differed from schema.org's real one (`http://` vocab, no
  date typing); the real context is now vendored and resolved locally.
- solution: `ResourceFairGraphBuilder` → `ResourceFairGraph` → `SchemaOrgJsonLd`, `SchemaOrgTurtle`, `DataCiteJson`;
  `AssetDownloadLinks` shared with the OAI export; `License.spdxId`; `StringFormatter.projectArkUrl`.
- prevention: mutation-checked file rule; goldens; DataCite schema validation; cross-format agreement and
  no-placeholder tests; a no-fetch document loader.
