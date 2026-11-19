---
plan: docs/specs/2026-11-19-export-service-identity/01-feat-export-service-identity-plan.md
target_repo: dsp-api
base_commit: 65d173f414e50e3d451a4ce88cc81ff1ca453c45
branch: feature/dev-7936-export-service-identity
started: 2026-11-19
status: complete
autonomy: decide
problem: >-
  dsp-repository's nightly VRE sync must export full project data from any dsp-api server, but every such route
  needs a SystemAdmin user's JWT and no machine credential exists; the HS256 secret cannot be handed out.
symptoms: >-
  No way for a non-user service to trigger, poll, download or delete a project export.
---

# Execution journal: export service identity (DEV-7936)

## Repos

| repo | branch | base_commit | merge_strategy | pr | shipped |
| --- | --- | --- | --- | --- | --- |
| dsp-api | feature/dev-7936-export-service-identity | 65d173f414e50e3d451a4ce88cc81ff1ca453c45 | rebase | PR 4448 | shipped |

## Phases

| phase | repo | phase_base | status | review_fix_rounds |
| --- | --- | --- | --- | --- |
| Phase 1: EdDSA verification and the trusted-key config | dsp-api | 1192b815e3a843752cc017d36b8800c3071dc4f8 | reviewed | 1 |
| Phase 2: the policy and the factory | dsp-api | c6d4608b8f3bf37715fb8823bf2dc0498e7e34b9 | reviewed | 2 |
| Phase 3: service callers on the export endpoints | dsp-api | 11e7daa6d30a0c7aa7cbd001c53b9e1e33498f99 | reviewed | 1 |
| Phase 4: audit log and span attributes | dsp-api | 52637fab66fffe61da1b4a7ed9dc33388862cce8 | reviewed | 1 |
| Phase 5: end-to-end coverage and documentation | dsp-api | 2aeb29a093a363c8f036b0541984af1a6f682220 | reviewed | 2 |

## Owner decisions

## Chunk queue

- P1-C1 | files: modules/jwt/{BUILD.bazel, src/main/.../JwtHeader.scala, src/main/.../EdDsaJwt.scala, src/testkit/.../Ed25519TestSigner.scala, src/test/.../EdDsaJwtSpec.scala, src/test/.../JwtCodecSpec.scala} | deps: none | owns: Phase 1 checkboxes 1-5 (JwtHeader kid/crit, EdDsaJwt, test signer, jwt tests, decodeAll rejects EdDSA) | accept: `bazel test //modules/jwt:test` green, every listed rejection pinned | context: plan "Technical Considerations" Security bullet
- P1-C2 | files: modules/webapi/src/main/.../config/AppConfig.scala, application.conf, core/LayersLive.scala, test/.../config/AppConfigSpec.scala | deps: none | owns: checkboxes 6 (ServiceIdentityConfig) and 12 (AppConfigSpec default) | accept: AppConfigSpec green | context: plan P1
- P1-C3 | files: modules/webapi/src/main/.../slice/common/domain/ServiceName.scala + test | deps: none | owns: checkbox 7 | accept: ServiceNameSpec green | context: docs/development/value-types.md
- P1-C4 | files: modules/webapi/src/main/.../slice/security/TrustedServiceKeys.scala + test | deps: P1-C2, P1-C3 | owns: checkboxes 8 and 10 | accept: TrustedServiceKeysSpec green | context: plan P1
- P1-C5 | files: modules/webapi/src/main/.../slice/security/{ServiceTokenVerifier.scala, ServicePrincipal.scala, SecurityModule.scala}, core/LayersLive.scala, BUILD.bazel (test dep on //modules/jwt:testkit) + test | deps: P1-C1, P1-C4 | owns: checkboxes 9 and 11 | accept: ServiceTokenVerifierSpec green on TestClock | context: plan P2, P3, P4, P9
- P1-gates | files: none | deps: all | owns: checkboxes 13 (`just test-unit`) and 14 (`just check`) | accept: both pass after the phase review | context: n/a
- P2-C1 | files: slice/common/api/Policy.scala, test-e2e/.../AuthorizationMatrixE2ESpec.scala | deps: none | owns: Phase 2 checkbox 1 (Policy case + allowedBy) | accept: webapi and e2e compile | context: plan P10, Technical Considerations "ADR-0013 infrastructure"
- P2-C2 | files: slice/security/ExportCaller.scala, slice/common/api/BaseEndpoints.scala, test-e2e/.../EndpointPolicyE2ESpec.scala | deps: P2-C1 | owns: checkboxes 2, 3, 8 | accept: compiles | context: plan Shape
- P2-C3 | files: slice/api/v3/{V3BaseEndpoint.scala, ApiV3Module.scala}, test V3BaseEndpointSpec, OntologyMappingEndpointsSpec | deps: P2-C2 | owns: checkboxes 4, 5, 6 | accept: V3BaseEndpointSpec routing table green | context: plan P5
- P2-C4 | files: test slice/security/AuthenticatorLiveSpec.scala (real HS256 path; v3 imports POST and a BaseEndpoints bearer endpoint) | deps: P2-C3 | owns: checkbox 7 | accept: service token gets 401 on both | context: plan P5
- P2-gates | files: none | deps: all | owns: checkboxes 9 (e2e build), 10 (`just test-unit`), 11 (`just check`) | accept: all pass after the phase review | context: n/a
- P3-C1 | files: slice/export/domain/TaskCreator.scala + TaskCreatorSpec | deps: none | owns: Phase 3 checkbox 1 (TaskCreator, codec, Schema.string) | accept: TaskCreatorSpec green | context: plan P7
- P3-C2 | files: slice/export/domain/{Models, DataTaskState, FilesystemDataTaskPersistence, ProjectMigrationExportService, ProjectMigrationImportService, ProjectDataImportService}.scala, api/v3/projects/{V3ProjectsEndpoints (DataTaskStatusResponse), V3ProjectsRestService}.scala, the specs the compiler names, ProjectDataImportE2ESpec, a DataTaskStatusResponse OpenAPI test | deps: P3-C1 | owns: checkboxes 2, 3, 5 | accept: webapi + e2e compile, export/import specs green, OpenAPI createdBy is a plain string | context: plan P7, Technical Considerations "DataTaskStatusResponse is shared"
- P3-C3 | files: test FilesystemDataTaskPersistenceSpec | deps: P3-C2 | owns: checkbox 4 | accept: old-shape files restore, service task round-trips, invalid service name = corrupt | context: plan "Persistence"
- P3-C4 | files: api/v3/V3ErrorCode.scala, api/v3/projects/V3ProjectsEndpoints.scala (trigger variant) | deps: none | owns: checkbox 6 | accept: compiles | context: plan P11
- P3-C5 | files: api/v3/projects/{V3ProjectsEndpoints, V3ProjectsServerEndpoints, V3ProjectsRestService}.scala + V3ProjectsRestServiceSpec | deps: P3-C2, P3-C4 | owns: checkboxes 7, 8, 9, 10, 11 | accept: V3ProjectsRestServiceSpec ownership table green | context: plan P6, P10, P11, Shape
- P3-gates | files: none | deps: all | owns: checkboxes 12 (e2e build), 13 (`just test-unit`), 14 (`just check`) | accept: all pass after the phase review | context: n/a
- P4-C1 | files: slice/security/ServiceTokenVerifier.scala + ServiceTokenVerifierSpec | deps: none | owns: Phase 4 checkbox 3 (WARN on a rejected service token) | accept: spec pins one WARN with the reason, no token, kid only when configured | context: plan P9
- P4-C2 | files: api/v3/projects/{V3ProjectsRestService, ExportServiceAudit}.scala + V3ProjectsRestServiceSpec | deps: none | owns: checkboxes 1, 2, 4, 5 (audit line, span attributes, admin unchanged, tests) | accept: one audit line per service call with the P12 outcome, exactly four span attributes, none for an admin | context: plan P8, P12, Technical Considerations "Observability"
- P4-gates | files: none | deps: all | owns: checkboxes 6 (`just test-unit`), 7 (`just check`) | accept: both pass after the phase review | context: n/a
- P5-C1 | files: modules/testkit/.../testservices/TestApiClient.scala | deps: none | owns: Phase 5 checkbox 2 (raw-bearer POST and zip download overloads) | accept: testkit and e2e compile | context: plan Phase 5
- P5-C2 | files: modules/test-e2e/{BUILD.bazel, src/test/resources/application.conf, src/test/scala/.../slice/api/v3/projects/{ExportServiceTestKey, ExportServiceIdentityE2ESpec}.scala} | deps: P5-C1 | owns: checkboxes 1, 3 | accept: `bazel build //modules/test-e2e:test` (suite runs at the final review) | context: plan Phase 5, ProjectExportTestSupport
- P5-C3 | files: docs/adr/0014-service-identity-for-exports.md, docs/adr/0013-authorization-policy-enforcement.md, mkdocs.yml | deps: none | owns: checkboxes 4, 5 | accept: `just check` | context: plan Settled decisions, P1-P12, journal open deferrals/deviations/side findings
- P5-C4 | files: docs/contexts/identity-access/CONTEXT.md | deps: none | owns: checkbox 6 | accept: `just check` | context: glossary format in the file
- P5-C5 | files: docs/03-endpoints/api-v3/project-migration.md, docs/04-publishing-deployment/configuration.md | deps: none | owns: checkbox 7, 8 and the configuration.md half of 11 | accept: `just check` | context: code as built (log lines, rejection categories)
- P5-C6 | files: CONVENTIONS.md, docs/development/code-conventions.md | deps: none | owns: checkbox 9 | accept: `just check` | context: factory table in code-conventions.md
- P5-C7 | files: ARCH-MAP.md | deps: none | owns: checkbox 10 and the ARCH-MAP half of 11 | accept: `just check` | context: jwt, webapi-security, webapi-app, webapi-common entries
- P5-gates | files: none | deps: all | owns: checkboxes 12 (e2e build), 13 (`just check`) | accept: both pass after the phase review | context: n/a

## Chunks

| chunk | commit | summary | tests |
| --- | --- | --- | --- |
| P1-C1 | 8e3ff37af | `EdDsaJwt.peekHeader`/`verify` (JDK only), `JwtHeader.kid`/`crit`, test-only `//modules/jwt:testkit` (`Ed25519TestSigner`), registered in `justfile` `scalafmt_targets` | `//modules/jwt:test` green; canary: dropping the padding regex and the crit check fails the padded and crit tests |
| P1-C2 | a59a73d75 | `ServiceIdentityConfig(trustedKeys)` under `app.service-identity`, default `"[]"`, env `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS`, in `AppConfigurations`; `LayersLive` input type waits for P1-C5 (ZLayer rejects an unused input) | `AppConfigSpec` green |
| P1-C3 | e67fbab37 | `ServiceName` (`^[a-z0-9-]+$`, `StringValueCompanion`) in `slice/common/domain`; no JSON codec yet (added where a codec needs it) | `ServiceNameSpec` green |
| P1-C4 | 0cf99033f | `TrustedServiceKeys.parse`/`layer` in `slice/security` (`TrustedServiceKey(name, kid, PublicKey)`, by-kid map, startup INFO line, `orDieWith` on a bad setting); webapi test target depends on `//modules/jwt:testkit` | `TrustedServiceKeysSpec` green; canary: dropping the duplicate-kid check fails its test |
| P1-C5 | 56c3b3417 | `ServiceTokenVerifier` (final class, `ZLayer.derive`), `ServicePrincipal`, `ServiceTokenRejection` enum with `reason`; hand-written `normaliseAudience`; malformed token -> `bad_signature`, iat > now + 30 s and exp < iat -> `bad_claims`; `SecurityModule` + `LayersLive` wired (`ServiceIdentityConfig`, `KnoraApi` added to deps) | `ServiceTokenVerifierSpec` green on `TestClock`; canary: leeway 0 and no default-port drop fail 6 tests; `//modules/testkit` builds |
| P1-R1a | 2231440e5 | review fix round 1: `JwtClaim` reads numeric claims with `longValueExact` (out-of-range or fractional = absent); nested-header guard test | `//modules/jwt:test` green; canary: `longValue` fails the new test |
| P1-R1b | 706b5d080 | review fix round 1: verifier tests for overflow and blank jti, `TrustedServiceKeys` die-at-startup asserted, `BadSignature` scope documented | verifier and key specs green |
| P1-gates | (no commit) | after fix round 1 and its clean re-review: `just test-unit` 7/7 targets pass, `just check` passes (journal tables restyled for MD060) | both green |
| P2-C1 | 5748383a2 | `Policy.SystemAdminOrExportService` (requirement sentence, enum scaladoc), `allowedBy -> Some(Set(Root))` | webapi and `//modules/test-e2e:test` compile |
| P2-C2 | 692c875b1 | `ExportCaller` (`Admin`, `Service`) in `slice/security`; `BaseEndpoints.bearerSystemAdminOrServicePolicyEndpoint` (bearer only, sets the policy, takes `String => IO[E, ExportCaller]`); `EndpointPolicyE2ESpec` message lists it | webapi and `//modules/test-e2e:test` compile |
| P2-C3 | 240bbb20a | `V3BaseEndpoint(authenticator, authorization, serviceTokenVerifier)`; `systemAdminOrExportServicePolicy` routes on header `alg == EdDSA` exactly, rejected service token -> fixed 401, user path = `handleBearerJwt` + `ensureSystemAdmin`; `ApiV3Module.Dependencies` gains the verifier; `BaseEndpointsSpec` unaffected (it builds `BaseEndpoints` only) | `V3BaseEndpointSpec` (29) and `OntologyMappingEndpointsSpec` green; canary: routing by `kid` fails 2 tests |
| P2-C4 | fa154ae36 | `AuthenticatorLiveSpec` suite on the real authenticator: an EdDSA token (also with `sub` = an existing user) fails `BadCredentials`; v3 imports POST and `bearerSystemAdminEndpoint` give 401, with a verifier that would admit it | spec green (10); positive canary in the same tests: a non-admin user's HS256 token gets 403 on the same request |
| P2-R1 | a6e263163 | review fix round 1 (scala-zio, security Warnings): stub knows every service-shaped token as the admin, so rejected EdDSA tokens' 401 proves they skip the user path; `HS256`/`eddsa`/no-`alg` tokens are admitted as the admin (user path; the real decoder 401s them, pinned in `AuthenticatorLiveSpec`); new policy on the real authenticator (service admitted, non-admin 403); `BaseEndpointsSpec` policy test; `servicePrincipal` renamed `serviceCaller` | V3/Base/AuthenticatorLive specs green (96); canary: falling back to the user path on rejection fails 6 |
| P2-R2 | 56ddcfe89 | review fix round 2 (consistency Warning: the V3 spec comment claimed a pin that did not exist; simplicity/dune duplication): `AuthenticatorLiveSpec` pins 401 for `HS256`/`eddsa`/no-`alg` service-signed tokens and a malformed header on the export policy; shared `v3Base`/`exportPolicyProbe`/`serviceClaim` helpers | spec green (12) |
| P2-gates | (no commit) | after fix round 2 and its clean re-review: `//modules/test-e2e:test` builds, `just test-unit` 7/7 targets pass, `just check` passes | all green |
| P3-C1 | aab33176c | `TaskCreator` (`ByUser`, `ByService`) in `slice/export/domain`, one-string codec (`urn:dsp:service:<name>` tried first, then `UserIri.from`), explicit `Schema.string`; codec and schema without `slice/api` helpers (api_imports rule) | `TaskCreatorSpec` green (7) |
| P3-C2 | b898339af | `TaskCreator` threaded through `CurrentDataTask`, `PersistedDataTask`, `DataTaskState.makeNew`, `createExport` (log line names `createdBy.value`), `DataTaskStatusResponse`; imports pass `ByUser`; trigger stays admin-only (`ByUser`) until P3-C5 | affected export/import specs green; `DataTaskStatusResponseSpec` (OpenAPI) green, canary: `.format("uri")` fails it; `//modules/test-e2e:test` builds |
| P3-C3 | cef6dc1ad | `FilesystemDataTaskPersistenceSpec` "createdBy field" suite through `restore()`: literal old-shape file with and without `onBehalfOf` restores as `ByUser`; service creator written as `urn:dsp:service:dsp-repository` and restored; invalid service name = corrupt (None) | spec green (18); the round-trip test is the positive canary for the corrupt-file absence test |
| P3-C4 | 3d7b9315f | `V3ErrorCode.export_slot_occupied` (fixed text, no placeholders) in `Conflicts`; trigger endpoint's conflict variant lists it | webapi compiles; produced and tested in P3-C5 |
| P3-C5 | 6922fa71e | four export endpoints on `systemAdminOrExportServicePolicy` (descriptions carry its requirement; trigger names `export_slot_occupied`); server endpoints pass the caller; `V3ProjectsRestService`: `creatorOf`/`mayAccess` (exhaustive `ExportCaller` matches, no `case _`), `findExport` = lookup by id then ownership, `ExportLookupError.{NotFound, NotOwner}` mapped to the shared 404 by `exportNotFound`; trigger answers `export_exists` (accessible) or `export_slot_occupied` | `V3ProjectsRestServiceSpec` green (13; real `ProjectMigrationExportService` over an in-memory slot, nulls for collaborators the export handlers never call, `withLiveRandom` so parallel tests get distinct export dirs); canaries: dropping the download ownership check fails the foreign-export tests, `Service => true` fails the slot and foreign tests; `//modules/test-e2e:test` builds |
| P3-R1 | ab0cbe04b | review fix round 1 (scala-zio Warning: free-slot triggers leaked a background export fiber and files): `cleanUp` waits for the triggered export to end and deletes it (`withLiveClock`); nulls documented as deliberate; `TaskCreator.ServicePrefix` private; race comment on delete's `None`; `DataTaskStateSpec` `creator`; Models comment | `V3ProjectsRestServiceSpec`, `DataTaskStateSpec`, `TaskCreatorSpec` green; no "Export failed" daemon log any more |
| P3-gates | (no commit) | after fix round 1 and its clean re-review (consistency, simplicity, dune): `//modules/test-e2e:test` builds, `just test-unit` 7/7 targets pass, `just check` passes (journal markdown fixed) | all green |
| P4-C1 | d9bdc27be | `ServiceTokenVerifier.verify` logs one WARN `Service token rejected: reason=<category> kid=<kid or ->` per rejection; the kid only after it matched a configured key (logged in the verifier, not the router, which cannot know that) | `ServiceTokenVerifierSpec` green (4 new log tests); canary: reverting the verifier fails the 3 rejection-line tests |
| P4-C2 | 7683fcaf1 | `exportCall` wrapper in `V3ProjectsRestService`: for a `Service` caller sets `dsp.service.{name,kid,action}` and writes one `ExportServiceAudit` INFO line from `onExit`; handlers record the found or collided task and the lookup refusal in a per-call `Ref[ExportCallFacts]`, `outcomeOf` reads the refusal first; `dsp.project.shortcode` from the resolved project on trigger, from the found task's project (best-effort `findById`) on status/download/delete, never on a refusal; `Tracing` added to `ApiV3Module.Dependencies` | `V3ProjectsRestServiceSpec` green (20, 7 new); canary: mapping `NotOwner` to `not_found` and dropping the lookup shortcode fail 4 |
| P4-R1 | 39ecdc947 | review fix round 1 (consistency, security, scala-zio, dune Warnings): raw U+2028/U+2029 in the jti sanitiser became escapes; jti quoting test; table test of all four actions (one line each, action + outcome, download defect = `error`); `outcomeOf` defect/interrupt = `error`; span helper fails on a non-SDK span; shortcode comment states it never gates | `V3ProjectsRestServiceSpec` green (23); canary: dropping the U+2028 check fails the quoting test |
| P4-gates | (no commit) | after fix round 1 and its clean re-review (consistency, simplicity, dune): `//modules/test-e2e:test` builds, `just test-unit` 7/7 targets pass, `just check` passes | all green |
| P5-C1 | 45ba0c9c5 | `TestApiClient.postJson(uri, f)` (bodiless POST with a request updater) and `getBytes(uri, f)` (binary GET), instance and companion | testkit and `//modules/test-e2e:test` compile; exercised by P5-C2 |
| P5-C2 | 0b1910265 | e2e `application.conf` trusts a committed test-only Ed25519 key (`ExportServiceTestKey`, name `e2e-export-service`, kid `e2e-export-service-test`); `ExportServiceIdentityE2ESpec` (4 tests); `//modules/jwt` and `:testkit` deps on the e2e target | `//modules/test-e2e:test` builds; suite not run (deferred to the final review), so no canary yet |
| P5-C3 | ecf6efbb0 | ADR-0014 (context, ten decisions as built, consequences incl. leaked key, replay, audit limits, project-existence probe, one operator per name, rollback, enforcement by review/tests, open points: WARN level, `ServiceName` location; alternatives); ADR-0013 table row; mkdocs nav | markdownlint clean |
| P5-C4 | 3b446e2de | Identity & Access `CONTEXT.md`: **Service identity**, **Service token**, Policy extended, relationship and flagged ambiguity (a service identity is not a User) | markdownlint clean |
| P5-C5 | 7f3b57fe3 | `project-migration.md`: caller table, Service identity section (token contract, OpenSSL minting recipe, conflicts and recovery, audit line, span attributes, rejection categories); `configuration.md`: env-var row and operator section (format, key generation, rotation, off, logs). Operator docs went to `configuration.md`, which documents `KNORA_WEBAPI_*` variables, as the checkbox allows | markdownlint clean; minting recipe run with OpenSSL 3.6.4, signature verified |
| P5-C6 | 99b5b4d43 | `CONVENTIONS.md` factory list and `code-conventions.md` policy section name `bearerSystemAdminOrServicePolicyEndpoint` and the handler rules (`findExport`, `exportCall`, exhaustive `ExportCaller`) | markdownlint clean |
| P5-C7 | 82bb3281c | ARCH-MAP: jwt (EdDsaJwt, testkit, rules), webapi-security (entities, interface, depends, router-only rule, WARN rule, wiring), webapi-app (`ServiceIdentityConfig` + parse-in-security rule), common `ServiceName`, api `ExportServiceAudit` + jwt dep, export `TaskCreator`; fingerprints not recomputed (dune-map's job) | markdownlint clean |
| P5-R1a | 4a3c3c09e | review fix round 1 (scala-zio Warnings, simplicity Warnings): every service trigger in the e2e spec runs with `skipAssets` inside `withServiceTrigger` (acquire/release: an export it started is awaited and deleted as root); `ProjectExportTestSupport` gains `pollUntilCompleted`/`awaitCompleted` with a request updater (fail fast on `Failed`) and `awaitAndDeleteExport`; `freeSlot` reuses `withCompletedExport`; jwt BUILD comment | `//modules/test-e2e:test` builds; suite not run |
| P5-R1b | 8885e6745 | review fix round 1 (consistency, security, dune Warnings): rotation wait 16 min; revocation needs a restart, leak runbook, test-key warning; `bad_signature` no longer lists unparsable/wrong-alg (they take the user path, no WARN line); ADR consequences (trust root, test key, slot occupation, ownership after leak, audience scope); jti/shortcode/createdBy as built; ARCH-MAP boundary rules in webapi-api and webapi-export, kit files, security/common edge; CONVENTIONS.md handler rule; glossary `ExportCaller` | markdownlint clean |
| P5-R2 | 941a42520 | review fix round 2 (dune DUNE-001/002 Warnings, consistency Suggestion): the trigger checks ownership with `mayAccess` in `exportExists`, not `findExport`; ARCH-MAP/CONVENTIONS/code-conventions name `mayAccess` as the one predicate and say the tests do not detect a fifth handler; scaladoc on the three bare-id `ProjectMigrationExportService` methods; `BadSignature` scaladoc | `just test-unit` green; no re-review (cap reached), checked inline against `V3ProjectsRestService` |
| P5-gates | (no commit) | after fix round 2: `//modules/test-e2e:test` builds, `just check` passes, `just test-unit` 7/7 targets pass; e2e suite not run (final review) | all green |

## Side findings

- Phase 1: review (dune): `ServiceTokenVerifier` is injectable from any slice via `SecurityModule.Provided`; ADR-0014 should state
  that only the `BaseEndpoints` policy factory calls it, ideally with a lint ratchet like `api_imports` (Phase 2/5).
- Phase 1: review (dune): `EdDsaJwt.verify` checks no claim while the sibling `JwtCodec.decodeAll` does; the jwt
  boundary rule in ARCH-MAP should say only `ServiceTokenVerifier` calls it. Name kept as the plan specifies.
- Phase 1: review (dune): the JSON-in-one-string setting is parsed in `slice/security`, not in `AppConfig`'s
  `.validate` chain (which would make app depend on security); ADR-0014 should record it as a deliberate exception.
- Phase 1: review (scala-zio): if `externalKnoraApiBaseUrl` cannot be normalised every token is `bad_audience` with
  no startup hint; a startup warning when keys are configured would help. Not reachable with the current URL builder.
- Phase 1: review (security): base64url signature has 4 malleable trailing bits; harmless while nothing keys on
  the raw token string. Duplicate JSON keys resolve last-wins consistently. Phase 2 must sanitise an unverified
  `kid` before logging it (P9 logs only a configured kid, so this already holds by design).
- Phase 1: review (devops): if the e2e tests need `//modules/jwt:testkit`, depend on it from the e2e/it test targets,
  not from the non-testonly `//modules/testkit`.
- Phase 1: review (simplicity): suggestions on `normaliseAudience` flattening, `TokenTimes` naming, private
  constants; the normalisation itself is the plan's P2 and stays.
- Phase 1: re-review (consistency): a `1e-999999999` exp is not tested; `longValueExact` on it may take a slow
  `setScale` path. Reachable only after a valid signature on either path.
- Phase 2: review (dune DUNE-001, security): the "a service only sees its own exports" rule is handler-enforced; Phase 3
  should keep the `ExportCaller` match in `V3ProjectsRestService` exhaustive (no `case _`) and test every endpoint.
- Phase 2: review (dune DUNE-002): `EndpointPolicyE2ESpec`'s failure message lists factory names by hand.
- Phase 2: review (dune DUNE-003, simplicity): service-key/claim test fixtures are copied in `V3BaseEndpointSpec` and
  `AuthenticatorLiveSpec`; a shared test fixture is worth it once Phase 3/4 specs need a third copy.
- Phase 2: review (security): router and verifier both test `alg == EdDSA`; an `EdDsaJwt.isEdDsa` helper would share
  one definition. `AuthorizationMatrixE2ESpec` has no service principal; the Phase 5 e2e spec covers services.
- Phase 2: review (scala-zio): the router drops `ServiceTokenRejection.reason`; Phase 4's WARN line needs it there.
- Phase 2: review (consistency, simplicity): `Policy` scaladoc's list of factories that re-check activity is long and
  omits the new one (its user path does re-check); a reflow is optional. Only `V3BaseEndpoint` calls
  `ServiceTokenVerifier` in main code; the rule goes in ADR-0014 (Phase 5), no lint added.
- Phase 2: re-review (simplicity, dune): odd-alg token variants are listed in both `V3BaseEndpointSpec` and
  `AuthenticatorLiveSpec`; fold them into the deferred shared service-token fixture. Variant lists lack per-entry labels.

- Phase 3: review (consistency, simplicity): `foreignExportTest` and the slot tests overlap a little; the two bare-user-IRI
  restore tests could merge; `exportExists` builds the no-details `Conflict` by hand (a helper next to `conflict` would be
  its one writer, DUNE-004). Kept as is.
- Phase 3: review (security): a service can tell an existing project IRI from an unknown one on trigger (404
  `project_not_found` vs 202/409), a consequence of the accepted "no project allowlist"; worth one line in ADR-0014.
  Duplicate service names across keys are allowed on purpose (rotation), so ownership by name assumes one operator per name.
- Phase 3: review (scala-zio): a malformed `createdBy` makes `TaskCreator.from` report the `UserIri` error message; a
  combined "user IRI or `urn:dsp:service:<name>`" message would read better in the restore log.
- Phase 3: review (dune DUNE-003): `mayAccess` derives the service branch from `creatorOf`, so the two matches agree; a
  new `ExportCaller` case must extend both (both exhaustive, so the compiler names them).
- Phase 4: review (scala-zio): a service call rejected by tapir decoding (malformed project IRI or export id in the path) never
  reaches the handler and writes no audit line; only the server's request log sees it. Not in the plan's scope.
- Phase 4: review (security): `DataTaskId.from` accepts any base64url string, so a requested `export_id` in the audit line is
  bounded in alphabet but not in length (only by the URL limit). Harmless for forging; a length check would be a separate change.
- Phase 4: review (scala-zio, observability): `error` covers both defects and interruptions (a client disconnect); P12 fixes the
  outcome set, so an `interrupted` outcome is for the owner to add if wanted.
- Phase 4: review (scala-zio): the shortcode lookup on status/download/delete is a `KnoraProjectService.findById` (a cache hit
  normally); a defect or hang there would still affect the call. Accepted: typed failures are swallowed, no timeout added.
- Phase 4: review (dune, consistency): `ARCH-MAP.md` was not refreshed for the security and v3 changes, and nothing outside the
  specs documents the `Export service call:` and `Service token rejected:` lines or the `dsp.service.*` attributes; both belong
  with Phase 5's documentation.
- Phase 4: review (dune): a fifth export handler that skips `exportCall` would go unaudited; the table test covers today's four.

- Phase 5: review (simplicity): the token contract, audit line and rejection categories appear in ADR-0014,
  `project-migration.md` and (rejection line) the ADR again; reviewer proposed trimming the ADR to decisions and
  linking the endpoint doc. Kept: the brief asked the ADR to record the log formats and categories as built.
- Phase 5: review (simplicity): the e2e spec validates the downloaded zip with `BagIt`, which other export specs already
  cover; kept because the plan asks for "downloads a valid zip".
- Phase 5: review (security): the OpenSSL minting recipe could add `umask 077`/`chmod 600`, `curl -sS` and a note that
  real clients mint in-process; secret scanners (gitleaks, push protection) may flag the committed e2e PKCS#8 test key
  and want an allowlist entry; ADR-0014 status stays `Proposed` until merge (owner's call).
- Phase 5: review (security, consistency): the e2e spec does not cover an expired or over-long token, an unknown kid,
  `export_exists` for the service's own export, or 404 on delete/download of another caller's export; those are pinned
  in `ServiceTokenVerifierSpec`, `V3BaseEndpointSpec` and `V3ProjectsRestServiceSpec`.
- Phase 5: review (consistency): `docs/05-internals/design/principles/authentication.md` (in the jwt kit) has no
  service-token paragraph; the `ServiceTokenRejection` scaladoc still lists "unparsable, wrong alg" under
  `BadSignature`, true of the verifier as a unit but unreachable over HTTP.
- Phase 5: the e2e test key pair was generated with the JDK (`KeyPairGenerator` Ed25519); that the two halves match is
  proven only when the e2e suite runs at the final review.

- Phase 5: re-review (simplicity): the `f`-taking overloads in `ProjectExportTestSupport` could replace the root ones
  (refuted: `rootUser` is a `User`, not a request updater, and the root callers stay as they are); `withServiceTrigger`
  on the 409 and 401 tests is the deliberate safety net for a regression that answers 202. Long markdown lines in
  `configuration.md` (rotation) and `project-migration.md` (span attributes) could be re-wrapped; the one-home-per-rule
  trimming across ADR, `configuration.md`, ARCH-MAP and CONVENTIONS.md was left for the final review.
- Phase 5: re-review (dune DUNE-003): a test enumerating the endpoints on `SystemAdminOrExportService` and asserting the
  set would raise the fifth-handler rule from review to a check; not built (outside the plan).

## Deferrals

- Phase 1: ARCH-MAP jwt/webapi-security/webapi-app entries and the `configuration.md` env-var row are stale after
  this phase; added as a `- [ ]` item to Phase 5 (docs), next to the existing ARCH-MAP item.

- Phase 3: scope (Warning): review (dune DUNE-001/002) the "a service only sees its own exports" rule is enforced by
  `findExport` in `V3ProjectsRestService`, while `ProjectMigrationExportService.getExportStatus`/`deleteExport`/
  `downloadExport` take a bare id and a handler on `systemAdminOrExportServicePolicy` compiles with the caller ignored.
  A structural guard (an owned-export proof type the service methods require) or a lint over such handlers would change
  the plan's P10 shape; for the final review. ADR-0014 (Phase 5) should at least state the rule and where it lives.

- Phase 5: scope (Warning): review (dune DUNE-003) proposes raising "only `V3BaseEndpoint`'s router calls
  `ServiceTokenVerifier`" and "only `ServiceTokenVerifier` calls `EdDsaJwt.verify`" from review to static analysis (a
  test or `check_authz`-style lint failing on a new reference). A new lint is not in the plan; recorded in ADR-0014's
  open points for the final review.

## Deviations

- Phase 1: provisional: rejection categories the plan leaves open. A token whose header cannot be parsed maps to
  `bad_signature`; an `iat` more than 30 s in the future and an `exp` before `iat` map to `bad_claims`. Alternatives
  set aside: a separate `not_yet_valid` category (the plan fixes the category list), `expired` for a future `iat`
  (misleading in the log). Commit 56c3b3417.
- Phase 1: provisional: the Ed25519 test signer is a `testonly` target `//modules/jwt:testkit` (plan offered it or
  one copy per module), added to the `justfile`'s `scalafmt_targets` so `just fmt`/`just check` cover it. Commit
  8e3ff37af.
- Phase 1: proposed: review (dune, DUNE-005) proposes moving `ServiceName` from `slice/common/domain` to
  `slice/security`, since webapi-common is the "false foundation" the modularization plan dissolves. Decided (P10):
  `slice/common/domain`, so the export domain can use it without depending on `slice/security`. Left as decided;
  for the owner at the final review. Commit e67fbab37.
- Phase 4: proposed (observability, security, scala-zio review): P9 decided one WARN per rejected service token. Any
  unauthenticated caller can send a token with `alg: EdDSA` and so write one WARN per request (also `disabled` when no key is
  configured). Reviewers propose INFO/DEBUG for `disabled`, `unknown_kid` and `bad_signature` (rejections before the sender is
  shown to hold a key) and WARN only for the rest, or sampling. Left as decided (commit d9bdc27be); the owner decides.
- After closeout: owner decision (2026-11-19): `409 export_slot_occupied` (P3-C4, P3-C5) removed. A service that
  triggers while any export holds the slot gets `409 export_exists` with its id and project, as a SystemAdmin does;
  exporting is a SystemAdmin or trusted-service operation, so the occupant's id is not a leak worth a separate code.
  P11 and its acceptance criterion in the plan are revised accordingly.

## Closeout

All five phases reviewed and closed. The full e2e suite (`just test-e2e`) passed once at the final review (609 s). The
final code review (scala-zio, security, consistency, dune, simplicity, observability, devops) found no Critical, and
no Warning named a present failure after the cull, so no fix round ran. Open for the owner: the provisional P1-P12
choices, the Phase 1 provisional choices, the deviations above, the Phase 3 structural-guard deferral and the Phase 5
static-guard deferral. Draft PR #4448.
