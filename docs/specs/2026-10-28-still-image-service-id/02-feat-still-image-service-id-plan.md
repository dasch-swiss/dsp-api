---
title: "feat: Expose the IIIF image service id on still image file values"
type: feat
date: 2026-10-28
author: "Dominique Steinbach"
status: reviewed
repository: dasch-swiss/dsp-api
repositories:
    - name: dsp-api
    path: /Users/dominiquesteinbach/workspaces/dev-7660-still-image-service/dsp-api
prd: 01-still-image-service-id-PRD.md
linear: DEV-7678
---

<!-- markdownlint-disable MD001 MD024 -->
<!-- The eng plan format requires `#### Phase N:` headings with `### <repo>` groups under each. -->

# feat: Expose the IIIF image service id on still image file values

## Overview

Add the computed complex-schema property
`knora-api:stillImageFileValueHasIIIFImageService` (`xsd:anyURI`, zero-or-one) to
`StillImageFileValue`. Its value is `{sipi.externalBaseUrl}/{shortcode}/{internalFilename}`,
the `id` Sipi reports in that image's `info.json`. It is absent for placeholder
assets. Clarify in the `knora-api` ontology that `dimX`/`dimY` are native-size
metadata, and update the Sipi docs. This plan covers A1 and A3 of the PRD.

## Problem Statement / Motivation

Clients build the IIIF service URL from `stillImageFileValueHasIIIFBaseUrl` plus
`fileValueHasFilename`, and some size their viewers from `dimX`/`dimY`. Under Sipi v10's
restricted view, a restricted user is served a smaller image, so tile and region
requests derived from the native size return 400s. See the PRD's Context section and
DEV-7660.

## Proposed Solution

Copy the existing `StillImageFileValueHasIIIFBaseUrl` property. It is a computed,
complex-only, Gravsearch-forbidden property, defined in four places:

1. Constant in `OntologyConstants.KnoraApiV2Complex`.
2. Property definition plus a `ZeroOrOne` cardinality on `StillImageFileValue` in
   `KnoraBaseToApiV2ComplexTransformationRules`.
3. Emission in the complex branch of `StillImageFileValueContentV2.toJsonLDValue`, skipped
   when `hasPlaceholderAsset`.
4. An entry in `GravsearchQueryChecker.forbiddenPredicates`.

A small pure helper on the `StillImageFileValueContentV2` companion
(`ValueMessagesV2.scala:2142`) builds the service id from `(externalBaseUrl, shortcode,
internalFilename)` with single-slash joins: `String` in, `String` out, no effect. It
drops a trailing slash on the base and keeps any path prefix. It does not go on the
sealed `FileValueContentV2` trait or into the shared `toJsonLDObjectMapInComplexSchema`
(`:2062-2078`), because `StillImageExternalFileValueContentV2` uses both. `fileValueAsUrl` is derived from the same helper
(`{serviceId}/full/max/0/default.jpg`) so the two cannot drift. For every
configuration the API can express today, its output is byte-identical.

**Unlike the template:** `StillImageExternalFileValue` also carries
`IIIFBaseUrl` (its own cardinality map and emission site). The new property is **not**
added there, because external IIIF is out of scope.

## Technical Considerations

- **Where it is emitted:** all complex-schema renderings of the value go through
  `StillImageFileValueContentV2.toJsonLDValue`: resource full and preview, single
  value, versions, full-text search and Gravsearch results (callers at
  `ResourceMessagesV2.scala:1133`, `:1259`). Emitting there covers every US-1 read path
  at once.
- **Simple schema:** it only calls `toJsonLDValueInSimpleSchema(fileUrl)`, and
  `KnoraBaseToApiV2SimpleTransformationRules` has no still-image extras. Nothing to do.
- **InstanceChecker:** `modules/test-e2e/.../e2e/InstanceChecker.scala:104-132`
  rejects properties without a cardinality on the class. The `ZeroOrOne` entry is
  mandatory. `ExactlyOne` would break placeholder responses (see the warning at
  `KnoraBaseToApiV2ComplexTransformationRules.scala:641-643`).
- **Gravsearch:** `checkStatement` applies `forbiddenPredicates` in both WHERE and
  CONSTRUCT (`GravsearchQueryChecker.scala:106-142`). One entry covers both, and the
  error is `Predicate <iri> cannot be used in a Gravsearch query` (`GravsearchException`,
  400).
- **Request parsing:** create/update file value bodies read only filename and
  legal-info fields (`ValueMessagesV2.scala:2007`, `:2143`). A client echoing the new
  property is not rejected, just as with `IIIFBaseUrl` today.
- **Base URL shape:** `AppConfig.Sipi.externalBaseUrl` is `protocol://host[:port]`
  (`AppConfig.scala:147-148`). It cannot carry a prefix or a trailing slash today, so
  the PRD's slash and prefix rules are pinned by unit tests on the helper.
- **How Sipi v10 builds `id`:** from the request, not from its config.
    - **Host:** `X-Forwarded-Host`, or the `Host` header when that is absent.
    - **Scheme:** `https` only when `X-Forwarded-Proto` is `https`, otherwise `http`.
    - **Path:** `/{prefix}/{identifier}`.
    - **Allowlist:** an optional `SIPI_PUBLIC_HOSTS` restricts the hosts it accepts.

  Sources, in the Sipi repo at v10.0.0: `src/server/rust/src/routes.rs:2038-2048` and
  `:2488-2513`, `config.rs:47-61`. `SIPI_EXTERNAL_HOSTNAME`/`_PORT` play no part. So the
  property equals `id` exactly when Sipi sees the public host and scheme that
  `sipi.externalBaseUrl` names. In production that is the reverse proxy's job.
- **The `info.json` test:** the e2e client reaches Sipi at `<local-ip>:<mapped-port>`
  (`SipiTestContainer.scala`), so the integration test sends
  `X-Forwarded-Host: 0.0.0.0:1024`, as a proxy would, and then asserts exact
  equality.
- **Filenames:** internal filenames are generated at ingest and need no
  percent-encoding. The helper passes them through unchanged.
- **Fixtures:** JSON-LD expected responses are compared by RDF-model equality, with
  `IIIF_BASE_URL` substituted (`modules/testkit/.../util/TestDataFileUtil.scala`).
  `ontologyR2RV2/knoraApiOntologyWithValueObjects.jsonld` is regenerated by
  `OntologyFormatsE2ESpec` and never hand-edited. No placeholder file value exists in
  `test_data/`, so absence is tested in `PlaceholderFileValueE2ESpec`, without adding
  shared data (see the learning below).
- **Release notes:** `CHANGELOG.md` is produced by release-please from Conventional
  Commit PR titles, so it is never edited by hand. The PR title `feat: ...` is the
  announcement.
- Conventions: `CONVENTIONS.md` (ASCII-only source, Scaladoc, comment rules, no
  "Knora" in prose).

## Implementation Phases

#### Phase 1: The image service id on still image file values

Covers PRD US-1 and US-2.

### dsp-api

- [ ] Add `StillImageFileValueHasIIIFImageService` to `OntologyConstants.KnoraApiV2Complex`, next to `StillImageFileValueHasIIIFBaseUrl` (`OntologyConstants.scala:839`)
- [ ] Define the property in `KnoraBaseToApiV2ComplexTransformationRules.scala`, near `:437`. It is an `XSD.ANYURI` datatype property, a subproperty of `KA.ValueHas`, with subject type `KA.StillImageFileValue`, an English label, and an `rdfs:comment` saying it names the IIIF image service of the file, grants no access, and that the served size comes from the service's `info.json`
- [ ] Register the new property in the property list (`KnoraBaseToApiV2ComplexTransformationRules.scala:865-882`)
- [ ] Add `KA.StillImageFileValueHasIIIFImageService -> ZeroOrOne` to `StillImageFileValueCardinalities` (`:656-660`) only. Leave `StillImageExternalFileValueCardinalities` (`:662-665`) unchanged
- [ ] Add `def serviceId(externalBaseUrl: String, shortcode: Shortcode, internalFilename: String): String` to the `StillImageFileValueContentV2` companion (`ValueMessagesV2.scala:2142`). It joins with single slashes, strips a trailing slash on the base and keeps a path prefix. Its Scaladoc states that the result equals Sipi's `info.json` `id` when Sipi sees the public host
- [ ] Derive `fileValueAsUrl` in `StillImageFileValueContentV2.makeFileUrl` (`ValueMessagesV2.scala:2106-2107`) as `{serviceId}/full/max/0/default.jpg`. The placeholder path via `fileUrlOrPlaceholder` stays as is
- [ ] In the complex branch of `StillImageFileValueContentV2.toJsonLDValue` (`ValueMessagesV2.scala:2109-2133`), emit the new key as `JsonLDUtil.datatypeValueToJsonLDObject(id, OntologyConstants.Xsd.Uri.toSmartIri)`, through `Option.when(!hasPlaceholderAsset)(...)`. Leave `StillImageExternalFileValueContentV2` unchanged
- [ ] Add `StillImageFileValueHasIIIFImageService` to `GravsearchQueryChecker.forbiddenPredicates` (`GravsearchQueryChecker.scala:174`)
- [ ] Create `StillImageFileValueContentV2Spec` in `modules/webapi/src/test/scala/org/knora/webapi/messages/v2/responder/valuemessages/`, in the `@RunWith(classOf[DspZTestJUnitRunner]) class ... extends ZIOSpecDefault` form of its siblings (`ValueContentV2Spec.scala`). Its helper suite is a table of cases: the plain base, a base with a trailing slash, a base with a path prefix, a prefix plus trailing slash, and an unmodified filename
- [ ] In `StillImageFileValueContentV2Spec`, add a `toJsonLDValue` suite. It uses `TestDataFactory.someProjectADM`, `AppConfig.layer`, `FileValueV2(..., "image/jp2")` and `StringFormatter.getInitializedTestInstance`. It asserts on the returned `JsonLDObject` map that:
    - the complex schema carries the property with `@type` `xsd:anyURI`;
    - a placeholder (`PlaceholderIri.instance.value`) has no property but keeps `stillImageFileValueHasIIIFBaseUrl`;
    - the simple schema has no property;
    - `fileValueAsUrl` equals a literal `{base}/0001/{file}/full/max/0/default.jpg`, built in the test without the helper.
- [ ] Create `GravsearchQueryCheckerSpec` in `modules/webapi/src/test/scala/org/knora/webapi/messages/util/search/gravsearch/`. It calls `GravsearchQueryChecker.checkStatement` with a `StatementPattern` whose predicate is `IriRef` of the new property, `ApiV2Complex` and `GravsearchTypeInspectionResult(Map.empty)`. It asserts that `GravsearchException` says "cannot be used in a Gravsearch query", for both `inConstructClause = false` and `true`, and that `stillImageFileValueHasIIIFBaseUrl` is still rejected
- [ ] Extend `PlaceholderFileValueE2ESpec` (`modules/test-e2e/.../it/v2/PlaceholderFileValueE2ESpec.scala:66-73`). Parse the created resource IRI from the POST response, read it back with `TestApiClient.getJsonLd(uri"/v2/resources/$resIri")`, and assert that `stillImageFileValueHasIIIFImageService` is absent and `stillImageFileValueHasIIIFBaseUrl` is present
- [ ] Add a case to `KnoraSipiIntegrationV2ITSpec`. It reads a still image created in the spec and fetches `{stillImageFileValueHasIIIFImageService}/info.json` from Sipi with the header `X-Forwarded-Host: 0.0.0.0:1024` (no user, as the spec's other still image fetches do). It parses the body with `JsonLDUtil.parseJsonLD` or a JSON parser and asserts that `id` equals the property byte-for-byte. If `TestSipiApiClient` cannot set a header, add an overload to `TestSipiApiClient.getFile` in `modules/testkit`
- [ ] In `KnoraSipiIntegrationV2ITSpec`, assert that a past version of the image value carries the same property value. Use `TestApiClient.getJsonLdDocument(uri"/v2/values/$resourceIri/$valueUuid", user, addVersionQueryParam(timestamp))` (pattern in `ValuesEndpointsE2ESpec.scala:656`)
- [ ] In `KnoraSipiIntegrationV2ITSpec`, assert that `/v2/resourcespreview/$resourceIri` carries the same property value (pattern in `ValuesEndpointsE2ESpec.scala:175`)
- [ ] Add the property, with `IIIF_BASE_URL/{shortcode}/{filename}` per value, to every non-placeholder still image file value in `searchR2RV2/FulltextSearchWithImage.jsonld` (7 values; read by `SearchEndpointsGetSearchE2ESpec.scala:131`)
- [ ] Add the property to `valuesE2EV2/get-still-image-file-value-response.jsonld` (read via the name template in `ValuesEndpointsE2ESpec.scala:640-646`)
- [ ] Add the property to `searchR2RV2/bookWithIncomingPagesWithAllRequestedProps.jsonld` (read by both the simple- and complex-schema Gravsearch specs)
- [ ] Add the property to `resourcesR2RV2/NarrenschiffFirstPage.jsonld`
- [ ] Grep `test_data/` for `stillImageFileValueHasDimX` and add the property to any still image value read by a spec and not covered above. `resourcesR2RV2/NarrenschiffFirstPageExpanded.jsonld` and `responseCheckerR2RV2/NarrenschiffFirstPageWrong.jsonld` stay unchanged: the first is read by no spec, and the second is only the "received" side of a check that must fail on a different value count
- [ ] Regenerate `ontologyR2RV2/knoraApiOntologyWithValueObjects.jsonld`. Run `just test-e2e --test_filter='.*OntologyFormatsE2ESpec.*'` once: on a mismatch the spec overwrites the file and fails. The `git diff` shows only the new property and its cardinality, and the simple knora-api ontology fixtures are unchanged. Run it again; it passes
- [ ] Run `bazel test //modules/webapi:test --test_filter='.*(StillImageFileValueContentV2Spec|GravsearchQueryCheckerSpec).*'`; it passes
- [ ] Run `just test-unit`; it passes
- [ ] Run `just test-e2e`; it passes (the full suite, so that every reader of a changed fixture runs)
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: dimX/dimY as metadata, and the docs

Covers PRD US-3 and US-4. The ontology text and the docs are one reviewable change,
because both state the same client contract.

### dsp-api

- [ ] Rewrite the `rdfs:comment` of `StillImageFileValueHasDimX` and `DimY` (`KnoraBaseToApiV2ComplexTransformationRules.scala:425-435`). The comment says the value is the native pixel size of the stored master recorded at ingest, not the size a given user is served, and is not for drawing, tiles, regions or overlays; use the image service's `info.json`
- [ ] Rewrite the `rdfs:comment` of `StillImageFileValueHasIIIFBaseUrl` (`:437-442`) to name `knora-api:stillImageFileValueHasIIIFImageService` as the preferred source for still image file values. No `owl:deprecated`
- [ ] Regenerate `ontologyR2RV2/knoraApiOntologyWithValueObjects.jsonld`. Run `just test-e2e --test_filter='.*OntologyFormatsE2ESpec.*'` once; on a mismatch it overwrites the file and fails. The `git diff` shows only these three comments
- [ ] Rewrite "File URLs in API v2" in `docs/06-sipi/sipi-and-dsp-api.md:28-34`. It describes `stillImageFileValueHasIIIFImageService` (append `/info.json`), says the property names the service but grants no access, says `info.json` is the source of the served size, and says `dimX`/`dimY` are native-size metadata. Drop the advice to construct IIIF URLs from the base URL and the filename for `StillImageFileValue`, and keep the existing wording correct for `StillImageExternalFileValue`
- [ ] Check `docs/03-endpoints/api-v2/` (e.g. `editing-values.md`, `reading-and-searching-resources.md`, `query-language.md`) and `docs/05-internals/design/api-v2/sipi.md` for still image file value or Gravsearch-forbidden-property wording, and update every place that contradicts Phase 1
- [ ] Run the `fix-markdownlint` skill on the changed docs; it reports no issues
- [ ] Run `just test-e2e --test_filter='.*OntologyFormatsE2ESpec.*'`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Once the release is on stage, fetch `{stillImageFileValueHasIIIFImageService}/info.json` for a public still image, and confirm that `id` equals the property. Scheme and host must match. If they differ, the proxy in front of Sipi is not forwarding the public host or `https`, and it needs an ops-deploy fix | Developer | after ship | Stage only carries the change after a merge and a deploy, which the owner decides |

## Acceptance Criteria

- [ ] A complex-schema still image file value with a real asset carries `stillImageFileValueHasIIIFImageService` = `{sipi.externalBaseUrl}/{shortcode}/{internalFilename}` as `xsd:anyURI`
- [ ] The value equals the `id` of Sipi v10's `info.json` for that image on the local stack
- [ ] Single-value, version, preview, full-text and Gravsearch reads carry the same property
- [ ] A placeholder still image has no such property
- [ ] `StillImageExternalFileValue` responses and cardinalities are unchanged
- [ ] The complex knora-api ontology declares the property with zero-or-one cardinality on `StillImageFileValue`; the simple ontology and simple-schema values do not contain it
- [ ] Gravsearch rejects the property in WHERE and in CONSTRUCT
- [ ] `fileValueAsUrl`, `dimX`/`dimY` and `stillImageFileValueHasIIIFBaseUrl` values are unchanged in every existing fixture
- [ ] The DimX/DimY/IIIFBaseUrl comments carry the A3 wording
- [ ] The Sipi docs describe the property and no longer tell clients to construct still image IIIF URLs
- [ ] The PR title is a `feat:` Conventional Commit mentioning DEV-7678, so release-please announces it

## Dependencies & Risks

- **Sipi image version in e2e:** integration tests use the `latest` Sipi image unless
  `SIPI_USE_EXACT_VERSION=true`. The `info.json` `id` test assumes v10, which is on
  `main` since #4404. If a stale local image fails the test, rebuild with
  `just docker-build-sipi-image`.
- **What Sipi reports as `id` depends on the proxy:** Sipi v10 takes host and scheme
  from `X-Forwarded-Host`/`Host` and `X-Forwarded-Proto` (see Technical
  Considerations). The PRD's "API and Sipi share one public base URL" constraint holds
  only if the proxy in front of Sipi forwards the public host and `https`. The
  integration test pins dsp-api's side. The deployed side is checked after ship (H1).
  If the test's `id` differs from the property despite the header, stop and report
  back; do not relax the assertion to a path suffix.
- **Fixture ripple:** adding a property changes every expected response with a still
  image (learning: shared fixtures ripple). The grep deliverable in Phase 1 catches
  files outside the known list.
- No data migration, no knora-base change, no ontology version bump: the property is
  computed and lives only in the knora-api transformation rules.

## Success Metrics

- The PRD's US-1 to US-3 criteria are each covered by at least one automated test
  named in Phase 1 or 2. US-4 is checked in review.
- The fixture diff touches no `fileValueAsUrl`, `dimX`, `dimY` or
  `stillImageFileValueHasIIIFBaseUrl` value. That is the baseline that the helper
  refactor is output-neutral.

## References

- PRD: `01-still-image-service-id-PRD.md`
- Template property: `modules/webapi/src/main/scala/org/knora/webapi/messages/OntologyConstants.scala:839`,
  `.../ontologymessages/KnoraBaseToApiV2ComplexTransformationRules.scala:437-442, 656-665, 865-882`,
  `.../valuemessages/ValueMessagesV2.scala:2042, 2106-2133, 2162-2200`,
  `.../search/gravsearch/GravsearchQueryChecker.scala:106-174`
- Config: `modules/webapi/src/main/scala/org/knora/webapi/config/AppConfig.scala:128-149`
- Tests: `modules/test-e2e/src/test/scala/org/knora/webapi/it/v2/KnoraSipiIntegrationV2ITSpec.scala`,
  `.../it/v2/PlaceholderFileValueE2ESpec.scala:66-82`, `.../e2e/InstanceChecker.scala:104-132`,
  `modules/testkit/src/main/scala/org/knora/webapi/testservices/TestSipiApiClient.scala`
- Learning: `dasch-specs/learnings/best-practices/test-fixture-isolation-shared-data-scope.md`
- Linear: DEV-7678 (parent DEV-7660)
