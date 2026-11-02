# Ingest Endpoints

Ingest stores the files of assets (the original, its derivatives and the asset metadata) and runs the bulk ingest,
asset export and import, and the asset maintenance actions. It is part of DSP-API and is served by the API under the
`/ingest/` path prefix, on the same host and port as every other DSP-API route.

| Routes                                              | Purpose                                               |
|-----------------------------------------------------|-------------------------------------------------------|
| `/ingest/projects/{shortcode}/assets/...`           | upload an asset, read its metadata, download original |
| `/ingest/projects/{shortcode}/bulk-ingest`          | bulk ingest from the ingest temp directory            |
| `/ingest/projects/{shortcode}/export`, `/import`    | asset export and import of a project                  |
| `/ingest/maintenance/...`, `/ingest/report/...`     | maintenance actions and reports (system admin)        |
| `/ingest/health`, `/ingest/info`, `/ingest/metrics` | health, version and metrics (public)                  |

For example, an upload of `image.jpg` into project `0001` on a local stack is
`POST http://localhost:3333/ingest/projects/0001/assets/ingest/image.jpg`.

## Compatibility with `ingest.<domain>`

Ingest used to run as a separate service at `https://ingest.<domain>`, without the `/ingest/` prefix. On the DaSCH
servers that address forwards `https://ingest.<domain>/<path>` to `/ingest/<path>` of the API, so existing clients
keep working. New clients should call the API host with the prefix. The separate Swagger UI at
`https://ingest.<domain>/docs` is gone; see [Endpoint reference](#endpoint-reference).

## Authentication

Secured ingest routes take a `Bearer` token in the `Authorization` header, the token DSP-API issues at login. Cookies
and HTTP basic authentication are not accepted. A request without a valid token, or whose token lacks the scope the
route requires, answers `401`. The original download (`GET .../assets/{assetId}/original`) also accepts anonymous
requests and answers according to the asset's permissions. `/ingest/health`, `/ingest/info` and `/ingest/metrics`
are public; `/ingest/metrics` returns the metrics of the whole API process.

## Uploads larger than 1 MiB

An upload body larger than 1 MiB must declare its size in a `Content-Length` header; the API then streams it to
storage. A larger body sent with chunked transfer encoding (no `Content-Length`) is rejected with `413`.

## Endpoint reference

The ingest endpoints are part of the API's [OpenAPI documentation](openapi.md), under the paths starting with
`/ingest/` (tags `projects`, `assets`, `bulk-ingest`, `import/export`, `maintenance` and `monitoring`).
