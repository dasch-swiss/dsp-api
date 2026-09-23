# DSP-API dashboards

Git-synced Grafana dashboards for DSP-API (folder UID `dsp-api-dashboards`, see `_folder.json`).
All use the **v2 schema** (`dashboard.grafana.app/v2`): panels under `spec.elements`, positions under
`spec.layout`, variables under `spec.variables`. `metadata.name` is the dashboard UID and must stay
stable. Edit the JSON here and push to `main`; the dashboards are read-only in the Grafana UI (see the
parent [`../README.md`](../README.md) for Git Sync mechanics).

| File | Dashboard | What it answers |
| --- | --- | --- |
| `dsp-api-response-duration.json` | DSP-API — Response Duration | Request latency, throughput and server time, global, per route group and per route. Detailed below. |
| `dsp-backend.json` | DSP-API — Backend | JVM / runtime / triplestore-facing backend health. |
| `dsp-gravsearch.json` | DSP-API — Gravsearch | Gravsearch volume, outcome and duration percentiles by query shape (metrics). |
| `dsp-route-usage.json` | DSP-API — Route Usage | Which API and dsp-app routes are called, and how often. |

For **how to read a slow Gravsearch trace**, see [`docs/observability/gravsearch-trace-runbook.md`](../../docs/observability/gravsearch-trace-runbook.md)
and [`traceql-recipes.md`](../../docs/observability/traceql-recipes.md).

## DSP-API — Response Duration

**Purpose:** spot latency regressions and slow or expensive endpoints for DSP-API, from the tapir
request metrics (`tapir_request_*`, `service_name="DSP_svc_api"`) and the API-side Fuseki round-trip
histogram (`fuseki_request_duration_*`). **Audience:** whoever is watching a deploy or chasing a slow
route.

### Metrics and their limits

- **Request durations: averages and percentiles**, both on `phase="body"` (full request). Averages are
  `rate(sum)/rate(count)` and have full history. Percentiles are `histogram_quantile()` over
  `tapir_request_duration_seconds_bucket`, which the ops-deploy scrape filter dropped until
  [ops-deploy#1434](https://github.com/dasch-swiss/ops-deploy/pull/1434): scraped on stage from
  2026-09-21 and on prod from 2026-09-23 ~06:00 UTC, so **percentile panels are empty before that**.
  Buckets: 5, 10, 25, 50, 75, 100, 250, 500, 750 ms, 1, 2.5, 5, 7.5, 10, 15, 30, 45, 60 s; quantiles
  interpolate linearly inside a bucket and cap at 60 s. The `phase="headers"` series carry no
  information (`headers`→`body` differs by < 0.1 ms per route).
- **Latency includes 4xx.** Duration series carry a `status` class label (`2xx`/`4xx`/`5xx`) and no panel
  filters on it. A burst of fast rejections (seen on prod 2026-09-23: ~1,170 `4xx` on
  `/v2/searchextended*` in 5 minutes) pulls avg and p50 down without anything getting faster; when
  comparing before/after a change, add `status="2xx"` in Explore.
- **`fuseki_request_duration_bucket`** is dsp-api's own timing of SPARQL round trips, labels
  `isGravsearch` / `isSearch` / `isMaintenance` / `type`, unit `millis`, **decade buckets** (10 ms,
  100 ms, 1 s, 10 s …). Quantiles from it are coarse within-decade interpolations; the threshold shares
  (panel 16) are exact. It has full history, so it is the percentile baseline for anything before the
  tapir buckets existed.
- **`path=""` is unmatched traffic**: CORS `OPTIONS` preflights and `HEAD` probes that hit no endpoint,
  ~11k/day on prod. Every panel excludes it with `path!=""`. These requests also leak
  `tapir_request_active` (incremented, never decremented) — do not build an in-flight panel on that
  gauge until dsp-api fixes it.
- **No version metric.** `target_info` for this service has no `service_version` and there is no
  build-info gauge, so deploys can only be inferred from restarts (see the marker below). A
  `dsp_api_build_info{version=…}` gauge in dsp-api (plus the scrape whitelist) would give real deploy
  markers.

### Filters

- `environment`, `stack` — from `label_values` on the metric. With `Stack = All` on prod the ~30 RDU
  boxes are included; select a stack for one deployment.
- `Route group` — single-select; the value is a path regex applied as `path=~"${routegroup}"`.
- `Route (path)`, `Method` — multi-select; per-route panels (5, 12, 13, 14, 18) layer these on top of
  the route group. **`Route (path)` must be interpolated as `` path=~`${path:regex}` ``** (the `:regex`
  format inside a PromQL *backtick* string): route templates contain regex metacharacters
  (`/v2/resources/*`, `/v2/ontologies/metadata/*`, `{listIri}`). With `${path:pipe}` the `*` becomes a
  quantifier and `/v2/resources/*` — the busiest prod route — silently drops out of every per-route
  panel while `/v2/resources` matches instead. The backticks are required because Grafana's escaping
  emits `\/` and `\{`, which a double-quoted PromQL string rejects as unknown escape sequences.
- `Exclude routes` — multi-select of path regexes applied as `path!~"${exclude:pipe}"`. Default excludes
  `/health` + `/version`. Safe when empty: PromQL fully anchors regex matchers.
- `Smoothing window` — the `rate()` window (`[$smoothing]`) on **every** time-series panel. A larger
  window averages peaks down (legend **Max** drops, **Mean** stays). Stat tiles and tables use
  `$__range` instead. Keep it ≤ the dashboard time range.
- `Percentile` — `p50` / `p90` / `p95` / `p99` (default `p95`), the value is the quantile applied as
  `histogram_quantile($quantile, …)` in panels 21 and 22; titles show it via `${quantile:text}`.
  Panels 1, 4 and 12 show fixed percentiles and ignore it.

### Panels

| Panel | Question | Source |
| --- | --- | --- |
| 1 Response time | avg, p50, p95, p99 of full-request duration over the range | `$__range` |
| 2 Request rate | Requests/s | `$__rate_interval` |
| 3 5xx error rate | Share of 5xx over the range | `$__range` |
| 4 Response duration — global | avg, p50, p95, p99 over time: does the tail move with the mean? | `[$smoothing]` |
| 11 Avg duration by route group | One line per route group; "reads slower" vs "searches slower" | `[$smoothing]` |
| 21 Percentile by route group | Same groups at the selected percentile: all of it slower, or only its tail? | `[$smoothing]` |
| 5 Avg duration by route | Mean duration per route over time (log2 axis) | `[$smoothing]` |
| 22 Percentile by route | Per route at the selected percentile (log2 axis) | `[$smoothing]` |
| 13 Server time per route | Stacked `rate(duration_sum)` per route — where the time goes | `[$smoothing]` |
| 18 Requests per route | Stacked req/s per route — the denominator for 13 | `[$smoothing]` |
| 19 Server time per route group | Stacked `rate(duration_sum)` per group | `[$smoothing]` |
| 20 Requests per route group | Stacked req/s per group — the denominator for 19 | `[$smoothing]` |
| 12 Routes ranked by total server time | Total time, avg, p50, p95, requests, 4xx, 5xx per route; path links to Tempo | `$__range` |
| 14 5xx responses per route | When errors happened (approximate count) | `[$__interval]` |
| 15 Triplestore p50/p95/p99 by kind | Fuseki round-trip latency, Gravsearch vs other | `[$smoothing]` |
| 16 Share of triplestore round trips > 100 ms / > 1 s | Exact threshold share | `[$smoothing]` |
| 17 Triplestore round trips/s by query type | SPARQL throughput by form and flags | `[$smoothing]` |
| 7 Slowest Gravsearch queries (traces) | Individual slow gravsearch executions, their project, the query | Tempo |

The table lists panels in layout order (one flat grid, no rows). Server time is requests × duration:
each server-time panel is followed by its requests panel so that load (server time rising with
requests) can be told from slowdown (server time rising without them).

### Layout rules (keep these identical across panels 4, 11, 21, 5, 22, 13, 18, 19, 20)

The nine stacked time-series panels are pixel-aligned so one x position is the same moment in all of
them: same height (9), right-hand table legend with fixed `width: 415`, y-axis `axisWidth: 90`, and
**no axis label** (a rotated label is drawn outside `axisWidth` and shifts the plot; put the unit in
the title). Panels keyed by route, route group or query type (5, 11, 13, 14, 17, 18, 19, 20, 21, 22) use
`color.mode: palette-classic-by-name`, so the same name has the same colour in every panel (with the
default palette colours reshuffle per panel).

### Restart marker

The "dsp-api restart" annotation (purple) fires when the API container of a selected stack
(re)started. Deploys do this, but so do crashes, host reboots and manual restarts; on prod about 5 of
8 markers per month coincide with a deploy. Query:
`count(min by (stack) (process_cpu_seconds_total{…}) < min by (stack) (… offset $__interval))`, min
step `2m`, shown on panels 4, 5, 11, 13–22. Three load-bearing choices:

- **Aggregate by `stack` first.** Every restart gives the container a new `container_id`, i.e. a new
  series, so a plain `resets()` never fires.
- **`min`, not `sum`.** A relabel that adds a label to a running container's series (seen on prod:
  `asserts_env`) makes old and new series overlap for the 5-minute staleness window; a `sum` doubles and
  halves and reads as a restart, the `min` does not move. A real restart drops the `min` to the fresh
  container's near-zero counter.
- **Compare with one step earlier, not `resets()` over a subquery window.** Adjacent subquery windows
  never share a sample pair, so a drop that straddles two windows is invisible. `X < X offset
  $__interval` is true for exactly one evaluation point per restart on any range.

### Query-shape rationale (don't "simplify" these away)

- **Panel 12 is a Tempo-style multi-query join.** Query A is avg duration, B `increase()` request
  count, C total time, D 4xx count, E 5xx count, F/G p50/p95 (`histogram_quantile` over
  `increase(_bucket[$__range])` by `le, path, method`); all carry `format: "table"` **and** query
  `version: "v0"`, and are merged on `path, method`. Without `format: table` the Prometheus results
  come back as time-series-wide frames and the `merge` transform produces one column per series (raw
  label header + NaN rows) instead of `method | path | Total | Avg | p50 | p95 | Requests | 4xx | 5xx`. And the
  server **silently strips `format` when `version` is empty** — so both are load-bearing. Every query is
  guarded with `and (<duration count> > 0)` so only routes that reported a duration in the range appear
  (avoids `0/0 = NaN` rows and rows with an empty time); D/E are filled with `or (<B> * 0)` so routes
  without errors show `0` rather than an empty cell.
- **Requests is `increase()` (a count), not `rate()`.** The slow routes here are rare (export/candelete
  run a handful of times an hour); a per-second rate rounds to `0.00` and reads as broken.
- **Panel 14 is approximate by construction — read it for *when*, trust the table for *how many*.**
  Its bars are `increase(...[$__interval])`, so consecutive bars tile the range without overlap, but
  `increase()` extrapolates sparse counts and the legend **Total** can exceed the table's 5xx column by
  about one per burst. An exact count is not achievable from this counter: a tapir 5xx series is **born
  at the first error** for a route (and reborn with a new label set on a relabel), so a per-step
  difference `X - X offset $__interval` misses every birth, and a birth fallback
  `X - (X offset … or X * 0)` re-counts the whole counter on each relabel. Do not use `[$smoothing]`
  here: overlapping windows count the same error several times. The `and on (path) (… [$__range] > 0)`
  clause keeps only routes that had a 5xx somewhere in the range.
- **Panels 5, 11, 21 and 22 use a log2 y-axis** (`scaleDistribution: log`). A single slow-but-rare route or
  group (`/v3/export/resources`, multiple seconds) otherwise compresses every other line into the
  baseline. `topk()` is not a substitute — in a range graph it re-picks members every step and flickers.
- **Percentiles sit next to the averages, they do not replace them** (panels 1, 4, 12, and 21/22 beside
  11/5). The average has full history and is what server time (13, 19) is built from; the percentiles
  exist only since the buckets were scraped (see *Metrics and their limits*). Aggregate buckets with
  `sum by (le, …)` *before* `histogram_quantile()` — a quantile of per-series quantiles is meaningless, and
  every restart mints new series (new `container_id`), so the `sum` is also what stitches them together.
  Percentiles per route (22, table p95) are noisy for rare routes: with a handful of requests per window
  p99 is simply the slowest request.
- **Panel 7 (Gravsearch traces)** queries Tempo (`grafanacloud-dasch-traces`) for `gravsearch` spans
  over the `Gravsearch duration ≥` threshold, `tableType: "spans"`, scoped by `span.environment` /
  `span.stack` (the span carries both). The verbatim query is **not** a column — it lives on the span's
  `gravsearch.query` **event** (`db.query.text`), deliberately kept off span attributes for cardinality
  (see the runbook). The **Trace ID** column carries an explicit internal Tempo link
  (`query = ${__value.raw}`) so it opens the trace by ID; from there: `gravsearch` span → Events →
  `gravsearch.query`. The trace-id field must **not** be excluded by the organize transform, or the link
  loses the ID and points nowhere.
- **Panel 7 project filter.** A **Project shortcode (Gravsearch)** variable is interpolated into the
  TraceQL as `event.db.query.text =~ ".*ontology/${project}.*"` (host-agnostic; empty matches all).
  Filtering on that event attribute also surfaces a truncated **Query (preview)** column; the Trace ID
  link remains the way to the full, untruncated query. The `span.environment` predicate must stay in,
  or an `event.`-scoped search crosses environments. The threshold default is `2s` — the p95 of the
  `gravsearch` span baseline (PRD REQ-3.1: baseline-anchored, not an arbitrary constant).
- **Panel 7's two project columns are not redundant** (DEV-7031). **Project**
  (`gravsearch.project_shortcodes`) is *inferred* from the query's IRIs — which projects it touches.
  **Scoped to** (`gravsearch.project_restriction`) is *stated* by the caller — the project the request
  limited the search to. Both are worth showing: dsp-app's Advanced Search always sends
  `limitToProject` (`advanced-search-results.component.ts`), and it is the most Gravsearch-heavy flow
  there is, so on a slow trace the pair tells you whether a researcher ran a project-scoped search or
  a query that merely references the project. Empty **Scoped to** means unrestricted, not unknown; it
  holds an IRI rather than a shortcode, so for newer projects it will not visually match **Project**.
- **Panel 7 keeps the text filter even though a project attribute now exists** (DEV-7031). The
  **Project** column selects `span.gravsearch.project_shortcodes`, but the *filter* deliberately still
  matches the query text: a TraceQL attribute predicate does not match a span that lacks the attribute,
  so filtering on it would return nothing until the release carrying the instrumentation is deployed,
  and would keep hiding every older trace still in retention. The column has no such window — it simply
  fills in as new traces arrive. Two consequences to know: the filter matches **ontology IRIs only**, so
  it misses dsp-api's internal searches (incoming links, still-image representations, incoming regions)
  whose queries name only built-in ontologies, while the column covers them because the attribute is
  derived from resource IRIs too. Read the column rather than trusting the filter for those.

### Notes

- The day/night swing in average duration is a **traffic-mix** effect, not health-check noise: at night
  ~82% of traffic is fast automated `/v2/resources/*` (~21 ms) and `/v2/node/{listIri}` (~6 ms) reads at
  steady throughput; daytime layers heavier human-driven search/admin/ontology calls on top. Excluding
  `/health`+`/version` barely moves it. The per-route-group panel (11) separates "are reads fast" from
  "are searches fast".
- On prod the routes that consume the most server time are extended search (+ its count), resources
  reads and ontology allentities; the slowest-average routes are rare exports. Optimisation pays off
  on the former list.
- Panel 7's **Project** and **Scoped to** columns are live (DEV-7031): `gravsearch.project_shortcodes` is a
  bounded, sorted, comma-separated set and `gravsearch.project_restriction` a single project IRI, both on the
  `gravsearch` root span, and both stay off the Alloy spanmetrics dimension list (which selects only
  `gravsearch.query.shape`, `stack`, `domain`, `environment`), so neither adds a Prometheus label. Remaining follow-up: once traces from before that release have aged out of retention, switch the
  project filter from the query text to the attribute — see the panel-7 bullet above for why not yet.
