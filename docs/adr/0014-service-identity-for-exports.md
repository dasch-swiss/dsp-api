# ADR-0014: A service identity that is not a user, for machine-to-machine project exports

Date: 2026-11-19

Relates to: DEV-7936, [ADR-0013](0013-authorization-policy-enforcement.md) (the `Policy` ADT this extends),
`modules/jwt` (`EdDsaJwt`), `slice/security/ServiceTokenVerifier.scala` and `TrustedServiceKeys.scala`,
`slice/api/v3/V3BaseEndpoint.scala` (token routing), `slice/api/v3/projects/V3ProjectsRestService.scala` and
`ExportServiceAudit.scala` (ownership and audit), `slice/export/domain/TaskCreator.scala`, the Identity & Access
context (`docs/contexts/identity-access/CONTEXT.md`), the project migration endpoints
([project-migration.md](../03-endpoints/api-v3/project-migration.md)).

## Status

Proposed

## Context

dsp-repository's nightly sync must export full project data from any dsp-api server. Every export route requires a
SystemAdmin user's token, and no machine credential exists. The HS256 secret that signs user tokens cannot be
handed out: whoever holds it mints SystemAdmin tokens for every user on that server. A SystemAdmin service account
per server was ruled out too: it would need an account on each server, and a SystemAdmin can do everything, not
just exports.

What the sync needs is narrow: trigger an export of a project, poll it, download the zip, delete it. It must not
see or touch anything else, including other callers' exports in the server-wide export slot.

## Decision

1. **A service identity is not a user.** A trusted service is named by a `ServiceName` (lower-case letters, digits,
   hyphens) and authenticates with an Ed25519-signed JWT (`alg: EdDSA`, RFC 8037) that it mints for itself with its
   own private key. dsp-api holds public keys only and never signs a service token. No user is looked up, and the
   token's `sub` is never read as a user IRI.

2. **Off by default; keys come from configuration.** `app.service-identity.trusted-keys` (environment variable
   `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS`) holds a JSON array of `{name, kid, publicKey}`, where `publicKey`
   is the base64 X.509 SubjectPublicKeyInfo of an Ed25519 key, with or without PEM armour. The default `[]` trusts
   no key; an empty or blank value means the same, because a declared but unset variable renders as `""`. A
   malformed array, an unparsable or non-Ed25519 key, a blank `kid`, an invalid name or a duplicate `kid` fails
   startup with a message naming the entry index and field, never key material. Several keys may share a name, so
   a service can rotate with an overlap. At startup one INFO line lists the number of keys with their names and
   `kid`s. The setting is parsed in `TrustedServiceKeys` (`slice/security`), not in `AppConfig`'s validation chain,
   which would make the configuration component depend on security.

3. **The token contract.** Header `alg` exactly `EdDSA` and a `kid` naming a configured key; `crit` is rejected and
   `jku`, `jwk`, `x5u`, `x5c` are ignored. Each part is unpadded base64url; the signature is 64 bytes, checked over
   the received `header.payload`. Claims: `iss` and `sub` both equal the key's name, so a key cannot claim another
   service's name; `aud` (a string or an array) contains this server's external base URL; `iat`, `exp` and a
   non-blank `jti` are present; `exp - iat` is at most 15 minutes, with no leeway; the token is current within 30
   seconds of leeway (`now < exp + 30 s`, `iat <= now + 30 s`). `aud` is compared after normalisation on both
   sides: lower-case scheme and host, the scheme's default port and a trailing slash dropped, anything beyond
   scheme, host and port matching nothing. A server configured as `https`, host `api.dasch.swiss`, port 443 thus
   accepts `aud: "https://api.dasch.swiss"`. `nbf` is not checked; the lifetime cap bounds the token. Bearer only.

4. **One policy, routed by the header.** A new `Policy.SystemAdminOrExportService` (decision 4 of ADR-0013)
   admits active SystemAdmins and trusted export services. Its factory
   `BaseEndpoints.bearerSystemAdminOrServicePolicyEndpoint` sets the policy; the routing function
   `V3BaseEndpoint.systemAdminOrExportServicePolicy` peeks at the unverified header. When `alg` is exactly `EdDSA`
   the token goes to `ServiceTokenVerifier` and never reaches the user authenticator; otherwise the user path runs
   and the user must be a SystemAdmin. The handler receives an `ExportCaller`: `Admin(user)` or
   `Service(principal)`. Every other endpoint rejects a service token with 401, because the HS256 decoder cannot
   verify an EdDSA signature; ingest included. Only that routing function calls `ServiceTokenVerifier`.

5. **What a service may do.** Only the four project export endpoints use the new policy: trigger, status, download
   and delete. Imports and data imports stay `SystemAdmin`. There is no server-side project allowlist: a service
   may export any project.

6. **A service sees only the exports it created.** An export's creator is a `TaskCreator`, a user IRI or a service
   name. In the task file and in `DataTaskStatusResponse.createdBy` it is one string: the user IRI as before, or
   `urn:dsp:service:<name>`; task files written by earlier versions load unchanged. Status, download and delete look
   the export up by id alone (never resolving the project, see DEV-6766), then check ownership before any state
   check or deletion. A service asking for an export it did not create gets `404 export_not_found`, the answer for
   an unknown id, so it cannot read, download or delete it, nor learn its state. Ownership is keyed on the service
   name, never the `kid`, so an export survives a key rotation. SystemAdmins keep access to every export. The rule
   lives in `V3ProjectsRestService` (`findExport` and `mayAccess`); the `ExportCaller` matches there are exhaustive.

7. **An occupied slot.** The export slot is server-wide. When a service triggers an export while the slot holds any
   export, its own or anyone else's, it gets `409 export_exists` with that export's id and project, exactly as a
   SystemAdmin does. There is no separate code that hides the occupant: exporting is reserved for SystemAdmins and
   trusted services, so telling a service another caller's export id and project is not a leak worth one. With its
   own export in the slot, the service recovers after a crash by polling, deleting and triggering again. When the
   export is not its own, its status answers `404` (rule 6), and the client is told to give up for this run and
   retry on the next one, never loop.

8. **Every service call is audited.** Each export call by a service writes exactly one INFO line when it ends,
   whether it succeeded, failed, died or was interrupted:

    ```text
    Export service call: action=export.status outcome=not_owner service=dsp-repository kid=dsp-repository-2026-11
    project_iri=<the export's project> requested_project_iri=<the path value> export_id=<id> jti="<jti>"
    ```

    (one line in the log). `action` is `export.create`, `export.status`, `export.download` or `export.delete`;
    `outcome` is `ok`, `not_owner`, `not_found`, `conflict` or `error`, so operators can tell an ownership refusal
    from an unknown id although the client gets the same 404. `project_iri` is the export's project when the export
    was found, else the requested one; `requested_project_iri` is always the path value. An unknown field is `-`;
    `export_id` is `-` only on a trigger that created none. `jti` is the only freely chosen value, so it comes last,
    quoted, cut to 256 characters, with control, line-separator and format characters replaced. The same calls set
    four bounded span attributes on the server span: `dsp.service.name`, `dsp.service.kid`, `dsp.service.action`
    and `dsp.project.shortcode` from the resolved project on trigger or the service's own export otherwise. No IRI, `jti` or
    export id becomes an attribute, because spanmetrics can turn attributes into labels. SystemAdmin calls are
    logged as before and get no service attributes.

9. **Rejected service tokens are logged.** Each rejection writes one WARN line
   `Service token rejected: reason=<category> kid=<kid or ->`. The categories: `disabled` (no key configured,
   checked first), `bad_signature` (the token fails as a JWS: a malformed part, `crit`, bad encoding or
   signature), `unknown_kid` (also a missing `kid`), `bad_claims` (`iss` or `sub` not the key's name; `iat`, `exp`
   or `jti` missing; `exp` before `iat`; `iat` more than 30 seconds ahead), `bad_audience`, `lifetime_too_long`,
   `expired`. The `kid` is logged only once it matched a configured key; the token is never logged. The client
   always gets the same `401 Invalid token.`. A token whose header does not parse or whose `alg` is not exactly
   `EdDSA` never reaches the verifier: it takes the user path and gets `401` with no such line. Rate limiting stays
   with the reverse proxy.

10. **The export format is unchanged.** A service receives the same zip as a SystemAdmin, password hashes included
    by design.

## Consequences

**Exposure of a leaked key.** A leaked private key exports any project in full, password hashes included, on every
server that trusts its public key, until the key is removed from that server's configuration. No project allowlist
narrows this, so the audit log is the only after-the-fact control. The key never leaves the service, and dsp-api
holds no secret that could leak it. Removing a key takes effect only when each server restarts without it, and
exports belong to the name, so after a leak the operator also deletes the exports the name still holds and reviews
the audit log since the leak.

**The configuration is the trust root.** Whoever can change `KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS` can grant
export of every project, the equivalent of a SystemAdmin credential. The E2E tests trust a key whose private half
is committed to the repository; it must never be added to a real server, and nothing but review prevents that.

**Replay.** `jti` is logged, not checked: a captured token can be replayed until it expires, at most 15 minutes
after `iat` plus 30 seconds of leeway. The client is asked to set `exp` five minutes after `iat`. `aud` limits replay
to one server only as long as servers have distinct external base URLs.

**Limits of the audit.** A download is logged when its response stream starts, so a download cut off mid-stream is
still `ok`. `error` covers both defects and interruptions such as a client disconnect. A call whose path fails to
decode (a malformed project IRI or export id) never reaches the handler and writes no audit line; only the
server's request log sees it.

**Information a service can still learn.** On trigger, an unknown project IRI answers `404 project_not_found`, a
known one 202 or 409, so a service can tell existing project IRIs from unknown ones. That follows from having no
project allowlist. Ownership by name assumes one operator per service name: two services given the same name in
the configuration share their exports. A service, or anyone holding its key, can keep the server-wide export slot
occupied with its own exports and so lock out every other caller until a SystemAdmin deletes them.

**Rollback.** A dsp-api version from before this decision cannot decode a task file whose creator is
`urn:dsp:service:<name>` and drops it as corrupt, leaving the export directory to be cleaned by hand.

**What enforces the rules.** The policy is declared and checked like every ADR-0013 policy; the four export
endpoints stay in the `exempt` list of `V3SystemAdminRoutesE2ESpec`, and `ExportServiceIdentityE2ESpec` covers them
with a service token. The rest rests on tests and review:

- Only `V3BaseEndpoint`'s routing function calls `ServiceTokenVerifier`. Nothing stops another slice from
  injecting it through `SecurityModule`; review does.
- Ownership is enforced in `V3ProjectsRestService`, while `ProjectMigrationExportService.getExportStatus`,
  `downloadExport` and `deleteExport` take a bare export id. A new handler on the export policy that skipped
  `findExport`, or the audit wrapper, would compile. The `V3ProjectsRestService` tests pin today's four handlers.
  A structural guard, such as a proof of ownership that those methods require, is not built.
- A new `ExportCaller` case must extend `creatorOf` and `mayAccess`; both matches are exhaustive, so the compiler
  names them.

**Open points.**

- *Log level of rejected tokens.* Every rejection is logged at WARN. Anyone can send a token with `alg: EdDSA` and
  so write one WARN line per request, also when no key is configured (`disabled`). The alternative is a lower level
  for `disabled`, `unknown_kid` and `bad_signature`, the rejections before the sender is shown to hold a key, or
  sampling.
- *Location of `ServiceName`.* It lives in `slice/common/domain`, so the export domain can use it without depending
  on `slice/security`. The alternative is `slice/security`, which would avoid an edge from security into the shared
  kernel that the modularization dissolves.
- *Verifier and ownership guards.* The single caller of `ServiceTokenVerifier` and the ownership check are enforced
  by review and tests only. A test that fails when a new file references `ServiceTokenVerifier` or
  `EdDsaJwt.verify`, and an ownership proof that the export service's methods require, would raise both to a check.

## Alternatives rejected

- **HS256 with a per-server shared secret.** The cheapest shape, reusing `JwtCodec`, but the secret mints SystemAdmin
  user tokens on that server, and every server would need its own client secret.
- **A SystemAdmin service account per server.** An account on each server, and a SystemAdmin can do everything.
- **The JWT `scope` claim on user tokens.** Still a user, and ADR-0013 keeps scope apart from authorization.
- **A JWT library** (jwt-scala, nimbus). The JDK verifies Ed25519 natively (JEP 339) and `modules/jwt` is the leaf
  for JWT mechanics; a library would add dependencies to every image for about a hundred lines.
- **A JSON object for a service creator** in `createdBy`. It would break the response's string type and the
  generated OpenAPI client; a second optional field would leave `createdBy` meaningless for service tasks.
