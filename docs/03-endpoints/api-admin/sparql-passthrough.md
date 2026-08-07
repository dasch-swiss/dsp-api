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
  returned. A preference list split across several `Accept` header lines is recombined into the one comma-separated
  value RFC 9110 makes it equivalent to; nothing is parsed, reordered or dropped.
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

Both checks run **before the request body is decoded**, so an unauthorized caller cannot drive body decoding, the
request-body cap, or any of the server logic behind them.

They do not stop the server from *buffering*. The HTTP server aggregates request bodies up to 1 MiB before any
per-endpoint logic runs at all, for every caller including an unauthenticated one. That is a server-wide property of
this API, not of this route, and it is what the sizing note under [Guardrails](#guardrails) is about.

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
| Request-body size | 1 MiB | `413`; the framework stops reading at the cap, so an over-cap body is never decoded whole |
| Response size | 64 MiB | `500` with a distinct body, and no partial response -- deliberately not `413`, which describes a request |
| Concurrent calls across the surface | 8 | `503`; the call is rejected, never queued |
| Store unreachable | -- | `503` |
| Store rejects this API's own credentials | -- | `502` with a scrubbed body, rather than relaying the store's `401` |
| Overall deadline exceeded | timeout + 15s | `504` |

Two notes on the response ceiling. It counts the bytes read **from the store**, before any compression this API
applies on the way out, so a compressed response on the wire may be much smaller than the ceiling. And because the
response is buffered to the ceiling before anything is sent, `max-response-bytes` sizes the heap a single call costs.

What it does **not** do is combine with the backstop into a total, and the reason is the mirror image of the
request-side case below. The backstop bounds concurrent calls *to the store*: its slot is released when the store's
bytes have been collected, which is before the HTTP layer has written them to the client. A response array therefore
stays live after its slot is gone, for as long as the client takes to read it — so a slow reader holds
`max-response-bytes` while a fresh call takes the slot it vacated. Worst-case response-side heap is
`max-response-bytes` times the number of simultaneous callers the deployment admits, not times
`max-concurrent-calls`; the backstop keeps a runaway script from opening store connections without limit, and does not
size the JVM.

To page through a large result, use `LIMIT` and `OFFSET` rather than raising the ceiling.

### Sizing the request-body cap

`max-request-body-bytes` interacts with the HTTP server's own 1 MiB aggregation threshold
(`maxAggregatedRequestBodySize`, which the server applies to every route and which is not configurable per
deployment). The interaction is not what the numbers suggest, and it matters in both directions:

- **Below 1 MiB it does not reduce peak buffering.** The server has already read and buffered up to 1 MiB before this
  cap is consulted at all, so lowering it changes which status a large request gets, not how much memory it costs.
- **Above 1 MiB it raises peak buffering, and nothing bounds the concurrency of that.** The concurrency backstop in
  the table above is applied in the server logic, which runs *after* body decoding, so N callers can each be holding a
  body of this size before any of them is counted against it. Worst-case request-side heap is therefore
  `max-request-body-bytes` times the number of simultaneous callers the deployment admits, not times the backstop.
- **Above 1 MiB it is unreachable for a chunked caller.** The server switches to streaming on the *declared*
  `Content-Length` alone, so a request with `Transfer-Encoding: chunked` keeps the aggregator — whose own limit is
  that same 1 MiB. A chunked body that outgrows it is answered by the HTTP layer with a bare `413`, below this API:
  no audit entry, no error envelope, and this cap never consulted. Clients that must send more than 1 MiB have to
  declare a `Content-Length`.

Raising it above 1 MiB is a decision about the whole process's memory, not about this route.

## Logging

Every call produces exactly one log entry -- including a rejected one, and including a call abandoned part-way --
recording the operation, the outcome, the user IRI and username where they are known, the duration, the request size in
bytes and, for a completed read, the response size and the store's status. Result payloads are never logged, and
neither are credentials or request headers. The entry carries the trace id when a trace context is present.

"Call" means a request this route actually claimed. A request to `/admin/sparql/query` with a method the route does not
serve -- `GET`, say -- is not routed here at all and produces no entry; it is a `404`, the same as any unrouted path.
Two further caveats to "exactly one" are listed under [Known limitations](#known-limitations).

The SPARQL text is recorded, because it is the audit trail of what was run, but bounded and contained:

- Truncated to 4096 characters, flagged with `sparql_truncated=true`.
- Stripped of control characters, `U+2028`/`U+2029` and Unicode format characters, so a statement cannot forge a
  second entry or disguise how the entry renders.
- Written as the last field and **quoted** (`sparql="..."`, with `"` and `\` escaped), so a statement cannot forge a
  *field* either. Every other field is machine-generated or a validated value type, so this is the only value in the
  entry that can contain text a caller chose — including the entry's own prefix, which is not stripped and so is not
  a safe anchor. What keeps the real fields readable is position plus parsing: `sparql` is last, so every other field
  precedes it. **Use a logfmt-aware parse that honours the quoting, or take the first match for a field** — never any
  match anywhere in the line. A literal line filter (`|= "... outcome=forbidden"` in Loki) can still match a statement
  a caller planted; that is a false-positive alert, not a misattribution, since parsed properly the outcome and the
  identity are still the real ones.
- Omitted entirely -- leaving only `request_bytes` -- for `forbidden`, `bad-request`, `request-cap-exceeded` and
  `overloaded`. Those are calls the surface refused outright, where the text is caller input with nothing to
  attribute. A call that was admitted and then abandoned or crashed (`interrupted`, `defect`) **does** carry its
  statement: what was running when it went wrong is exactly what the entry is for. The exception is a `defect` raised
  in the *security logic*, which happens before the body is decoded — that entry has neither a statement nor a
  `request_bytes`.

| `outcome` | When |
| --- | --- |
| `ok` | The store answered with a success status |
| `store-error` | The store answered with an error status, relayed verbatim |
| `unauthenticated` | No or invalid credentials; emitted by the endpoint's security logic, which is the last place the distinction is in scope |
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
| `defect` | A bug in this API: either in the security logic, or behind the store seam. Always a `500` |

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
- **A rejection can close the connection, so pool churn on this route is expected.** When a request is refused before
  its body was necessarily read -- a `401`, a `403`, or a decode failure on a request declaring a `Content-Length`
  above the server's 1 MiB aggregation threshold -- the response carries `Connection: close`. Without it, zio-http
  leaves such a connection unreadable and the client's *next* request on it hangs until its own timeout. Connections
  dropped after a rejected passthrough request are therefore deliberate, not a fault. Every other rejection leaves the
  connection alone, including one on a chunked body: the server aggregates anything it has not been *told* is large,
  so that body was already fully read.
- **That same rejection can reach the caller as a reset rather than as a status.** The statuses on this page are what
  the server *sends*; on a request closed as described above, they are not necessarily what the client *reads*. The
  response is written while the caller is still uploading and the connection is then closed with bytes still in
  flight, so depending on the client and on timing the caller may observe a broken pipe or `Connection reset by peer`
  instead of the `401`, `403` or `413`. This is inherent to refusing an upload before reading it — the alternative is
  reading a body that has already been refused, which is the cost the early rejection exists to avoid. The audit
  entry and the status are unaffected: this is about what survives the trip back, not about what the surface decided.

    `Expect: 100-continue` is not a way around it. This API's HTTP server leaves zio-http's `acceptContinue` at its
    default of `false`, so Netty's expect-continue handler is never installed and no `100 Continue` is ever sent; a
    client that waits for one waits out its own timeout and then uploads anyway. Enabling it would not help either:
    that handler answers `100 Continue` unconditionally, before any of this API's logic runs, so it would wave
    through precisely the request that is about to be refused. Verified against zio-http 3.11.3.

    A caller that needs the status reliably should keep the request body **under the 1 MiB aggregation threshold and
    declare a `Content-Length`**. Below the threshold the server reads the whole body before any per-endpoint logic
    runs, so the rejection is written to a connection with nothing left in flight, carries no `Connection: close`,
    and the status arrives intact.
- **A crash also produces a second, unprotected log line.** Both stages that can raise a defect emit one, from
  different places. A defect reaching the **server logic** is logged by tapir's default server log, which is left in
  place, carrying the exception's raw message and stacktrace and the request line. A defect in the **security logic**
  is logged by this API itself — `ZIO.logErrorCause("Defect in the security logic", cause)` in `BaseEndpoints` —
  carrying the raw message and stacktrace; tapir's own server log records a security failure at DEBUG, so the ERROR
  line there is ours, not the framework's. The realistic trigger for the second on this route is a store outage
  during the token's user lookup (`findUserByIri(...).orDie` in `Authenticator`), whose message can embed the lookup
  query dsp-api sent — dsp-api's own text, not the caller's. Either way the `defect` audit entry has a companion
  ERROR line with none of the containment this surface applies to its own entry — unbounded, not stripped of
  line-breaking characters, and not quoted. It is API-wide and pre-existing rather than a property of this route, but
  it is the one path that bypasses the entry hardening, so a deployment treating the passthrough's log as sensitive
  should know the `defect` outcome has a companion line in the same stream.
- **Two honest caveats to "exactly one entry per call".** An interruption *inside the security logic* -- a client
  disconnecting during authentication -- produces no entry, because the audit emitter for a rejection is on the
  rejection path and an interrupt takes neither branch. And the `ok` entry is written when the store's response has
  been received, before it is serialized back to the client, so a call recorded as `ok` may still have failed to reach
  the caller. The entry attributes what was *run*, which is what an audit trail is for; it is not a delivery receipt.
