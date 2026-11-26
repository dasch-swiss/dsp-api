---
title: "fix: Accept the placeholder license IRI in the bulk data import"
date: 2026-11-26
author: Nora Ammann
status: implemented
linear: DEV-8079
repositories: []
---

# fix: Accept the placeholder license IRI in the bulk data import

## Context

The v3 bulk data import (`/v3/projects/{projectIri}/data-imports`) fails on a file value with
`"knora-api:hasLicense": {"@id": "urn:dasch:placeholder"}`:

```text
Transformation failed: Failed to transform RDF: Couldn't parse IRI: urn:dasch:placeholder
```

The v2 create path accepts the same value. DSP-TOOLS `ingest-xmlupload` uses the bulk import (DEV-8033), so it fails
on test servers where DSP-TOOLS fills missing legal info with the sentinel.

**Root cause.** Stage 1 of `OntologyTransformer` (`rewritingSink`,
`modules/webapi/src/main/scala/org/knora/webapi/slice/ontology/OntologyTransformer.scala:1228`) calls
`sf.toSmartIri(uri).toInternalSchema` on every URI node. `SmartIriImpl` validates through
`Iri.validateAndEscapeIri` (`modules/webapi/src/main/scala/dsp/valueobjects/Iri.scala:90`), whose `UrlValidator`
accepts only `http`/`https`. A `urn:` IRI throws. Literal sentinels (`hasCopyrightHolder`, `hasAuthorship`,
`fileValueHasFilename`) are not URI nodes, so only `hasLicense` fails.

The doc comment of `rejectPlaceholderLegalMetadata` (`OntologyTransformer.scala:998-1004`) records this
rejection as intended. It is wrong: the rejection ignores `allow-placeholder` and breaks parity with v2.

## Proposed Solution

In `rewritingSink`, keep the object node unchanged when the triple's predicate is
`OntologyConstants.KnoraApiV2Complex.HasLicense` (`OntologyConstants.scala:659`) and the object equals
`PlaceholderIri.instance.value`. Rewrite every other node as before. The existing stage-2 gate
`rejectPlaceholderLegalMetadata` already rejects a URI-node sentinel when `allow-placeholder` is off,
so production behaviour stays a rejection, now with the policy message.

No other change is necessary downstream:

1. SHACL `data-shapes.ttl:806` requires only `sh:nodeKind sh:IRI` for `hasLicense`. The sentinel passes.
2. The validator placeholder scan runs only when `allow-placeholder` is off.
3. v2 stores the license as IRI `<urn:dasch:placeholder>` (`FileValueV2.licenseIri`, `LicenseIri.PLACEHOLDER`).
   The import then stores the same triple.

## Technical Considerations

- Keep the exemption narrow: only the object of `hasLicense`. A global sentinel exemption in `rewriteUri` also
  lets the sentinel through as a subject, predicate, link target or list node. The validator placeholder scan
  checks objects only, and it does not run when `allow-placeholder` is on
  (`ProjectMigrationImportValidator.scala:88`). Such a payload would then reach the triplestore. Other non-HTTP
  IRIs keep failing in stage 1, as before.
- `rewritingSink` applies the rewrite in `triple` and in `quad`. Apply the exemption in both, through one
  shared helper.
- Rewrite the doc comment of `rejectPlaceholderLegalMetadata`: the license sentinel now reaches stage 2 and is
  caught there as an IRI object.
- Out of scope: the leftover `urn:dasch:placeholder` in `allowedCopyrightHolders` after a failed import.
  DSP-TOOLS causes it (DEV-8059).
- Out of scope: the bulk import does not check enabled licenses or allowed copyright holders the way v2
  `LegalInfoService` does (`LegalInfoService.scala:65-110`). This is a separate parity gap. Record it as a
  follow-up issue if confirmed.
- Out of scope: the DEV-6939 note in the dasch-specs bulk-import implementation index states the stage-1
  rejection as fact. It becomes wrong when this fix lands.

## Implementation Approach

TDD in `modules/webapi/src/test/scala/org/knora/webapi/slice/ontology/OntologyTransformerSpec.scala`, suite
`fileValuesStage2` (around line 2434, next to the existing placeholder tests). Reuse `fileValueJsonLd`,
`filenameInner`, `runTransformStage2`, `runTransformStage2Failure`, `expectedStage2SingleValue`, `messageOf`.

- [x] Add failing test: a `hasLicense` `{"@id": "urn:dasch:placeholder"}` with a real filename passes through as
  `knora-base:hasLicense <urn:dasch:placeholder>` when `allow-placeholder` is on. Catches a regression of the
  stage-1 rewrite.
- [x] Add failing test: a file value with the sentinel in all four fields (filename, copyright holder,
  authorship, license) emits `knora-base:hasLicense <urn:dasch:placeholder>` when `allow-placeholder` is on,
  under `assertNoInteraction`. This is the DSP-TOOLS payload. It catches a break in the
  `convertPlaceholderFileValue` branch.
- [x] Add failing test: the placeholder license with `allow-placeholder` off fails with a message that contains
  `legal metadata` and not `Couldn't parse IRI`. Catches a gap in the production policy gate.
- [x] Add test: the sentinel as an object of a predicate other than `hasLicense` (for example a link target)
  still fails in stage 1 with `Couldn't parse IRI`. Catches a widening of the exemption to other positions.
- [x] Run `just test-unit`; the three failing tests fail for the expected reason, and all other tests pass.
- [x] Change `rewritingSink` in `OntologyTransformer.scala:1228` to keep the `hasLicense` sentinel object
  unchanged, in `triple` and in `quad`.
- [x] Update the doc comment of `rejectPlaceholderLegalMetadata` (`OntologyTransformer.scala:998-1004`) to
  describe the IRI-object case as handled there.
- [x] Add test: a sentinel `hasLicense` inside a JSON-LD `@graph` (the `quad` path of `rewritingSink`) passes
  stage 1 unchanged. Catches a divergence of the `quad` path from the `triple` path.
- [x] Add a parity fixture (`placeholder_legal_info`) to `BulkImportParityE2ESpec`: a still image with the
  sentinel as license, copyright holder and authorship, in both payloads. Catches divergence between the bulk
  import and the v2 create path for placeholder legal info.
- [x] Run `just test-unit`; all tests pass, output is clean.
- [x] Run `just fmt`, then `just check`; both pass.
- [x] Commit `fix(ontology): accept the placeholder license in the bulk data import (DEV-8079)`;
  `just commit-lint` passes.

## Acceptance Criteria

- [x] With `allow-placeholder` on, the bulk import stores `knora-base:hasLicense <urn:dasch:placeholder>` for a
  file value, the same as v2.
- [x] With `allow-placeholder` off, the bulk import rejects the sentinel license with the placeholder-policy
  message.
- [x] The sentinel in any position other than the `hasLicense` object still fails in stage 1.
- [x] All existing `OntologyTransformerSpec` tests pass unchanged.

## Dependencies & Risks

- Low risk: one predicate-and-object check in stage 1. Every other URI keeps the same rewrite.
- `test_ingest_xmlupload_parity.py` in dsp-tools verifies the end-to-end parity after a release.

## Success Metrics

DSP-TOOLS `ingest-xmlupload` succeeds on a test server for a file without legal info.

## References

- Linear: DEV-8079. Related: DEV-8033, DEV-8059.
- `OntologyTransformer.scala:110` (stage 1), `:998` (legal-metadata gate), `:1056` (placeholder file value),
  `:1228` (`rewritingSink`).
- `modules/webapi/src/main/scala/org/knora/webapi/slice/common/Iris.scala:20` (`PlaceholderIri`).
- `ValueMessagesV2.scala:2035` (`FileValueV2.placeholderFields`, v2 gate).
