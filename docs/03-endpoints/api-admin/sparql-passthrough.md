# SPARQL Passthrough

`POST /admin/sparql/query` forwards a SPARQL 1.1 query to the triplestore untouched and returns the store's response
unchanged. It exists so that trusted staff can inspect data the curated API does not surface, without holding a
database credential.

The endpoint is **not registered unless the deployment enables it**. When it is off, the path returns `404` and does
not appear in the API documentation.

## Contract

Standard SPARQL 1.1 Protocol, so off-the-shelf tooling works against it directly:

- The query is **forwarded verbatim**. It is never parsed, validated, rewritten or ontology-checked.
- The store's HTTP status, `Content-Type` and response body are **relayed verbatim**. A malformed query therefore
  yields the store's own `400` and its own message, not this API's JSON error envelope.
- `Accept` is forwarded unchanged, so the format is negotiated by the store. With no `Accept`, the store's default is
  returned.
- The SPARQL protocol dataset parameters `default-graph-uri` and `named-graph-uri` are relayed unchanged. Any other
  query-string parameter is ignored.

Read-only: there is no update endpoint yet.

## Authentication

A **bearer JWT in the `Authorization` header, and nothing else.** HTTP basic is rejected on this route even though the
rest of the API accepts it, and a session cookie is never accepted. Requests must come from a `SystemAdmin`.

```bash
TOKEN=$(curl -s -X POST https://api.example.org/v2/authentication \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.org","password":"..."}' | jq -r .token)
```

| Situation | Status |
| --- | --- |
| No credentials, or an invalid or expired token | `401` |
| Authenticated, but not a `SystemAdmin` | `403` |
| Route not enabled on this deployment | `404` |

Both checks run **before the request body is read**, so an unauthorized caller cannot make the server buffer a request
at all.

## Request forms

Both forms of the SPARQL 1.1 Protocol POST are accepted. Any other request `Content-Type`, including none at all, is
rejected with `415`.

Direct, as `application/sparql-query`:

```bash
curl -X POST https://api.example.org/admin/sparql/query \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/sparql-query' \
  -H 'Accept: text/csv' \
  --data-binary 'SELECT ?s ?p ?o WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?s ?p ?o } } LIMIT 10'
```

Form-encoded, which `rdf4j`, YASGUI and most other clients default to:

```bash
curl -X POST https://api.example.org/admin/sparql/query \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: application/sparql-results+json' \
  --data-urlencode 'query=SELECT (COUNT(*) AS ?n) WHERE { ?s ?p ?o }'
```

Sending the query as a `?query=` **query-string parameter is rejected with `400`.** This is deliberate rather than an
omission: a query string is written to the edge proxy's access log, so a query sent that way would have leaked its text
before this API ever saw it. The rejection makes the mistake visible; the `GET` form of the protocol is unsupported for
the same reason.

## Guardrails

| Guardrail | Default | What happens when it trips |
| --- | --- | --- |
| Store-side execution timeout | 120s | The store cancels the query at its own engine and its timeout response is relayed |
| Request-body size | 1 MiB | `413`; the framework stops reading at the cap, so an over-cap body is never buffered whole |
| Response size | 64 MiB | `500` with a distinct body, and no partial response -- deliberately not `413`, which describes a request |
| Concurrent calls across the surface | 8 | `503`; the call is rejected, never queued |
| Store unreachable | -- | `503` |
| Store rejects this API's own credentials | -- | `502` with a scrubbed body, rather than relaying the store's `401` |
| Overall deadline exceeded | timeout + 15s | `504` |

Two notes on the response ceiling. It counts the bytes read **from the store**, before any compression this API
applies on the way out, so a compressed response on the wire may be much smaller than the ceiling. And because the
response is buffered to the ceiling before anything is sent, the ceiling multiplied by the concurrency backstop is the
worst-case heap this surface can occupy: raising either raises that bound.

To page through a large result, use `LIMIT` and `OFFSET` rather than raising the ceiling.

## Logging

Every call produces exactly one log entry -- including a rejected one, and including a call abandoned part-way --
recording the operation, the outcome, the user IRI and username where they are known, the duration, the request size in
bytes and, for a completed read, the response size and the store's status. Result payloads are never logged, and
neither are credentials or request headers. The entry carries the trace id when a trace context is present.

The SPARQL text is recorded, because it is the audit trail of what was run, but bounded: it is truncated to 4096
characters (flagged with `sparql_truncated=true`), stripped of control characters so a statement cannot forge a second
entry, and omitted entirely -- leaving only `request_bytes` -- for an outcome decided before anything reached the
store, where the text is unbounded caller input with nothing to attribute.

| `outcome` | When |
| --- | --- |
| `ok` | The store answered with a success status |
| `store-error` | The store answered with an error status, relayed verbatim |
| `unauthenticated` | No or invalid credentials; emitted by the server's security-failure hook |
| `forbidden` | Authenticated but not a `SystemAdmin` |
| `bad-request` | The statement was sent as a query-string parameter |
| `malformed-request` | The framework could not decode the request (e.g. an unsupported `Content-Type`) |
| `request-cap-exceeded` | The request body exceeded the cap |
| `response-cap-exceeded` | The store's response exceeded the ceiling |
| `overloaded` | The concurrency backstop was saturated |
| `store-unavailable` | The store could not be reached |
| `upstream-rejected` | The store refused this API's own credentials |
| `timed-out` | The overall deadline elapsed |
| `interrupted` | The call was abandoned before it finished |
| `defect` | An unexpected failure behind the seam |

## Configuration

Off by default. See [Configuration](../../04-publishing-deployment/configuration.md) for the environment variables:
`KNORA_WEBAPI_ALLOW_SPARQL_PASSTHROUGH` and the four `KNORA_WEBAPI_SPARQL_PASSTHROUGH_*` guardrails.

The flag is read at startup only, so changing it requires an API restart.

## Enabling it on a deployment

Enabling the passthrough on a host has a precondition that the flag itself does not enforce.

**1. Confirm the store refuses federated `SERVICE` calls.** ARQ executes `SERVICE` by default, which means a plain
`SELECT` can make the *store* issue outbound HTTP to a target the query author chooses -- reach the caller does not
otherwise have, and a path that bypasses the response ceiling while logging almost nothing. `modules/fuseki/dsp-repo.ttl`
sets `arq:httpServiceAllowed` to `false`, but the Fuseki entrypoint copies that file only when none exists and
`/fuseki` is a persistent volume, so **an environment created before that change keeps its old configuration across
redeploys.** The container logs a warning when that is the case, but verify per host rather than trusting the image:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$STORE/dsp-repo/query" \
  -u "admin:$DB_PASSWORD" \
  -H 'Content-Type: application/sparql-query' \
  --data-binary 'SELECT * WHERE { SERVICE <http://127.0.0.1:1/sparql> { ?s ?p ?o } }'
```

`422` (body: `SERVICE execution disabled`) means the mitigation is active. `502`, or a connection error to
`127.0.0.1:1`, means it is **not**: the store attempted the outbound call. Fix the on-disk `dsp-repo.ttl` and restart
before enabling the flag.

**2. Consider a server-side query-timeout floor.** dsp-api sends the timeout as Fuseki's per-request `timeout`
parameter, which that parameter's own documentation notes can be capped or ignored
([apache/jena#3044](https://github.com/apache/jena/issues/3044)). A server-side floor makes the cancellation guarantee
robust. Mind the units, which differ between the two mechanisms: the per-request `timeout` parameter is in **seconds**,
while `arq:queryTimeout` in the assembler and `--timeout` on the command line are in **milliseconds** (both verified
against Fuseki 6.1.0). A floor caps *every* query the store serves, not only passthrough ones, so choose a value above
the longest legitimate query -- Gravsearch already runs with a 120s timeout.

**3. Time-box the window on production.** While the flag is on, the route is listed in the public API documentation at
`/api/docs/`, and the `404` becoming a `401` is itself observable. Obscurity is not the control here -- authentication
and `SystemAdmin` authorization are -- but a break-glass window should still be closed deliberately rather than left
open, and an alert on the startup warning (`ALLOW_SPARQL_PASSTHROUGH is turned ON`) is worth having.

## Known limitations

- **Default-graph semantics depend on store configuration.** A query relying on the default graph resolves against
  whatever the store's union-default-graph setting is, so its meaning is a property of the deployment rather than of
  the query. The passthrough does not rewrite queries, so scope patterns to explicit graphs (`GRAPH`, `FROM`) when a
  statement needs to mean the same thing everywhere. See
  [SPARQL queries](../../development/dsp-api-sparql-queries.md).
- **Store errors depend on the store.** Fuseki 6.1.0 does not refuse an `Accept` it cannot satisfy; it falls back to
  its default serialization. The relay carries whatever the store answers, so what a client sees for an odd `Accept` is
  the store's choice, not this API's.
