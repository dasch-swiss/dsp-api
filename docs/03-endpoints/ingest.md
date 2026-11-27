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

## Errors

The ingest routes, except `/ingest/health`, `/ingest/info` and `/ingest/metrics`, answer these failures with a JSON
body. `401`, `403`, `404` and `409` keep their own bodies.

| Status | Meaning                                                                 | Body                                        |
|--------|-------------------------------------------------------------------------|---------------------------------------------|
| `400`  | The request is malformed, for example an empty upload                   | `errors`: a list of `argument` and `reason` |
| `422`  | The request is well-formed, but the file content cannot be ingested     | `correlation_id`, `error_code`, `message`   |
| `500`  | An internal error; the message is fixed text and holds no server detail | `correlation_id`, `error_code`, `message`   |

The `422` and `500` bodies have the same fields:

```json
{
  "correlation_id": "0af7651916cd43dd8448eb211c80319c",
  "error_code": "unsupported_format",
  "message": "File image.gif is not a supported file."
}
```

- `correlation_id` identifies the request in the DSP-API logs and traces. Inside an API request it is the request's
  trace ID. Give it to DaSCH support when you report a failure.
- `error_code` is a stable, machine-readable code. Clients decide on it, not on `message`.
- `message` is for people. It names the uploaded file, never a path on the server.

DSP-API returns these `error_code` values:

| `error_code`         | Status | Meaning                                                                  |
|----------------------|--------|--------------------------------------------------------------------------|
| `internal`           | `500`  | An unexpected failure; the cause is in the logs under the correlation ID |
| `unsupported_format` | `422`  | The file type is not supported                                           |
| `image_too_large`    | `422`  | The image header declares more pixels than the server accepts            |
| `sipi_killed`        | `500`  | The image conversion was stopped, usually because it ran out of memory   |
| `sipi_crashed`       | `500`  | The image conversion crashed                                             |
| `sipi_no_report`     | `500`  | The image conversion failed without a report                             |

When the image conversion reports its own error, its `error_code` is passed through. It answers `422` when the
uploaded file cannot be read (`decode_failed`, `unsupported_format`, `malformed_input`, `metadata_parse_failed`),
and `500` otherwise.

A file that fails in a bulk ingest gets no HTTP answer: it is logged once with its path in the import folder, its own
`correlation_id` and its `error_code`, as a warning when its content is rejected and as an error otherwise.

## Endpoint reference

The ingest endpoints are part of the API's [OpenAPI documentation](openapi.md), under the paths starting with
`/ingest/` (tags `projects`, `assets`, `bulk-ingest`, `import/export`, `maintenance` and `monitoring`).
