---
title: "feat: a service identity that is not a user, for machine-to-machine project exports"
type: feat
date: 2026-11-19
author: "Balduin Landolt"
status: implemented
repositories: []
linear: DEV-7936
linear_project: VRE→Archive transformation pipeline
---

# feat: a service identity that is not a user, for machine-to-machine project exports

## Overview

dsp-api gets a **service identity**: a caller that is not a `User`, authenticates with a short-lived Ed25519-signed
JWT that it mints itself, and may only run the v3 project export flow (trigger, status, download, delete) for the
exports it created. Trusted public keys are configured per server; with none configured the feature is off. Every
service call is logged.

The decisions below were settled with the owner on 2026-11-19 and are recorded in DEV-7936 ("Decisions"). They are
not reopened here.

## Problem Statement / Motivation

dsp-repository's Deposit Area synchronises whitelisted projects from the VRE every night: it exports the project's
full data (non-public data and permissions included) from whichever dsp-api server (prod, stage, test servers) holds
the project. Today every route that returns a project's full data needs a SystemAdmin user's JWT, and no machine
credential exists. The pipeline's first usable increment depends on this; no interim SystemAdmin account is planned.

dsp-api tokens are HS256 with one secret per server (`JwtService.scala:74-76`, `modules/jwt` is HS256 only). Handing
that secret to a client would let it mint SystemAdmin tokens, so the service needs its own, asymmetric credential.

## Proposed Solution

### Settled decisions (DEV-7936)

1. Ed25519 (`alg: EdDSA`, RFC 8037). The client holds the private key and mints its own tokens; dsp-api holds public
   keys only.
2. Off by default: `application.conf` trusts no key; ops-deploy sets keys per server through an environment variable.
3. A config list of trusted keys `{name, kid, public key}`. The name is the service identity. A token needs a listed
   `kid`, `aud` equal to this server's external base URL, a lifetime `exp - iat` of at most 15 minutes, Bearer only.
4. Permitted: trigger an export, its status, its download, its deletion; status, download and delete only for
   exports the service created. Every other endpoint rejects the token (401). New `Policy` case. `createdBy` becomes
   user-or-service; task files written by the previous version still load.
5. No server-side project allowlist.
6. Export format unchanged (password hashes are in exports by design). ADR records the exposure of a leaked key.
7. One INFO log line per call (service name, `kid`, `jti`, action, project IRI, export id) plus span attributes.
   `jti` logged, not checked for replay.
8. dsp-api only, one PR. ADR-0014, Identity & Access `CONTEXT.md`, endpoint descriptions. Tests use a key pair
   generated in the test. The PR body documents ops-deploy settings and the token contract for the client.

### Decisions taken during planning (reversible; listed for the owner's veto)

- **P1. Key format in config.** One env var `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS` holding a JSON array
  `[{"name": "dsp-repository", "kid": "dsp-repository-2026-11", "publicKey": "<SPKI>"}]`, where `publicKey` is the
  base64 of the X.509 SubjectPublicKeyInfo DER, with or without PEM armour and line breaks (what
  `openssl pkey -pubout` prints). There is no list-from-env pattern in the repo (HOCON `${?VAR}` yields a string);
  the closest precedent is the comma-separated `fair.dpe-oai-shortcodes`. A JSON string carries the three fields per
  key. A malformed array, an unparsable or non-Ed25519 key, a duplicate `kid`, or a blank name fails startup. An
  empty or whitespace-only value means "no keys", like `[]`, because an unset-but-declared env var renders as `""`.
  Several keys may share one `name` (rotation overlap); `kid`s are unique. At startup one INFO line states the number
  of trusted keys with their names and `kid`s, never key material. Options weighed: comma-separated `name:kid:key`
  triples (fragile with base64), indexed `CONFIG_FORCE_` env vars (unused here).
- **P2. Audience comparison.** `aud` must match the server's external base URL after normalisation: lower-case
  scheme and host, default port dropped (443 for https, 80 for http), trailing slash dropped. Reason: ops-deploy sets
  `DSP_API_PROTOCOL=https`, `DSP_API_PORT=443`, so `KnoraApi.externalKnoraApiBaseUrl` renders
  `https://api.dasch.swiss:443`, while a client naturally uses `https://api.dasch.swiss`. `aud` may be a string or an
  array (RFC 7519 §4.1.3); one element must match. Both sides go through the same normaliser; it parses the authority
  by hand rather than with `URI.getHost`, which is null for host names containing `_` (Docker service names).
- **P3. Issuer and subject.** `iss` and `sub` must both equal the matched key's `name`. Binding the identity to the
  key means a token signed with one key cannot claim another service's name.
- **P4. Time checks.** `exp`, `iat` and `jti` are required. Clock leeway 30 s: reject when `now >= exp + 30s` or
  `iat > now + 30s`, and when `exp - iat > 15 min` (no leeway on the lifetime). `JwtClaim` has no `nbf` field
  (unknown claims land in `content`), so `nbf` is not checked; the lifetime cap bounds the token anyway. The current
  time comes from ZIO `Clock`, so tests drive it with `TestClock`. The client contract recommends `exp` 5 minutes
  after `iat`.
- **P5. Token routing.** The new factory peeks at the unverified JWS header. When `alg == "EdDSA"` the token goes to
  the service verifier and never reaches `Authenticator.authenticate`, which would read `sub` as a user IRI.
  Otherwise the existing user path runs and the user must be a SystemAdmin. No other endpoint base changes: the
  HS256 decoder rejects an EdDSA token (the signature check fails), so the token gets 401 there. A test pins that.
  `alg` is compared exactly (`EdDSA`); a malformed header, a missing `alg` or another spelling takes the user path
  and gets its 401. A token with `alg` `HS256` and a service `kid` never reaches the service verifier.
- **P6. Not someone else's export → 404.** A service asking for the status, download or deletion of an export it did
  not create gets `404 export_not_found`, the same answer as for an unknown id, so it learns nothing about other
  exports. The ownership check runs right after the lookup by id and before any state check, so a service never sees
  `409 export_in_progress` or `export_failed` for someone else's export, and a deletion never starts. Lookup and
  deletion are separate calls on the single slot; a swap of the slot between them is possible but harmless (the
  deletion acts on the id, which then no longer exists). SystemAdmins keep full access to every export, including the
  service's. Ownership is keyed on the service name, never on `kid` or `jti`, so an export created under an old key
  stays the service's after rotation.
- **P11. Occupied slot** (revised by the owner on 2026-11-19). The slot is server-wide. When a service triggers an
  export while the slot holds any export, its own or anyone else's, it gets `409 export_exists` with that export's id
  and project, exactly as a SystemAdmin does. With its own export it recovers after a crash (check status, delete,
  trigger again). The client contract says: on `export_exists` for an export that is not its own (its status answers
  `404`), give up for this run and retry on the next one, never loop. The ticket keeps the slot collision itself out
  of scope; this only fixes what the service is told.
  Revision note: the first version answered another caller's export with a new `409 export_slot_occupied` (fixed
  message, no id or project). The owner dropped it: exporting is a SystemAdmin or trusted-service operation, so
  revealing another caller's export id and project to a service is not a leak worth a separate code.
- **P7. How a service creator is written.** `createdBy` becomes a `TaskCreator` with two cases, user IRI or service
  name, serialised as one string in both the task file and `DataTaskStatusResponse.createdBy`: the user IRI as today,
  or the IRI `urn:dsp:service:<name>` for a service. Old task files decode unchanged and the API field stays a string.
  The codec is hand-written (a derived one would emit an object): it tries the `urn:dsp:service:` prefix first, then
  `UserIri.from`; a string that fits neither, or a prefix with an invalid name, fails to decode. The tapir schema is an
  explicit `Schema.string` with the description "IRI of the user or service that created the task." (only the
  description changes in the OpenAPI output; dsp-app's generated model already types the field as a string and
  nothing in dsp-app or dsp-tools reads it). The enum cases are `ByUser` and `ByService`, so they do not shadow the
  `User` class. Options weighed: a JSON object for services (breaks the response's string type and the OpenAPI
  client), a second optional field (leaves `createdBy` meaningless for service tasks).
  **Rollback:** a binary from before this change cannot decode a service task file and drops it silently
  (`readTaskJson` logs and treats the file as corrupt), leaving the export directory to be cleaned by hand. The ADR
  and the PR body say so.
- **P8. Span attributes are bounded.** `docs/observability/instrumentation-recipe.md` forbids traffic-bounded values
  (instance IRIs, ids) as span attributes, because spanmetrics can turn them into labels. The span gets the bounded
  ones: `dsp.service.name`, `dsp.service.kid`, `dsp.service.action`, and the project shortcode. `jti`, project IRI
  and export id go into the log line only. This narrows decision 7 to fit a repo rule.
- **P9. Rejected service tokens are logged too.** A token routed to the service path that fails verification logs
  one WARN line with a fixed reason category (`unknown_kid` (also for a missing `kid`), `bad_signature`,
  `bad_audience`, `expired`, `lifetime_too_long`, `bad_claims`, `disabled`) and the `kid` only when it is a configured
  one. With no keys configured every service token is `disabled`, checked before the `kid` lookup. The client always
  gets the same fixed-text 401. The raw token is never logged. No rate limiting in dsp-api;
  that stays with the reverse proxy.
- **P12. Audit outcomes.** The audit line carries an `outcome` (`ok`, `not_owner`, `not_found`, `conflict`,
  `error`), so operators can tell an ownership refusal from an unknown id although the client gets the same 404.
  For a download the line records that the response stream started; a download cut off mid-stream is still logged as
  `ok`. The ADR states that limit.
- **P10. Names.** Policy case `SystemAdminOrExportService`; requirement sentence "Requires SystemAdmin permissions,
  or a trusted export service, which only sees the exports it created." Config section `app.service-identity`. Code
  in `slice/security/` (`ServiceIdentityConfig`, `ServicePrincipal`, `ServiceTokenVerifier`), EdDSA mechanics in
  `modules/jwt`. `ServiceName` is a shared value type in `slice/common/domain` (next to `LanguageCode`), so the
  export domain can use it without depending on `slice/security`. `ExportCaller` lives in `slice/security`; the
  mapping `ExportCaller -> TaskCreator` lives in `V3ProjectsRestService`, the only place that needs both.

### Shape

```text
Bearer token ──► BaseEndpoints.bearerSystemAdminOrServicePolicyEndpoint (sets Policy.key)
                   └─ routing function from V3BaseEndpoint.systemAdminOrExportServicePolicy
                        ├─ header alg == EdDSA ─► ServiceTokenVerifier ─► ExportCaller.Service(principal)
                        └─ otherwise ─► Authenticator + ensureSystemAdmin ─► ExportCaller.Admin(user)
V3ProjectsRestService
  trigger  : createdBy from the caller; any export in the slot answers 409 export_exists with its id
  status / download / delete : lookup by id, then a Service caller must equal task.createdBy, else 404
  every Service call : one INFO audit line + bounded span attributes
```

```scala
// modules/jwt — pure, JDK only
object EdDsaJwt {
  def peekHeader(token: String): Try[JwtHeader]                        // unverified; for routing and kid lookup
  def verify(token: String, key: PublicKey): Try[(JwtHeader, JwtClaim)] // alg == "EdDSA", 64-byte signature
}

// slice/security
final case class ServicePrincipal(name: ServiceName, kid: String, jti: String)
enum ExportCaller { case Admin(user: User); case Service(principal: ServicePrincipal) }
```

## Alternative Approaches Considered

- **HS256 with a per-server shared secret** (the cheapest shape: reuse `JwtCodec`). Rejected: the secret mints
  SystemAdmin user tokens on that server, and every server would need its own client secret.
- **A real SystemAdmin service account per server.** Rejected by the ticket: no account on each server, and a
  SystemAdmin can do everything.
- **Reuse the JWT `scope` claim on user tokens.** Rejected: still a user, and ADR-0013 keeps Scope apart from
  authorization.
- **A JWT library (jwt-scala, nimbus).** Rejected: the JDK verifies Ed25519 natively (JEP 339) and `modules/jwt`
  is the repo's leaf for JWT mechanics; a library adds dependencies to every image for about a hundred lines.

## Technical Considerations

- **Security.** `alg` is pinned per key (only `EdDSA` reaches the verifier; HS256 or `none` with a service `kid` is
  rejected). The key is chosen by `kid` from the static map only; `jku`, `jwk`, `x5u`, `x5c` are ignored and `crit`
  is rejected. The signature is checked over the received `header.payload` bytes; it must decode to 64 bytes;
  base64url input with `=`, `+`, `/` or whitespace is rejected. Error messages to clients are fixed strings
  (dasch-specs learning `shared-tapir-error-envelope-leaks-exception-messages.md`): no new variant in
  `BaseEndpoints.errorOutputs`; the v3 base already prepends 401 and 403.
- **Ordering.** The service check runs in the tapir security logic, before the body is decoded, like
  `bearerSystemAdminPolicyEndpoint` (learning `security-logic-authenticates-body-buffering-precedes-it.md`). The
  ownership check needs the export id from the path, so it runs in the RestService.
- **Do not reintroduce DEV-6766.** Status, download and delete resolve the export by id alone
  (`V3ProjectsRestService.scala:81-84`). The ownership check compares only the task's creator and never resolves the
  project.
- **Persistence.** `FilesystemDataTaskPersistence.readTaskJson` silently drops a task file that fails to decode, so a
  codec change that broke old files would lose the export slot's state without an error. A test decodes a literal
  task file in the old shape.
- **`DataTaskStatusResponse` is shared** by exports, imports and data imports. The field stays a JSON string; a test
  pins the OpenAPI schema of `createdBy` (no snapshot test of the spec exists in the repo).
- **ADR-0013 infrastructure.** Two exhaustive matches need the new case: `Policy.requirement` and
  `AuthorizationMatrixE2ESpec.allowedBy` (returns `Some(Set(Root))`, so the four export endpoints stay `exempt` to
  `V3SystemAdminRoutesE2ESpec`). `Policy.key` may only be set in `slice/common/api` and `V3BaseEndpoint`
  (`tools/lint/check_authz.py`), so the factory lives in `BaseEndpoints`. Domain code must not import `slice/api`
  (`check_api_imports.py`): `TaskCreator` lives in the export domain, `ServiceName` in `slice/common/domain`,
  `ServicePrincipal` and `ExportCaller` in `slice/security`.
  New code must not mention `SystemUser`.
- **Observability.** The audit line follows the single-entry `onExit` pattern of `observed` in
  `slice/api/admin/service/SparqlPassthroughRestService.scala:172-227` (it takes a `User`, so it is a model, not a
  reusable helper); attributes go on the current server span via `Tracing`.
- **Code conventions.** Methods stay under 50 lines (split the verifier into per-claim checks); comments cite no plan
  or P-number; no `unsafeFrom` on token input; no `orDie` on a bad token; imports, not fully qualified names.

## Implementation Phases

<!-- markdownlint-disable-next-line MD001 -->
#### Phase 1: EdDSA verification and the trusted-key config

- [x] `modules/jwt`: add `kid: Option[String] = None` and a `crit` presence flag to `JwtHeader`, parsed in `fromJsonString`; every existing `JwtHeader(` call site still compiles
- [x] `modules/jwt`: add `EdDsaJwt.peekHeader` and `EdDsaJwt.verify` (alg exactly `EdDSA`, strict base64url checked against `[A-Za-z0-9_-]` before decoding, 64-byte signature, `crit` rejected, `SignatureException` mapped to a failure rather than thrown, key algorithm checked to be EdDSA/Ed25519), JDK `java.security` only; existing `JwtCodec` signatures unchanged
- [x] Ed25519 test signer (`sign(claim, kid, privateKey)`) in test sources, reachable from the jwt, webapi and e2e tests (a test-only Bazel target, or one copy per module); main code carries no signing code
- [x] `modules/jwt` tests: a token signed with a generated key verifies; a tampered payload, a signature by another key, `alg` `HS256`, `none` or `eddsa`, a 63-byte signature, a malformed signature, padded or non-url base64 and a `crit` header are each rejected
- [x] `modules/jwt` test: `JwtCodec.decodeAll` rejects an EdDSA-signed token (guards the 401 on every HS256 path, ingest included)
- [x] `AppConfig`: `ServiceIdentityConfig(trustedKeys: String)` under `app.service-identity`, `trusted-keys = "[]"` overridden by `${?KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS}`, added to `AppConfig` and `AppConfigurations` and projected as its own layer
- [x] `ServiceName` value type in `slice/common/domain` (non-empty, `^[a-z0-9-]+$`, `StringValue`/`StringValueCompanion` with `from` returning `Either`, per `docs/development/value-types.md`)
- [x] Trusted-key layer: decode the JSON into a plain DTO list, then convert into an immutable `Map[kid, TrustedServiceKey(name, kid, PublicKey)]`; empty or whitespace-only means no keys; malformed JSON, an unparsable or non-Ed25519 key, a duplicate `kid` and an invalid name or blank `kid` each fail startup with a message naming the field and entry index, never key material; one INFO line at startup lists the count, names and `kid`s
- [x] `ServiceTokenVerifier` (final class, `ZLayer.derive`) in `slice/security`: key by `kid` (missing `kid` = `unknown_kid`), verify, then `iss == sub == name`, normalised `aud` match (P2), `exp`/`iat`/`jti` present, 30 s leeway via ZIO `Clock`, `exp - iat <= 15 min`; returns `IO[ServiceTokenRejection, ServicePrincipal]` with the P9 categories as an enum; registered in `SecurityModule` and `LayersLive`
- [x] Trusted-key layer tests: empty, `""`, whitespace and `[]` mean no keys; each failure case fails with a message that contains no key material; two keys may share a name
- [x] `ServiceTokenVerifier` tests on `TestClock`, one per rejection reason, plus: no keys configured rejects every token as `disabled`; a valid token is accepted; `aud` as string and as array; `aud` `https://h` matches a server configured as `https`, `h`, port `443`; `aud` `http://localhost:3333` matches a server on that port; an uppercase host matches; a path in `aud` does not match; a token from key A claiming key B's name is rejected; `exp - iat` of exactly 15 min passes, 15 min 1 s fails
- [x] `AppConfigSpec`: the default trusts no key
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 2: the policy and the factory

- [x] `Policy.SystemAdminOrExportService` with the P10 requirement sentence and an updated enum scaladoc; `AuthorizationMatrixE2ESpec.allowedBy` gets `SystemAdminOrExportService -> Some(Set(Root))` in the same commit (both matches are exhaustive); the four export entries stay in `exempt`
- [x] `ExportCaller` enum in `slice/security` (`Admin(user)`, `Service(principal)`)
- [x] `BaseEndpoints.bearerSystemAdminOrServicePolicyEndpoint`: bearer only, sets the policy, takes a `String => IO[E, ExportCaller]` and passes it to `zServerSecurityLogic`
- [x] `V3BaseEndpoint` gets the verifier as a constructor dependency; production wiring and the test call sites (`V3BaseEndpointSpec`, `OntologyMappingEndpointsSpec`, `BaseEndpointsSpec` where affected) are updated
- [x] `V3BaseEndpoint.systemAdminOrExportServicePolicy(errorOut)`: routes by exact header `alg` (P5); a rejected service token maps to the fixed 401 `Invalid token.`; the user branch is `handleBearerJwt` plus `ensureSystemAdmin`
- [x] `V3BaseEndpointSpec`: the new factory sets the policy on the completed endpoint; routing table: valid service token admitted, a valid-looking EdDSA token with no keys configured 401, invalid service token 401, unknown `kid` 401, malformed header 401, `alg` `eddsa` 401, an `HS256` token with a service `kid` takes the user path, non-admin user 403, SystemAdmin admitted
- [x] Test: a valid service token on a `systemAdminPolicy` v3 endpoint (the imports POST) and on a `BaseEndpoints` bearer endpoint gets 401
- [x] `EndpointPolicyE2ESpec`: the failure message lists the new factory
- [x] Build the e2e tests (`bazel build //modules/test-e2e:test`); they compile (the e2e suite itself runs once in the final review)
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 3: service callers on the export endpoints

- [x] `TaskCreator` enum in `slice/export/domain` (`ByUser(UserIri)`, `ByService(ServiceName)`) with the hand-written one-string codec and the explicit `Schema.string` of P7
- [x] `CurrentDataTask`, `PersistedDataTask`, `DataTaskState.makeNew` and `ProjectMigrationExportService.createExport` take a `TaskCreator`; the export log line names the creator; the import call sites (`ProjectMigrationImportService`, `ProjectDataImportService`, `V3ProjectsRestService`) pass `TaskCreator.ByUser`, keeping `onBehalfOf` separate
- [x] Existing specs that assert `createdBy` or call `makeNew`/`createExport` are updated (`V3ProjectsRestServiceSpec`, `ProjectMigrationExportServiceSpec`, `ProjectDataImportE2ESpec` and the others the compiler names)
- [x] `FilesystemDataTaskPersistence` test through the real read path: a literal task file in the old shape (`"createdBy":"http://rdfh.ch/users/..."`, with and without `onBehalfOf`) restores; a service task round-trips; a file whose `createdBy` is `urn:dsp:service:` with an invalid name is treated as corrupt
- [x] `DataTaskStatusResponse.createdBy` carries the `TaskCreator` string; a test renders the OpenAPI document with `OpenAPIDocsInterpreter` and asserts `createdBy` is `type: string` with no format or pattern
- [x] ~~New `V3ErrorCode.export_slot_occupied` (fixed message, no id or project) for P11, with its 409 variant on the trigger endpoint~~ (removed again: P11 revised by the owner on 2026-11-19)
- [x] Switch the four export endpoints in `V3ProjectsEndpoints` to `systemAdminOrExportServicePolicy`; descriptions end with the new requirement; imports and data imports stay `systemAdminPolicy`
- [x] `V3ProjectsServerEndpoints`: status, download and delete take the caller instead of dropping it
- [x] `V3ProjectsRestService`: trigger records the caller as creator and answers a service per P11; status, download and delete look the export up by id, then check ownership (P6) before any state check or deletion, without resolving the project
- [x] The ownership refusal is an internal outcome distinct from an unknown id (an internal error case mapped to the shared `404 export_not_found` at the edge of the RestService), so the audit of Phase 4 can tell `not_owner` from `not_found`
- [x] `V3ProjectsRestServiceSpec`: a service sees its own export; gets 404 for a user's and another service's export, also when that export is in progress (not 409) or failed; its delete of someone else's export leaves it in place; trigger with a user's export in the slot gives `export_exists` with its id (P11 revised by the owner on 2026-11-19); trigger with its own export in the slot gives `export_exists` with the id, and delete then re-trigger succeeds; for a failed own export, status works, download gives `export_failed`, delete works; an export created under key A is visible under key B of the same name; a SystemAdmin sees and deletes a service's export
- [x] Build the e2e tests (`bazel build //modules/test-e2e:test`); they compile (the e2e suite itself runs once in the final review)
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 4: audit log and span attributes

- [x] Every export call by a `Service` caller emits exactly one INFO line from `onExit` (success, failure, defect or interruption) with service name, `kid`, `jti`, action (`export.create`, `export.status`, `export.download`, `export.delete`), project IRI, export id when known, and the P12 outcome, taken from the internal outcome before it is mapped to the HTTP error; the project IRI is the task's when the export is found and the path value otherwise (both logged on `not_owner`); the helper is new code modelled on `observed` in `slice/api/admin/service/SparqlPassthroughRestService.scala`, which takes a `User` and is not reused
- [x] The same calls set `dsp.service.name`, `dsp.service.kid`, `dsp.service.action` and the project shortcode on the current server span via `Tracing` (the shortcode only when the project is known from the task or, on trigger, from the resolved project; never derived from the unvalidated path); no IRI, `jti` or export id as an attribute (P8); `V3ProjectsRestService` gets `Tracing` as a dependency
- [x] A rejected service token logs one WARN line with the P9 reason category; the raw token and an unconfigured `kid` are never logged
- [x] SystemAdmin calls keep their existing log line and gain no service attributes
- [x] Tests: the audit line is emitted once per service call with all fields, including on a failed call and on an ownership refusal (`not_owner`, while the response equals the unknown-id 404); the span carries exactly the four attributes; a rejected token logs its reason without the token
- [x] Run `just test-unit`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

#### Phase 5: end-to-end coverage and documentation

- [x] `modules/test-e2e` `application.conf` trusts a test public key (`app.service-identity.trusted-keys`, JSON in a HOCON string) whose private key is a committed test-only fixture used by the e2e signer (a deliberate deviation from decision 8's "generated in the test": the server reads its config at startup, before the test runs; unit tests still generate their keys)
- [x] `TestApiClient` overloads that send a raw bearer token for a POST without a body and for the zip download (the existing `getJson(uri, f)` overload covers GET)
- [x] `ExportServiceIdentityE2ESpec`: a service token triggers an export, polls it, downloads a valid zip and deletes it; the same token gets 401 on an import endpoint, an admin endpoint and a v2 endpoint; with a SystemAdmin export in the slot, the service trigger gets `export_exists` with its id and the service gets 404 on that export's status; a token with the wrong `aud` gets 401
- [x] `docs/adr/0014-service-identity-for-exports.md`: context, the settled decisions and P1–P12, consequences (a leaked private key exposes full project exports, password hashes included, on every server that trusts it, until the key is removed; no server-side project allowlist, so the audit log is the only after-the-fact control; `jti` not checked, so a token can be replayed within its lifetime; the download audit records the start of the stream; a rollback drops service task files), alternatives rejected
- [x] ADR-0013: add `SystemAdminOrExportService` to decision 4's table, linking ADR-0014
- [x] `docs/contexts/identity-access/CONTEXT.md`: terms **Service identity** and **Service token** (avoid: service user, machine user, technical user); extend **Policy**; note that a service identity is not a **User**
- [x] `docs/03-endpoints/api-v3/project-migration.md`: who may call which export endpoint, the token contract (header `alg`/`kid`, claims, `aud` normalisation, lifetime, recommended 5-minute `exp`), the 409 cases of P11 and how a client recovers (an own export still in progress answers `409 export_in_progress` on delete, so the client polls until it is terminal), and a curl example
- [x] Operator documentation for `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS` in `docs/03-endpoints/api-v3/project-migration.md` (a "Service identity" section, next to the token contract), or the existing deployment/configuration page if one documents `KNORA_WEBAPI_*` variables: format, key generation with openssl, rotation (add the new `kid`, switch the client, wait 15 minutes, remove the old `kid`), turning it off
- [x] `CONVENTIONS.md` and `docs/development/code-conventions.md`: the new factory in the factory list
- [x] `ARCH-MAP.md`: the new security entities under webapi-security, `ServiceName` under webapi-common
- [x] `ARCH-MAP.md` jwt entry (no longer HS256-only: `EdDsaJwt`, `JwtHeader.kid`/`crit`, the testonly `src/testkit` target), webapi-security (`Provided`, Depends on `KnoraApi`/`ServiceIdentityConfig`, wiring line), webapi-app (`ServiceIdentityConfig` in `AppConfigurations`); `docs/04-publishing-deployment/configuration.md` row for `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS` (default `[]`, JSON array format) (deferred from Phase 1 review)
- [x] Build the e2e tests (`bazel build //modules/test-e2e:test`); they compile (the e2e suite itself runs once in the final review)
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Generate the dsp-repository key pair, store the private key as a dsp-repository secret, set `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS` in ops-deploy for the servers that should be readable | DevOps / owner | after ship | Key custody and deployment config live outside this repo |
| H2 | Implement token minting in dsp-repository per the contract in the PR body | dsp-repository maintainer | after ship | Other repository, out of scope (decision 8) |

## Acceptance Criteria

- [ ] With no trusted keys configured, any EdDSA token gets 401 on every endpoint (verified at verifier and factory level; the e2e server always trusts a key)
- [ ] With a trusted key, a valid service token can trigger, poll, download and delete its own export
- [ ] A service token gets 404 on status, download or delete of an export it did not create, whatever that export's state
- [ ] A service triggering while any export holds the slot, its own or another caller's, gets `409 export_exists` with that export's id and project (revised by the owner on 2026-11-19)
- [ ] A service token gets 401 on every endpoint outside the four export endpoints
- [ ] Wrong `aud`, unknown `kid`, bad signature, expired token, lifetime above 15 minutes, `iss`/`sub` not matching the key's name: each 401
- [ ] SystemAdmin behaviour on the four export endpoints is unchanged, including access to service-created exports
- [ ] Task files written by the previous version restore unchanged
- [ ] `DataTaskStatusResponse.createdBy` stays a plain string in the OpenAPI spec (only its description changes)
- [ ] Each service call logs one INFO line with service name, `kid`, `jti`, action, project IRI, export id and outcome; an ownership refusal is logged as `not_owner`
- [ ] An export created under one key of a service stays accessible under another key of the same service
- [ ] Malformed trusted-key config fails startup with a message that does not echo key material
- [ ] `just test-unit`, `just check` and the e2e suite (`just test-e2e`) pass, including `EndpointPolicyE2ESpec` and `AuthorizationMatrixE2ESpec`

## Dependencies & Risks

- The client (dsp-repository) does not exist yet, so the token contract is proven only against tokens our tests sign.
  The PR body gives the contract precisely enough to build against (H2).
- A mis-normalised `aud` check would lock the client out on prod (`:443`). Covered by P2 and its test.
- Changing `createdBy` touches imports. Covered by the old-shape decode test and the unchanged string wire format.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Leaked private key exposes full exports (password hashes included) on every trusting server | L | H | 15-minute tokens; audit log per call; remove the `kid` to revoke; recorded in ADR-0014 |
| Algorithm confusion lets an HS256 or `none` token through the service path | L | H | `alg` pinned to EdDSA in the verifier; HS256 paths unchanged; tests for both |
| Old task file no longer decodes and is silently dropped | M | M | One-string codec keeps the old shape; literal old-shape test |
| Service reads a person's manual export | L | M | Ownership check, 404; unit and e2e tests |
| Span attribute cardinality | L | L | Only bounded values as attributes (P8) |

## Success Metrics

- All acceptance criteria hold and CI is green.
- A token minted by the test signer, following the PR body's contract, completes the export round trip in e2e.

## References

- Ticket: DEV-7936 (Decisions section)
- `docs/adr/0013-authorization-policy-enforcement.md`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/common/api/BaseEndpoints.scala:235-293`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/api/v3/V3BaseEndpoint.scala:74-108`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/api/v3/projects/V3ProjectsEndpoints.scala:74-149`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/api/v3/projects/V3ProjectsRestService.scala:70-115`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/export/domain/FilesystemDataTaskPersistence.scala:21-112`
- `modules/webapi/src/main/scala/org/knora/webapi/slice/infrastructure/JwtService.scala`
- `modules/jwt/src/main/scala/org/knora/jwt/JwtCodec.scala`
- `modules/webapi/src/main/scala/org/knora/webapi/config/AppConfig.scala:107-109` (external base URL)
- `modules/webapi/src/main/scala/org/knora/webapi/slice/api/admin/service/SparqlPassthroughRestService.scala:172-227` (audit pattern)
- `docs/observability/instrumentation-recipe.md`
- RFC 8037, RFC 8725, RFC 7519; JEP 339
- dasch-specs learnings: `design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`,
  `best-practices/shared-tapir-error-envelope-leaks-exception-messages.md`
