---
plan: 02-feat-still-image-service-id-plan.md
target_repo: dasch-swiss/dsp-api
base_commit: 35429e03d6733eb912a2e2a7ea03451f09822073
branch: feature/dev-7678-expose-the-iiif-image-service-id-on-still-image-file-values
started: 2026-10-28
status: complete
---

# Execution journal: IIIF image service id on still image file values

## Problem

Clients build the IIIF service URL of a still image from `stillImageFileValueHasIIIFBaseUrl`
plus `fileValueHasFilename`, and some size their viewers from `dimX`/`dimY`. Under Sipi v10's
restricted view a restricted user is served a smaller image, so requests derived from the
native size fail. dsp-api exposes the service id directly and documents `dimX`/`dimY` as
native-size metadata (DEV-7678, parent DEV-7660).

## Repos

| repo | path | branch | base_commit | merge_strategy | pr | status |
| --- | --- | --- | --- | --- | --- | --- |
| dsp-api | /Users/dominiquesteinbach/workspaces/dev-7660-still-image-service/dsp-api | feature/dev-7678-expose-the-iiif-image-service-id-on-still-image-file-values | 35429e03d | squash | https://github.com/dasch-swiss/dsp-api/pull/4408 | shipped |

## Phases

| phase | phase_base | status |
| --- | --- | --- |
| 1 | 35429e03d | reviewed |
| 2 | f67be944c | reviewed |

## Chunk queue

| id | goal | files | mode |
| --- | --- | --- | --- |
| 1.1 | Property constant, definition, cardinality, helper, emission, Gravsearch ban | OntologyConstants, KnoraBaseToApiV2ComplexTransformationRules, ValueMessagesV2, GravsearchQueryChecker | inline |
| 1.2 | Unit specs: StillImageFileValueContentV2Spec, GravsearchQueryCheckerSpec | new webapi test files | worker |
| 1.3 | E2E/IT: PlaceholderFileValueE2ESpec, KnoraSipiIntegrationV2ITSpec, TestSipiApiClient | test-e2e, testkit | worker |
| 1.4 | Expected-response fixtures | test_data/generated_test_data | worker |
| 1.5 | Regenerate the knora-api ontology fixture, run the suites | test_data ontology fixture | inline |
| 2.1 | DimX/DimY/IIIFBaseUrl comments, regenerate fixture | transformation rules, ontology fixture | inline |
| 2.2 | Sipi and API v2 docs | docs/ | inline |

## Chunks

| id | status | commits | summary | blocker |
| --- | --- | --- | --- | --- |
| 1.1 | complete | 0d6ccdf03 | Constant, property, ZeroOrOne cardinality, serviceId helper, complex emission (not for placeholders), Gravsearch ban | none |
| 1.4 | complete | 0d6ccdf03 | 10 values added across 4 expected-response fixtures; committed with 1.1 so e2e stays green | none |
| 1.2 | complete | ade21dfdd | StillImageFileValueContentV2Spec (helper table, rendering) and GravsearchQueryCheckerSpec; break-check went red | none |
| 1.3 | complete | 8ed5be872, f67be944c | Placeholder has no service id; info.json id equals the property (X-Forwarded-Host/Proto); past version keeps its own id; TestSipiApiClient.getFile with headers. The first run never executed the Sipi spec (MutableTestIri rejected the UUID); fixed in review, 24 tests ran and passed. resourcespreview test dropped: that endpoint returns no values | none |
| 1.5 | complete | 50269aef6 | knora-api ontology fixture regenerated: only the new property and its maxCardinality 1 restriction; 9 OntologyFormats tests ran and passed | none |
| 1.R | complete | e67852f88, f67be944c | Phase 1 review (consistency, code-simplicity, scala-zio, dune). Fixed: MutableTestIri blocker, info.json parsed as plain JSON, serviceId no longer strips a trailing slash (matches every other Sipi URL), Gravsearch ban comment, unit spec map check | none |
| 2.1 | complete | 15a4a0fee | DimX/DimY/IIIFBaseUrl comments; fixture regenerated (only the three comments); 9 OntologyFormats tests ran and passed | none |
| 2.2 | complete | bca832ee7 | "File URLs in API v2" in the Sipi guide rewritten; API v2 endpoint docs and the internal Sipi design doc carry no contradicting wording | none |
| 2.R | complete | 72fff761c | Phase 2 review (consistency, code-simplicity): no blockers; wording tightened, "when present" for placeholders; "regions" kept, since IIIF region coordinates are relative to the info.json size | none |

## Side findings

- Bazel only fetches outside the command sandbox (Unknown host inside it).
- A bazel e2e run whose server cannot bind its port exits 0 with `OK (0 tests)`, and bazel caches that pass. Check the test log's count and use `--nocache_test_results` after such a run. A stray `dsp-api-pr-4404` compose stack held the ports and was stopped (not removed) with the user's OK.
- Two concurrent `just fmt` runs hung the license-header step with ~1,600 idle processes; run one at a time.
- zio-test's `assertTrue` macro crashes expanding a private accessor of a nested case class; bind the value first.
- bookWithIncomingPagesWithAllRequestedProps.jsonld is read by the simple- and complex-schema Gravsearch specs; both get a complex-schema response (schema comes from the request header).

## Deferrals

- DUNE-002: `ReadResourcesServiceLive` builds the region-preview thumbnail base as `{externalBaseUrl}/{shortcode}/{filename}` independently of `serviceId`. Region previews are out of scope for DEV-7678.
- DUNE-001: `ARCH-MAP.md` fingerprints are stale for the touched components; a map refresh is repo-wide work, not part of this change.

## Closeout

- Suites: `just test-unit` 3086 tests passed; `just test-e2e` 1022 of 1023 passed, the one failure (`ValueHasXmlOnValueWriteE2ESpec`, client timeout on a 6000-tag standoff POST) fails identically on origin/main cafe3c1ca on this machine; `just check` passes.
- Human action H1 (after ship): on stage, confirm the info.json `id` equals the property, scheme and host included.
