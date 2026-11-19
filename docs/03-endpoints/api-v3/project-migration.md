# Project Migration Export/Import

The project migration API allows exporting a complete project from one DSP instance and importing it into another instance where the project does not yet exist. This is used for migrating projects between servers (e.g. from a testserver to production).

A complete project includes the project record, its ontologies, all resources and values, permissions, group definitions, and referenced users. Binary assets (files stored by [ingest](../ingest.md)) can optionally be included. The export is self-contained — cross-project references (memberships in other projects, groups from other projects) are stripped so the import does not depend on external state.

## Endpoints

For request/response schemas, error codes, and interactive testing, see the
[OpenAPI documentation](https://api.dasch.swiss/api/docs/#/API%20v3).

| Route | Method | Description |
| --- | --- | --- |
| [`/v3/projects/{projectIri}/exports`](https://api.dasch.swiss/api/docs/#/API%20v3/postV3ProjectsProjectiriExports) | `POST` | Trigger an async export |
| [`/v3/projects/{projectIri}/exports/{exportId}`](https://api.dasch.swiss/api/docs/#/API%20v3/getV3ProjectsProjectiriExportsExportid) | `GET` | Poll export status |
| [`/v3/projects/{projectIri}/exports/{exportId}`](https://api.dasch.swiss/api/docs/#/API%20v3/deleteV3ProjectsProjectiriExportsExportid) | `DELETE` | Delete a completed/failed export |
| [`/v3/projects/{projectIri}/exports/{exportId}/download`](https://api.dasch.swiss/api/docs/#/API%20v3/getV3ProjectsProjectiriExportsExportidDownload) | `GET` | Download the export zip |
| [`/v3/projects/{projectIri}/imports`](https://api.dasch.swiss/api/docs/#/API%20v3/postV3ProjectsProjectiriImports) | `POST` | Upload zip and trigger async import |
| [`/v3/projects/{projectIri}/imports/{importId}`](https://api.dasch.swiss/api/docs/#/API%20v3/getV3ProjectsProjectiriImportsImportid) | `GET` | Poll import status |
| [`/v3/projects/{projectIri}/imports/{importId}`](https://api.dasch.swiss/api/docs/#/API%20v3/deleteV3ProjectsProjectiriImportsImportid) | `DELETE` | Delete a completed/failed import |

## Authentication and Authorization

All endpoints require a valid **Bearer JWT token** and **SystemAdmin** permissions, with one exception: the four
export endpoints also accept a token of a trusted export service, which only sees the exports it created (see
[Service identity](#service-identity)).

Requests from non-SystemAdmin users (e.g. project admins) are rejected with `403 Forbidden`.

| Endpoint | SystemAdmin | Trusted export service |
| --- | --- | --- |
| Trigger an export | yes | yes |
| Export status, download, delete | every export | only exports it created; any other export answers `404 export_not_found` |
| Imports (trigger, status, delete) | yes | no (`401`) |

## Service identity

A service identity lets a machine client, such as dsp-repository's sync, export projects without a user account.
The service holds an Ed25519 private key and signs its own short-lived tokens; the server trusts only the public
keys configured in
[`KNORA_WEBAPI_SERVICE_IDENTITY_TRUSTED_KEYS`](../../04-publishing-deployment/configuration.md#service-identity),
and trusts none by default. A service identity is not a user: its token is accepted by the four export endpoints
only and gets `401` on every other endpoint, imports included. There is no per-project restriction: a trusted
service may export any project.

### Token contract

The token is a JWT signed with Ed25519 (RFC 8037), sent as `Authorization: Bearer <token>`. Every part is unpadded
base64url.

- **Header:** `alg` exactly `EdDSA` and `kid` naming one of the server's trusted keys. A `crit` parameter is
  rejected; `jku`, `jwk`, `x5u` and `x5c` are ignored.
- **`iss` and `sub`:** both the service name configured for that key.
- **`aud`:** this server's external base URL, a string or an array containing it. Both sides are compared after
  normalisation: scheme and host in lower case, the scheme's default port (443 for `https`, 80 for `http`) and a
  trailing slash dropped. `https://api.dasch.swiss` therefore matches a server configured with port 443, while a
  path, query or user part never matches.
- **`iat`, `exp`, `jti`:** all three required, `jti` non-blank. `exp - iat` must be at most 15 minutes. The server
  allows 30 seconds of clock skew: the token is rejected once `now >= exp + 30 s` or while `iat > now + 30 s`.
  Set `exp` five minutes after `iat` and mint a fresh token, with a fresh `jti`, per run. `nbf` is not checked.

A rejected token always gets the same `401` with the message `Invalid token.`; the reason is only logged on the
server (see [Auditing and logs](#auditing-and-logs)). `jti` is logged, not checked for reuse, so a token can be
replayed until it expires: keep it as secret as the key.

Minting a token with OpenSSL 3 (LibreSSL lacks Ed25519):

```bash
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
NOW=$(date +%s)
HEADER=$(printf '{"alg":"EdDSA","typ":"JWT","kid":"dsp-repository-2026-11"}' | b64url)
PAYLOAD=$(printf '{"iss":"dsp-repository","sub":"dsp-repository","aud":"https://api.dasch.swiss","iat":%d,"exp":%d,"jti":"%s"}' \
  "$NOW" "$((NOW + 300))" "$(uuidgen)" | b64url)
printf '%s.%s' "$HEADER" "$PAYLOAD" > signing-input
SIG=$(openssl pkeyutl -sign -rawin -inkey dsp-repository-2026-11.pem -in signing-input | b64url)
TOKEN="$HEADER.$PAYLOAD.$SIG"

curl -s --request POST \
  --url 'https://api.dasch.swiss/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/exports' \
  --header "Authorization: Bearer $TOKEN"
```

An export a service created reports `createdBy` as `urn:dsp:service:<name>` in its status; a SystemAdmin's
export reports the user's IRI.

### Conflicts and recovery

The export slot is server-wide, so a service shares it with SystemAdmins and other services.

- **`409 export_exists`** on trigger: the slot holds an export, the service's own or anyone else's, and the error
  details carry its `id` and project. Check that export's status:
    - If the status answers, the export is the service's own. This is how a service recovers after a crash: poll
      that export until it is `completed` or `failed`, delete it, and trigger again.
    - If the status answers `404 export_not_found`, the export is someone else's. Give up for this run and retry on
      the next one; do not loop.
- **`409 export_in_progress`** on delete or download of an own export that is still running: poll the status until
  it is `completed` or `failed`, then delete. Downloading a failed export answers `409 export_failed`.
- **`404 export_not_found`** on status, download or delete: there is no such export, or it is not the service's.
  The two cases are answered identically.

### Auditing and logs

Every export call by a service writes exactly one INFO line when the call ends, whether it succeeded or failed:

```text
Export service call: action=<action> outcome=<outcome> service=<name> kid=<kid> project_iri=<iri> requested_project_iri=<iri> export_id=<id> jti="<jti>"
```

- `action` is `export.create`, `export.status`, `export.download` or `export.delete`.
- `outcome` is `ok`, `not_owner`, `not_found`, `conflict` or `error`. `not_owner` and `not_found` give the client
  the same `404`; only the log tells them apart. A download is `ok` once its response stream has started, even if
  the transfer is cut off later.
- `project_iri` is the export's project when the export was found, otherwise the requested one;
  `requested_project_iri` is always the IRI from the path. `export_id` is the export's id, or the requested one;
  it is `-` only on a trigger that created no export.
- `jti` comes last, quoted, cut to 256 characters, with control, line-separator and format characters replaced by
  spaces.

A request whose path does not decode (a malformed project IRI or export id) is rejected before this logging and
writes no line. SystemAdmin calls are logged as before.

The same calls set these attributes on the request's trace span: `dsp.service.name`, `dsp.service.kid`,
`dsp.service.action`, and `dsp.project.shortcode` when the project is known (the resolved project on trigger, the
project of the service's own export otherwise; never on a refused lookup). IRIs, export ids and `jti` are never span attributes.

Every rejected service token writes one WARN line, never the token itself:

```text
Service token rejected: reason=<category> kid=<kid or ->
```

The `kid` is shown only when it names a configured key. Only a token whose header parses and whose `alg` is exactly
`EdDSA` is treated as a service token; any other token is handled as a user's token and gets `401` without this
line. The categories:

| Reason | Cause |
| --- | --- |
| `disabled` | No key is configured on this server. |
| `unknown_kid` | The `kid` is missing or names no configured key. |
| `bad_signature` | The token is not a valid EdDSA JWS: a malformed part, a `crit` header, bad encoding, an unreadable payload or a signature that does not verify. |
| `bad_claims` | `iss` or `sub` is not the key's name; `iat`, `exp` or `jti` is missing; `exp` is before `iat`; or `iat` is more than 30 seconds ahead. |
| `bad_audience` | `aud` does not name this server. |
| `lifetime_too_long` | `exp - iat` exceeds 15 minutes. |
| `expired` | `exp` passed more than 30 seconds ago. |

## Feature Flag (Import Only)

The import endpoints are gated behind the
[`allow-import-migration-bagit` feature flag](../../04-publishing-deployment/configuration.md#allow-import-migration-bagit).
When disabled, import endpoints return `404 Not Found`.

Export endpoints are always available and not gated by a feature flag.

## Export Format

Exports are packaged as [BagIt](https://www.rfc-editor.org/rfc/rfc8493) zip archives containing:

```
bagit.txt
bag-info.txt
manifest-sha256.txt
tagmanifest-sha256.txt
data/
  rdf/
    ontology-1.nq      # Project ontologies (one file per ontology)
    ontology-2.nq
    data.nq             # Project data graph (resources, values, links)
    admin.nq            # Admin data (project record, groups, users)
    permission.nq       # Permission data
  assets/
    assets.zip          # Binary assets (optional)
```

All RDF data is serialized as N-Quads and uses the internal knora-base schema.

Within the archive, the `data/rdf/` N-Quads are DEFLATE-compressed, while `data/assets/assets.zip` is stored
without effective compression: its contents are already-compressed media, so re-compressing them only wastes
CPU. This is why `assets.zip` does not shrink relative to its source.

The `bag-info.txt` file includes metadata fields:

| Field                  | Description                                     |
| ---------------------- | ----------------------------------------------- |
| `Source-Organization`  | Always `DaSCH Service Platform`                 |
| `External-Identifier`  | The project IRI                                 |
| `Bagging-Date`         | Date the export was created                     |
| `KnoraBase-Version`    | The knora-base ontology version (integer)       |
| `Dsp-Api-Version`      | The dsp-api version that created the export     |
| `Source-Server`        | The hostname of the source server               |

## Key Behaviors

All operations are **asynchronous**. Triggering an export or import returns `202 Accepted` with a task ID.
Poll the status endpoint until `status` is `completed` or `failed`.
The `status` field is one of: `in_progress`, `completed`, `failed`.

Only **one export task and one import task** can exist at a time — the slots are server-wide, not per project. Delete the previous task before triggering a new one. Attempting to create a second returns `409 Conflict` with the existing task's `id` and the owning project's IRI in the error details.

Only **one import runs at a time** on this server, whether it is a bulk import or a migration import. A second import
is rejected with `409 Conflict` and error code `import_running`. The slot check runs before the `import_exists` check,
and the request validation checks of the endpoint (`400`, `404`) still run before it. The error body and the
difference between the slot and the task's lifetime are described in
[Project Data Import](project-data-import.md#key-behaviors).

Export status, delete and download resolve the task by its `exportId` alone; the `{projectIri}` in the path is not used for the lookup and does not have to resolve to an existing project. An export therefore stays inspectable and deletable after its project has been erased, which is what keeps the export slot from getting stuck.

The import endpoint requires the `{projectIri}` in the URL to match the `External-Identifier` in the BagIt `bag-info.txt`. The request body is the zip file with content type `application/zip`.

An import `POST` that gets a non-2xx response and declares a `Content-Length` above 1 MiB closes the connection (`Connection: close`), because the unread body is still on the wire. Open a new connection for the next request.

Exports can optionally exclude assets via `?skipAssets=true`.

Downloads are only available for completed exports. Attempting to download an in-progress or failed export returns `409 Conflict`.

The RDF data upload to Fuseki is **atomic** — all NQ files are streamed as a single POST request. If the upload fails, no partial data is written to the triplestore.

## Import Validation

The import performs several layers of validation before writing any data to the triplestore.

### BagIt Integrity

The BagIt package is validated against its manifest checksums. Corrupt or incomplete archives are rejected.

### Version Compatibility

The `KnoraBase-Version` in the bag must exactly match the target instance. A mismatch **fails the import**.
A `Dsp-Api-Version` mismatch only produces a warning in the server logs and does not block the import.

### Project and Group Uniqueness

The import fails if:

- A project with the same IRI already exists on the target instance.
- A project with the same shortcode already exists on the target instance.
- Any group defined in the export already exists on the target instance.

### SHACL Validation

The import validates all files using SHACL shapes. It validates in two steps:

1. The ontology files are validated against the ontology shapes.
2. The data files (`admin.nq`, `data.nq`, `permission.nq`) are validated against `data-shapes.ttl` and the migration-mode shapes.

The ontology check verifies that:

- Each project ontology has 1-5 `rdfs:label` values with valid language tags (`en`, `de`, `fr`, `it`, `rm`), unique per language, and single-line.
- Each project ontology is attached to the correct project IRI and has a `lastModificationDate`.
- Each resource class and property has 1-5 labels with valid language tags.

The data check has these consequences:

- A bag is rejected if a resource or value is attached to the built-in SystemUser or AnonymousUser.
- A bag is rejected if a resource or value is attached to a user that is not in the bag's `admin.nq`.
- A previous value needs no `hasPermissions`.

Each NQuad file must also be valid N-Quads and contain exactly one named graph.

### User Handling

The import handles users referenced in the export with the following logic. User merging is always additive — existing group memberships or user profile data (e.g. name) on the target instance will never be overwritten.

**Built-in users** (SystemUser, AnonymousUser) are stripped from the admin data since they already exist on every instance.

**For each remaining user**, the import looks up by IRI, email, and username:

| Lookup Result | Behavior |
| --- | --- |
| **Found by IRI** | Verifies that email and username match the existing user. Logs warnings for profile differences (e.g. name). Strips the user's profile triples from the export data and keeps only memberships scoped to the imported project (e.g. `isInProject`, `isInGroup`). Because the triplestore upload is additive, the user's existing data and memberships in other projects on the target instance are not affected. |
| **Not found at all** | Creates the user as a new user. Strips `isInSystemAdminGroup` (set to `false`) — defense-in-depth, since the export already sets the flag to `false` (see *System administrators* below). Removes any cross-project memberships. |
| **No IRI match, but email or username collision** | Fails with an error message identifying the conflict. |

**Root user**: If the export contains the root user (either as an existing user match or as a new user), the import **fails**. Resources referencing the root user require pre-migration cleanup before import. See [Root User Cleanup](../../10-migration-guides/root-user-cleanup.md) for instructions.

**System administrators**: Project members or project admins who also belong to the SystemAdmin group on the source instance are included in the export, but the `isInSystemAdminGroup` flag is set to `false` in the exported admin data. System-admin membership is a property of the source instance and does not carry over to the target instance.

**Cross-project membership scoping**: Both export and import strip cross-project membership triples to ensure the package is self-contained:

- `isInProject` references to other projects are removed.
- `isInGroup` references to groups not belonging to the exported project are removed.
- `isInProjectAdminGroup` references to other projects are removed.

### Permissions

Permission data from `permission.nq` is preserved as-is during import. No transformation or rewriting is applied. The export already scopes permissions to the exported project, so the imported permissions match the source instance exactly.

## Typical Workflow

A typical migration workflow using `curl`:

### 1. Export from Source Server

```bash
# Trigger export
curl -s --request POST \
  --url 'https://source-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/exports' \
  --header 'Authorization: Bearer <source-jwt>'
# Response: {"id": "<exportId>", "status": "in_progress", ...}

# Poll status until completed
curl -s --request GET \
  --url 'https://source-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/exports/<exportId>' \
  --header 'Authorization: Bearer <source-jwt>'
# Response: {"status": "completed", ...}

# Download
curl --request GET \
  --url 'https://source-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/exports/<exportId>/download' \
  --header 'Authorization: Bearer <source-jwt>' \
  --output project-export.zip
```

### 2. Import to Target Server

```bash
# Upload and trigger import
curl -s --request POST \
  --url 'https://target-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/imports' \
  --header 'Authorization: Bearer <target-jwt>' \
  --header 'Content-Type: application/zip' \
  --data-binary @project-export.zip
# Response: {"id": "<importId>", "status": "in_progress", ...}

# Poll status until completed
curl -s --request GET \
  --url 'https://target-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/imports/<importId>' \
  --header 'Authorization: Bearer <target-jwt>'
# Response: {"status": "completed", ...}
```

### 3. Cleanup

```bash
# Delete export on source server
curl --request DELETE \
  --url 'https://source-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/exports/<exportId>' \
  --header 'Authorization: Bearer <source-jwt>'

# Delete import on target server
curl --request DELETE \
  --url 'https://target-server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/imports/<importId>' \
  --header 'Authorization: Bearer <target-jwt>'
```

## Limitations

- **No cross-version compatibility**: Export and import require the same `KnoraBase-Version`.
- **Project must not exist on target**: The import fails if the project IRI or shortcode already exists.
- **One task at a time**: Only one export and one import can exist server-wide. Delete the previous task before triggering a new one.
- **One import at a time**: An import is rejected with `import_running` while another import, of either kind, runs on this server. See [Project Data Import](project-data-import.md#key-behaviors).
- **No payload size limit**: The migration import accepts a bag of any size. A very large bag can exhaust the API heap during validation, because the data files load into the validation model.
- **Root user references**: Projects where resources or values reference the root user cannot be imported directly. The references must be reassigned before export.
- **Assets**: Assets are exported and imported by ingest, which runs inside DSP-API. If ingest has no assets for the project, the export continues without them.
