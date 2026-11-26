---
plan: docs/specs/2026-11-26-bulk-import-placeholder-license/01-fix-bulk-import-placeholder-license-plan.md
target_repo: /Users/noraammann/Documents/Cloned_GitHub/dsp-api
base_commit: 0c8159eb8032035bbc1d05aa9e872afd2ef4beca
branch: feature/dev-8079-dsp-api-bulk-data-import-rejects-the-legal
started: 2026-11-26
problem: >
  The v3 bulk data import rejects a file value whose hasLicense is the placeholder sentinel
  urn:dasch:placeholder, while the v2 create path accepts it when allow-placeholder is on.
  DSP-TOOLS ingest-xmlupload uses the bulk import and fails on test servers.
symptoms:
  - "Transformation failed: Failed to transform RDF: Couldn't parse IRI: urn:dasch:placeholder"
status: complete
---

# Execution Journal: 01-fix-bulk-import-placeholder-license-plan

## Chunk queue

1. Tests: four OntologyTransformerSpec cases (license on, all four fields on, license off, other position).
2. Fix: keep the hasLicense sentinel object unchanged in rewritingSink; update the gate doc comment.

## Chunks

| id | status   | commit(s) | summary                                                                               | blocker |
|----|----------|-----------|---------------------------------------------------------------------------------------|---------|
| 1  | complete | dd46c151c | Four OntologyTransformerSpec tests; red for the three positive/off cases, guard green | none    |
| 2  | complete | dd46c151c | rewritingSink keeps the hasLicense sentinel object; gate doc comment updated          | none    |
| 3  | complete | 0d4c7c29e | Review fixes: @graph (quad path) test, shorter stage-1 comment                        | none    |
| 4  | complete | 92c4a221b | Parity fixture placeholder_legal_info in both payloads; e2e parity green              | none    |

## Deferrals

- none

## Deviations

- The red run used `bazel test //modules/webapi:test --test_filter='.*OntologyTransformerSpec.*'`,
  the per-chunk narrowing of `just test-unit`. The full `just test-unit` runs at the quality check.
- The chunk commits (dd46c151c, 0d4c7c29e, 92c4a221b) and the plan commit were squashed into one commit, as the repo's
  one-commit-per-PR policy requires. The SHAs in `## Chunks` are the pre-squash ones.
- A mutation that widened the exemption to every predicate made the guard test fail; the file was restored.

## Side findings

- `ValueMessagesV2.scala` (`FileValueV2.placeholderFields`) and `LegalInfoService.scala` call the create-path
  gate the single enforcement point of `allow-placeholder`. The bulk import has its own gates
  (`rejectPlaceholderLegalMetadata`, `ProjectMigrationImportValidator` scan) since commit 192e9546c. Pre-existing;
  a follow-up can share one definition of the sentinel-bearing fields or correct the comments.
- Review finding refuted: a sentinel `hasLicense` on a non-file subject with `allow-placeholder` off does not
  persist. `ProjectMigrationImportValidator.validate` scans every object position before upload.

## Closeout

- root_cause: Stage 1 of OntologyTransformer converts every URI node to a SmartIri. SmartIri validation
  (`Iri.validateAndEscapeIri`) accepts only http(s), so the IRI-valued license sentinel `urn:dasch:placeholder`
  failed in every environment. The literal sentinels were not affected.
- investigation: A doc comment and the DEV-6939 index note described the rejection as intended, without a
  reason. It contradicted the documented allow-placeholder behaviour. A global sentinel exemption was rejected:
  it would let the sentinel through as a link target or subject.
- solution: `rewritingSink` keeps the sentinel unchanged only as the object of knora-api:hasLicense, in the
  triple and quad paths. The stage-2 legal-metadata gate rejects it when allow-placeholder is off.
- prevention: Five OntologyTransformerSpec tests: license on, all four fields on, license off, other position
  still fails, @graph path. One BulkImportParityE2ESpec fixture with placeholder legal info. Mutation checks
  confirmed that the guard, @graph and parity tests fail on regression.
