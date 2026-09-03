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

Triggering an import requires a valid **Bearer JWT token** and **SystemAdmin** permissions. Requests from
non-SystemAdmin callers (e.g. project admins) are rejected with `403 Forbidden`.

Imported data is attributed to the `onBehalfOfUser`, not the triggering admin. That user is validated synchronously,
before any `202`/write:

- An unknown email or username is rejected `404 Not Found` (`on_behalf_of_user_not_found`).
- A user that is a system admin, not a member or admin of the project, inactive, or without project-wide create
  rights is rejected `400 Bad Request` (`on_behalf_of_user_ineligible`), with a `reason` in the error details
  (`is_system_admin`, `not_project_member`, `inactive`, `cannot_create`).
- A value that parses as neither an email nor a username is rejected `400` (`reason: malformed_identifier`).

## Feature Flag

The endpoints are gated behind the
[`allow-project-data-import` feature flag](../../04-publishing-deployment/configuration.md#allow-project-data-import),
which is disabled by default. When disabled, the endpoints return `404 Not Found`.

## Request Format

The request requires an `onBehalfOfUser` query parameter — the project user (a user **email or username**) every
imported resource and value is attributed to. It must be an active member or admin of the target project and **not**
a system admin (see [Authentication and Authorization](#authentication-and-authorization)).

The request body is the project's data graph as JSON-LD in the knora-api v2 external (complex) schema, with content
type `application/ld+json`. The payload contains resources and their values only — no admin data, ontologies, or
permission data.

Resource and value metadata that is managed by the server is synthesised during the import and need not (and should
not) be supplied in the payload:

- `attachedToProject` is derived from the `{projectIri}` path parameter.
- `attachedToUser` is the `onBehalfOfUser`, not the authenticated (triggering) admin.
- `hasPermissions` is resolved once from the project's default object access permissions (DOAPs) as they apply to
  the `onBehalfOfUser`, and applied uniformly to every resource and value. Precedence follows that user's own group
  memberships — a project member resolves the ProjectMember-group DOAP. Per-resource-class and per-property DOAPs are
  not resolved per entity.
- Creation dates are set to the time of the import.

The `onBehalfOfUser` is resolved once at trigger time. Eligibility and the resolved permissions are a snapshot:
mid-flight changes to that user (deactivation, role change, removal) are not reconsidered. The parameter is
per-request, so a retry after a delete may supply a different user. The import status response reports the
on-behalf-of user (the `onBehalfOf` field) alongside `createdBy` (the triggering admin), so attribution is auditable
without reading a resource.

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
existing task's `id` in the error details. Delete the previous task before triggering a new one.

The upload to Fuseki is **atomic** — if the upload fails, no partial data is written to the triplestore.

## Import Validation

Before anything is written to the triplestore, the transformed data is SHACL-validated against the knora-base data
shapes. The project's ontologies are fetched from the triplestore to support the validation. Validation failure
fails the task with a descriptive error message and leaves the triplestore untouched.

## Typical Workflow

```bash
# Upload and trigger import (onBehalfOfUser is a project user email or username)
curl -s --request POST \
  --url 'https://server/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/data-imports?onBehalfOfUser=project.member' \
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
- **Assets must be pre-ingested**: The import does not ingest binary assets. File values must reference assets already
  present in dsp-ingest. The import fetches their metadata by internal filename and rejects an asset that is not yet
  ingested. 3D file values (`DDDFileValue`) are not yet supported.
- **JSON-LD only**: Other RDF serializations are not accepted.
- **Single instance**: Task state is held in memory per instance.
