# Project Data Import

The project data import API creates a new project's data graph from a [knora-api (v2 external schema)](../api-v2/introduction.md)
JSON-LD payload. The server transforms the payload into the internal knora-base representation, validates it, and
streams it into the triplestore.

The data import handles **instance data only** — the resources and their values. The project and its ontologies
must already exist on the instance, and the project's data graph must not exist yet (create-only).

## Endpoints

For request/response schemas, error codes, and interactive testing, see the
[OpenAPI documentation](https://api.dasch.swiss/api/docs/#/API%20v3).

| Route                                                | Method   | Description                             |
| ---------------------------------------------------- | -------- | --------------------------------------- |
| `/v3/projects/{projectIri}/data-imports`             | `POST`   | Upload JSON-LD and trigger async import |
| `/v3/projects/{projectIri}/data-imports/{importId}`  | `GET`    | Poll import status                      |
| `/v3/projects/{projectIri}/data-imports/{importId}`  | `DELETE` | Delete a completed/failed import        |

## Authentication and Authorization

All endpoints require a valid **Bearer JWT token** and **SystemAdmin** permissions.

Requests from non-SystemAdmin users (e.g. project admins) are rejected with `403 Forbidden`.

## Feature Flag

The endpoints are gated behind the
[`allow-project-data-import` feature flag](../../04-publishing-deployment/configuration.md#allow-project-data-import),
which is disabled by default. When disabled, the endpoints return `404 Not Found`.

## Request Format

The request body is the project's data graph as JSON-LD in the knora-api v2 external (complex) schema, with content
type `application/ld+json`. The payload contains resources and their values only — no admin data, ontologies, or
permission data.

Resource and value metadata that is managed by the server is synthesised during the import and need not (and should
not) be supplied in the payload:

- `attachedToProject` is derived from the `{projectIri}` path parameter.
- `attachedToUser` is the authenticated user.
- `hasPermissions` is resolved once from the project's default object access permissions (DOAPs) as they apply to
  the authenticated user, and applied uniformly to every resource and value. Since the importer is always a system
  admin, the project's ProjectAdmin-group DOAP has first precedence. Per-resource-class and per-property DOAPs are
  not resolved per entity.
- Creation dates are set to the time of the import.

`@graph` declarations in the payload are ignored: all data is written exclusively into the project's data named
graph, which is derived from the project.

## Key Behaviors

The import is **asynchronous**. Triggering an import returns `202 Accepted` with a task ID.
Poll the status endpoint until `status` is `completed` or `failed`.
The `status` field is one of: `in_progress`, `completed`, `failed`.

The import is **create-only**: if the project already has a data graph, the request is rejected with
`409 Conflict` and error code `data_graph_exists`. The precondition is checked synchronously when the import is
triggered and re-verified immediately before the upload. Updating or extending an existing data graph is not
possible with this API.

Only **one data-graph import** can exist at a time. Attempting to create a second returns `409 Conflict` with the
existing task's `id` in the error details. Delete the previous task before triggering a new one. Data-graph imports
are tracked independently of migration imports and exports.

The upload to Fuseki is **atomic** — if the upload fails, no partial data is written to the triplestore.

## Import Validation

Before anything is written to the triplestore, the transformed data is SHACL-validated against the knora-base data
shapes. The project's ontologies are fetched from the triplestore to support the validation. Validation failure
fails the task with a descriptive error message and leaves the triplestore untouched.

## Typical Workflow

```bash
# Upload and trigger import
curl -s --request POST \
  --url 'https://server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/data-imports' \
  --header 'Authorization: Bearer <jwt>' \
  --header 'Content-Type: application/ld+json' \
  --data-binary @project-data.jsonld
# Response: {"id": "<importId>", "status": "in_progress", ...}

# Poll status until completed
curl -s --request GET \
  --url 'https://server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/data-imports/<importId>' \
  --header 'Authorization: Bearer <jwt>'
# Response: {"status": "completed", ...}

# Cleanup
curl --request DELETE \
  --url 'https://server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/data-imports/<importId>' \
  --header 'Authorization: Bearer <jwt>'
```

## Limitations

- **Project and ontologies must already exist**: The import assumes class and property IRIs in the payload resolve
  against ontologies already in the triplestore.
- **Create-only**: Re-importing requires deleting the project's data graph first, which is out of scope for this API.
- **No assets**: Binary assets are not part of the import; only RDF data is handled.
- **JSON-LD only**: Other RDF serializations are not accepted.
- **Single instance**: Task state is held in memory per instance (same constraint as migration import/export).
