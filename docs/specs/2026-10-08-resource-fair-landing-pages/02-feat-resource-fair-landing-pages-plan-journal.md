---
plan: docs/specs/2026-10-08-resource-fair-landing-pages/02-feat-resource-fair-landing-pages-plan.md
target_repo: /Users/raitisveinbahs/work/2-dsp-api
base_commit: 5f4f820d8
branch: feature/dev-7420-resource-landing-pages
started: 2026-10-08
problem: >
  Resource and value ARKs resolve to dsp-app's /resource/{project}/{resource},
  a client-rendered SPA shell that is identical for every route and Accept and
  carries no machine-readable metadata, so F-UJI and harvesters find nothing on
  it. dsp-api must serve that route (forwarded by dsp-app's nginx) as the shell
  with schema.org JSON-LD, Dublin Core meta and Signposting spliced in, plus
  JSON-LD and DataCite representations, all built under the anonymous view.
status: in-progress
---

# Execution Journal: 02-feat-resource-fair-landing-pages-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | 5f4f820d8 | feature/dev-7420-resource-landing-pages | squash | in-progress | <https://github.com/dasch-swiss/dsp-api/pull/4385> |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@5f4f820d8 | 1 |
| 2 | reviewed | dsp-api@e1cfdaae8 | 1 |
| 3 | reviewed | dsp-api@e8a6b8cfa | 1 |
| 4 | reviewed | dsp-api@698c62e76 | 1 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | slice/fair/ports/*.scala, slice/fair/domain/ResourceFacts.scala (model only), config/AppConfig.scala, application.conf | — | "1.1 (ports + DTOs)", "1.3 ResourceFacts", "4.1 FairConfig" (pulled forward: 1.6 needs C1) | webapi compiles; four port traits with fair-owned DTOs; FairConfig projected with C1-C4 defaults and startup validation | ADR-0011 decisions 2,6,7; AppConfig.scala:19-37,259,262-323,351-360 | — |
| 1.2 | dsp-api | slice/export/api/service/ExportService.scala, new pure object in slice/resources/domain, unit spec | — | "1.2 extract pure ExportService parts" | ExportServiceSpec + golden unchanged and green; file selection returns ReadValueV2 over file values only | ExportService.scala:146-197 | — |
| 1.3 | dsp-api | (split, see 1.3a/1.3b) slice/fair/domain/ResourceFacts.scala (derive), slice/fair/domain/service/ResourceFactsBuilder.scala, test: slice/fair/ports/*InMemory.scala, ResourceFactsSpec, ResourceFactsBuilderSpec | 1.1 | "1.4"-"1.16" (rules + unit tests, tests first) | all rule tests green; derive is pure | plan TD2-TD11 | — |
| 1.4 | dsp-api | `<Port>Live` adapters in slice/resources, slice/admin, slice/ontology, assets provider packages | 1.1, 1.2 | "1.1 (Live adapters)" | adapters compile; anonymous read with withDeleted=false, skipRetrievalChecks=false, NotFound/Forbidden -> None | ReadResourcesServiceLive.scala:39-50; ResourceMessagesV2.scala:909-923 | — |
| 1.5 | dsp-api | — | 1.1-1.4 | "1.17 just check + just test" | both green | — | — |
| 1.3a | dsp-api | slice/fair/domain/{ResourceFacts,FactsInputs}.scala, ports/FairResources.scala (originalUrl), test ResourceFactsSpec | 1.1 | "1.6"-"1.11", "1.13"-"1.15" | pure derive green on the rule matrix | plan TD2, TD6-TD11 | 1.3 |
| 1.3b | dsp-api | slice/fair/domain/service/ResourceFactsBuilder.scala, StringFormatter project ARK, test ports/*InMemory.scala, ResourceFactsBuilderSpec | 1.3a | "1.4", "1.5", "1.12", "1.16" | builder gathers inputs via ports; malformed/missing -> None; port failure stays failure | plan TD3-TD5 | 1.3 |
| 2.1 | dsp-api | slice/fair/domain/writers/{SchemaOrgJsonLd,ScriptSafeJson}.scala + specs | — | "2.1", "2.2", "2.6 (JSON-LD)", "2.9" | JSON-LD key order and shapes; script-safe output property-tested | scratchpad phase2-writer-mapping.md; schema_org.rs | — |
| 2.2 | dsp-api | slice/fair/domain/writers/{DublinCoreMeta,Html}.scala + specs | 2.1 | "2.3", "2.6 (DC: description cut)", "2.9 (attributes)" | tag order, 1000-code-point cut, attribute escaping | dublin_core_meta.rs, record_dublin_core.rs | — |
| 2.3 | dsp-api | slice/fair/domain/writers/DataCiteJson.scala, MODULE.bazel, maven_install.json, modules/webapi/BUILD.bazel, test schema resource + specs | 2.2 | "2.4", "2.5", "2.7" | DataCite JSON validates against the vendored draft-07 schema | record_datacite.rs, datacite_json.rs | — |
| 2.4 | dsp-api | WritersAgreementSpec | 2.3 | "2.8", "2.10" | writers agree; just check + just test green | — | — |
| 4.1 | dsp-api | slice/common/api/{NegotiatesOwnAccept,RequiresMethod}.scala, core/DspApiServer.scala + spec | — | "4.8", "4.6 (RequiresMethod.head)" | attribute exempts from 406; existing interceptor tests unchanged | scratchpad phase4-design.md | — |
| 4.2 | dsp-api | slice/fair/domain/service/AppShell*.scala + spec | 4.1 | "4.3" | cached shell fetch, 10 s / 2 s TTLs, never fails | phase4-design.md | — |
| 4.3 | dsp-api | slice/fair/api/FairRestService.scala, FairError + spec | 4.2 | "4.2", "4.4", "4.5" | landing/representation answers per design | phase4-design.md | — |
| 4.4 | dsp-api | slice/fair/api/{FairEndpoints,FairServerEndpoints,FairApiModule}.scala, ApiModule, Endpoints, LayersLive, ExportApiModule + core in-memory HTTP spec | 4.3 | "4.6", "4.7", "4.9", "4.10"-"4.15" | routes served end to end in memory | phase4-design.md | — |
| 4.5 | dsp-api | modules/test-e2e FairEndpointsE2ESpec, docker-compose.yml | 4.4 | "4.16", "4.17", "4.18" | E2E green; gate green | — | — |
| 4.6 | dsp-api | ARCH-MAP.md, CONTEXT files (session runs /dune:dune-map) | 4.5 | "4.19" | map lists webapi-fair | — | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | 22e84f763 | fair ports (resources, projects, class labels, assets) with fair-owned DTOs, ResourceFacts model, FairConfig (4.1 pulled forward) | none |
| 1.2 | dsp-api | complete | c5fad015d | RecordRules (selectFileValue over file values only, typeOfData, description table, original URL) shared by ExportService; golden unchanged | none |
| 1.3a | dsp-api | complete | 5a33bf972 | pure ResourceFacts.derive (page URL, file, access, licence chain, creators, class label) + 26-case spec; unresolved-licence mutation caught | none |
| 1.3b | dsp-api | complete | 070bc4461 | ResourceFactsBuilder (parse uppercased shortcode, gather via ports, derive), StringFormatter.projectArkUrl, four InMemory doubles, builder spec | none |
| 1.4 | dsp-api | complete | 98ae402f3 | FairResourcesLive (anonymous read, withDeleted=false, NotFound/Forbidden -> None), FairProjectsLive (BUILT_IN licence mapping), FairClassLabelsLive, FairAssetsLive; pure-mapping specs | none |
| 1.R1 | dsp-api | complete | 9df941c2c | review fix: FairClassLabels -> FairClasses with requiresFile; a file-requiring resource with no file in the anonymous view is restricted; vacuous builder tests removed; gather parallel | none |
| 1.R2 | dsp-api | complete | 93d74a7e1 | review fix: FairResourcesLive error-channel tests (stub read service); FairConfig raw strings, three URLs validated (underscore hosts accepted), AppConfigSpec cases; RecordRules -> ResourceMetadataRules, typed selectFileValue | none |
| 1.5 | dsp-api | complete | — | just check + just test green after review fixes (spec markdown fixed in the phase's spec-state commit) | none |
| 2.1 | dsp-api | complete | 3cea9612e | SchemaOrgJsonLd (ordered keys, record adaptation), ScriptSafeJson (<,>,&,U+2028/9 escapes, property-tested), WriterFacts shared helpers | none |
| 2.2 | dsp-api | complete | e0c67a1b4 | DublinCoreMeta (ordered DC.* tags, 1000-code-point description cut), Html attribute escaping; emoji-cut mutation caught | none |
| 2.3 | dsp-api | complete | 5ca5d762f | DataCiteJson (kernel order, no empties, schemaVersion last); networknt json-schema-validator 1.5.9 test dep; vendored schema validated as draft-07 for file / no-file / org-fallback | none |
| 2.4 | dsp-api | complete | 021cddf2f | WritersAgreementSpec: title, identifier, creators, licence, type, dates agree across the three writers over five variants | none |
| 2.R1 | dsp-api | complete | 47495bc1c | review fix: schema negative controls + year/date format checks; WriterFacts.nameType; blank description -> None; serialised contentSize pinned | none |
| 2.5 | dsp-api | complete | — | just check + just test green after review fixes | none |
| 3.1 | dsp-api | complete | 95630dd1c | Negotiation.decide port (byte limit, 20 raw entries, Rust f64 q syntax, ASCII-only case folding); 22 Rust tests mirrored + 3 port-specific | none |
| 3.2 | dsp-api | complete | 7e6db5295 | Signposting (landing/representation link sets, RFC 8288 header with href delimiter encoding, link elements), FairUrls, OaiShortcodes (moved from FairConfig); OAI id matches oai-pmh.md literal | none |
| 3.R1 | dsp-api | complete | 5e535753a | review fix: Unicode whitespace as \u escapes (ASCII-only), port pointer comment, non-vacuous long-s case-fold test (equalsIgnoreCase folds it: verified in jshell), OAI identifier encoded with the urlencoding crate's unreserved set | worker's bazel runs wedged the server; session killed its own clients, restarted the server and verified |
| 3.3 | dsp-api | complete | — | just check + just test green after review fixes | none |
| 4.1 | dsp-api | complete | 41411adf9 | NegotiatesOwnAccept attribute exempts an endpoint from the early 406 (passthrough audit / Connection: close still key on routeMarker); RequiresMethod.head | none |
| 4.2 | dsp-api | complete | ac01cf876 | AppShell / AppShellLive: capacity-1 zio-cache (Some 10 s, None 2 s), 500 ms read / 1 s overall timeout, Accept-Encoding identity, 200 + </head> only | none |
| 4.3 | dsp-api | complete | ea13efc5c | FairRestService: landing (303 / spliced shell / unchanged shell / 502, Vary + Cache-Control always, safeApply guards), JSON-LD and DataCite representations, identical empty 404 | none |
| 4.4a | dsp-api | complete | 4fb0108ec | FairEndpoints (GET/HEAD landing, JSON-LD, DataCite; no security input; NegotiatesOwnAccept), FairServerEndpoints, FairApiModule; adapters + shared AssetInfoService layer wired in LayersLive; ApiModule/Endpoints | none |
| 4.4b | dsp-api | complete | e13f5d3c7, 8460c4285 | FairEndpointsHttpSpec (23 wire-level tests for 4.10-4.15); fixed val initialisation order in FairEndpoints (body output was null -> MatchError on every body route) | none |
| 4.5 | dsp-api | complete | 2d052df45, afdfc63dd | FairEndpointsE2ESpec (7 tests; stub shell on localhost:18742; plan's three resources verified in test data, no substitutions) and docker-compose KNORA_WEBAPI_FAIR_* | E2E needed port 3333: user approved stopping container dsp-api-api-1 (left stopped) |
| 4.R | dsp-api | complete | 6027a6612, f6da61675 | review fixes: one NegotiatesOwnAccept exemption (passthrough carries it), FairInternalError case object, no-</head> shell -> 502, defects -> typed 500 with landing headers, nosniff on representations, AppShellLive moved to fair/infrastructure (no redirects, 1 MiB cap), single AssetInfoServiceLayer; HTTP suites named by behaviour, E2E stub lifecycle | none |
| 4.6 | dsp-api | complete | 534576fb7 | scoped ARCH-MAP update (webapi-fair + touched entries), docs/contexts/fair/CONTEXT.md, root index | user chose scoped update; schema-1 migration / full re-map left for a follow-up |
| 4.7 | dsp-api | complete | — | just check + just test green; just test-e2e 967/971 with two ValuesEndpointsE2ESpec reorder tests (PUT /v2/values/order) answering 500 in the full suite only; that spec alone passes 107/107 on the same commit | flaky in full-suite context, unrelated to fair (previous full run lost Fuseki in the same spec) |

## Deferrals

- ARCH-MAP.md schema-1 -> 2 migration and re-mapping of the 22 components already stale from unrelated main changes: follow-up PR (user decision at 4.19).
- Phase 1 review: ARCH-MAP/CONTEXT update -> new 4.19. OAI shortcode-list parsing moved out of `FairConfig` (config must not import `Shortcode`) -> 3.2.
- 1.15 "a hidden file's authorship is ignored" -> 4.11 (needs the resources adapter; a hidden file never reaches derive).
- Session scope set at intake: phases 1-4 (dsp-api only). dsp-app (Phase 6) gets a fresh branch cut from origin/main when that phase starts, not the existing `feature/dev-7420-resource-landing-proxy`.

## Side findings

- The vendored DataCite schema (`shared/fair/testdata/schemas/datacite-4.3-schema.json`) declares draft-07, not draft-04 as plan item 2.5 says; validate as draft-07 with format validation off. DPE validates only project-level DataCite JSON against it, never record-level output.
- Plan L1 vs 1.2: L1 forbids ports returning `ReadResourceV2`, so the extracted export helpers are shared by `ExportService` and fair's resources adapter (`FairResourcesLive`), not called by `ResourceFacts.derive` directly. Licence special cases reach fair through the projects port as a lookup result.
- dsp-api has no per-project custom licences; TD8, 1.9 and 1.14 amended at intake (user decision: built-in only).
- Interpretation of TD5/TD9/1.15: a file value in the anonymous view (>= RV) supplies type, licence and authorship; it is advertised as a DataDownload only at >= V. A "hidden" file is one absent from the anonymous view, which the resources adapter guarantees (tested over HTTP in Phase 4).
- Chunk 1.3 split into 1.3a (pure derive + ResourceFactsSpec) and 1.3b (ResourceFactsBuilder, InMemory doubles, builder spec, project ARK helper).
- ExportService's OAI description lookup uses `r.values.get(internalSmartIri)` against a complex-schema read; it may never match (out of scope here; FairResourcesLive uses `findValues(PropertyIri)`). Worth checking in the export follow-up (plan 5.3).
- Phase 1 outcome: all items ticked; deferred to Phase 3: OAI shortcode parsing (3.2); deferred to Phase 4: hidden-file authorship HTTP test (4.11), ARCH-MAP/CONTEXT update (4.19). Review: 5 reviewers, one fix round (hidden-file access level, error-channel tests, config validation, naming).
- DataCite `sizes`: DPE's record writer deliberately omits them (they describe the bitstream; docs/src/dpe/oai-pmh.md). This work emits `sizes` because the PRD's File row asks for DataCite formats/sizes; the size is only published when the file is visible at V. The DataCite writer also omits `rightsList` without a licence where DPE falls back to the access-rights text (plan TD8).
- Phase 2 outcome: all items ticked; deferred: none. Review: 4 reviewers (dune skipped: pure code inside the fair slice), one fix round.
- `just test` once hit the //modules/webapi:test Bazel timeout (762 s) with all 2491 tests OK and a 13-minute gap in the log between unrelated suites; an immediate re-run passed in 26.5 s. Treated as a machine stall, not a code failure.
- The ASCII-only production-code rule is not enforced by `just check`; Negotiation.scala carried literal Unicode whitespace until the Phase 3 review caught it.
- Phase 3 outcome: all items ticked; deferred: none. Review: 3 reviewers, one fix round. Phase 4 must drop a `Link`/`Location` value the HTTP layer rejects (CR/LF) with a warning, and join repeated `Accept` headers before the 2048-byte check.
- Plan 4.2 error type: implemented as a uniform `FairResponse(status, headers, body)` for 200/303/404/502 so every landing answer carries Vary/Cache-Control and HEAD reuses GET's answer; only infrastructure failures are a typed error (empty 500). Statuses and bodies are as 4.2 specifies.
- `KnoraApi.externalKnoraApiBaseUrl` keeps a non-80 port, so with external port 443 representation URLs read `https://host:443/...`; this is the existing behaviour for every API URL dsp-api emits, not new here.
- Phase 4 outcome: all items ticked; deferred: ARCH-MAP schema migration (follow-up). Review: 6 reviewers, one fix round. `ValuesEndpointsE2ESpec` reorder tests are flaky in the full E2E suite (500 / lost triplestore) but pass in isolation.
- Spec id convention adopted (user, 2026-10-12): an id is written `#<id>` once at its origin and bare elsewhere; applied to the plan and PRD.
