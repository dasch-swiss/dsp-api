# Project Migration Export/Import

The project migration API allows exporting a complete project from one DSP instance and importing it into another instance where the project does not yet exist. This is used for migrating projects between servers (e.g. from a testserver to production).

A complete project includes the project record, its ontologies, all resources and values, permissions, group definitions, and referenced users. Binary assets (files managed by dsp-ingest) can optionally be included. The export is self-contained — cross-project references (memberships in other projects, groups from other projects) are stripped so the import does not depend on external state.

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

All endpoints require a valid **Bearer JWT token** and **SystemAdmin** permissions.

Requests from non-SystemAdmin users (e.g. project admins) are rejected with `403 Forbidden`.

## Feature Flag (Import Only)

The import endpoints are gated behind the `allow-import-migration-bagit` feature flag.
When disabled, import endpoints return `404 Not Found`.

The flag can be set via the environment variable `ALLOW_IMPORT_MIGRATION_BAGIT`
or in `application.conf`:

```
app.features.allow-import-migration-bagit = true
```

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

Only **one export task and one import task** can exist simultaneously per project. Delete the previous task before triggering a new one. Attempting to create a second returns `409 Conflict` with the existing task's `id` in the error details.

The import endpoint requires the `{projectIri}` in the URL to match the `External-Identifier` in the BagIt `bag-info.txt`. The request body is the zip file with content type `application/zip`.

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

The import validates both ontology and data files using SHACL shapes:

**Ontology shapes** check that:

- Each project ontology has 1-5 `rdfs:label` values with valid language tags (`en`, `de`, `fr`, `it`, `rm`), unique per language, and single-line.
- Each project ontology is attached to the correct project IRI and has a `lastModificationDate`.
- Each resource class and property has 1-5 labels with valid language tags.

**Data shapes** check that:

- Each resource has `rdfs:label`, `isDeleted`, `attachedToUser`, `attachedToProject` (matching the import project IRI), `hasPermissions`, and `creationDate`.
- Each value has `valueCreationDate`, `attachedToUser`, `isDeleted`.
- Each `LinkValue` has `rdf:subject`, `rdf:predicate`, `rdf:object`, and `valueHasRefCount`.
- Every `attachedToUser` reference points to a `knora-admin:User` node present in the admin data.

SHACL validation is memory-intensive. Midsized projects (e.g. BEOL) require 3–4 GB of available RAM on the server to complete validation successfully.

### User Handling

The import handles users referenced in the export with the following logic. User merging is always additive — existing group memberships or user profile data (e.g. name) on the target instance will never be overwritten.

**Built-in users** (SystemUser, AnonymousUser) are stripped from the admin data since they already exist on every instance.

**For each remaining user**, the import looks up by IRI, email, and username:

| Lookup Result | Behavior |
| --- | --- |
| **Found by IRI** | Verifies that email and username match the existing user. Logs warnings for profile differences (e.g. name). Strips the user's profile triples from the export data and keeps only memberships scoped to the imported project (e.g. `isInProject`, `isInGroup`). Because the triplestore upload is additive, the user's existing data and memberships in other projects on the target instance are not affected. |
| **Not found at all** | Creates the user as a new user. Strips `isInSystemAdminGroup` (set to `false`). Removes any cross-project memberships. |
| **No IRI match, but email or username collision** | Fails with an error message identifying the conflict. |

**Root user**: If the export contains the root user (either as an existing user match or as a new user), the import **fails**. Resources referencing the root user require pre-migration cleanup before import. See [Root User Cleanup](../../10-migration-guides/root-user-cleanup.md) for instructions.

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
- **One task at a time**: Only one export and one import can exist per project. Delete the previous task before triggering a new one.
- **Root user references**: Projects where resources or values reference the root user cannot be imported directly. The references must be reassigned before export.
- **Assets**: Asset export/import is handled via dsp-ingest. If dsp-ingest has no assets for the project, the export continues without them.
