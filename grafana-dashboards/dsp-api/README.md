# DSP-API dashboards

Git-synced Grafana dashboards for DSP-API (folder UID `dsp-api-dashboards`, see `_folder.json`).
All use the **v2 schema** (`dashboard.grafana.app/v2`): panels under `spec.elements`, positions under
`spec.layout`, variables under `spec.variables`. `metadata.name` is the dashboard UID and must stay
stable. Edit the JSON here and push to `main`; the dashboards are read-only in the Grafana UI (see the
parent [`../README.md`](../README.md) for Git Sync mechanics).

| File | Dashboard | What it answers |
| --- | --- | --- |
| `dsp-api-response-duration.json` | DSP-API — Response Duration | Request latency & throughput, global and per route. Detailed below. |
| `dsp-backend.json` | DSP-API — Backend | JVM / runtime / triplestore-facing backend health. |
| `dsp-gravsearch.json` | DSP-API — Gravsearch | Gravsearch volume, outcome and duration percentiles by query shape (metrics). |
| `dsp-route-usage.json` | DSP-API — Route Usage | Which API and dsp-app routes are called, and how often. |

For **how to read a slow Gravsearch trace**, see [`docs/observability/gravsearch-trace-runbook.md`](../../docs/observability/gravsearch-trace-runbook.md)
and [`traceql-recipes.md`](../../docs/observability/traceql-recipes.md).

## DSP-API — Response Duration

**Purpose:** spot latency regressions and slow endpoints for DSP-API, from the tapir request metrics
(`tapir_request_*`, `service_name="DSP_svc_api"`). **Audience:** whoever is watching a deploy or
chasing a slow route.

**Averages, not percentiles.** Every request-duration figure is `rate(sum)/rate(count)` on
`phase="body"` (full-request duration). The metric arrives with **no histogram buckets**, so
percentiles are not available here — use the Gravsearch dashboard / Tempo for tail latency. The
buckets are not missing at the source: dsp-api's tapir interceptor emits them, but the ops-deploy
scrape filter (`grafana.metrics.filter` in `roles/dsp-deploy/templates/docker-compose-svc.yml.j2`)
whitelists only `_sum`/`_count`. [ops-deploy#1434](https://github.com/dasch-swiss/ops-deploy/pull/1434)
enables `_bucket` (~5,600 extra series); once deployed, the proposed panels below can move to
`histogram_quantile()`. The only true percentile on this dashboard today is the API-side **triplestore**
round-trip histogram (`fuseki_request_duration_bucket`, panels 15–16).

**Filters (scope every panel unless noted):**

- `environment`, `stack` — from `label_values` on the metric.
- `Route group` — a coarse, single-select bucket (`Search`, `Resources`, `Admin`, `Export`, …); the
  value is a path regex applied as `path=~"${routegroup}"`.
- `Route (path)`, `Method` — fine multi-select; the per-route panels (5, 6) layer these on top of the
  route group.
- `Exclude routes` — multi-select of path regexes applied as `path!~"${exclude:pipe}"`. Default
  excludes `/health` + `/version`. Safe when empty: PromQL fully anchors regex matchers, so an empty
  exclude drops only empty-path series, not everything.
- `Smoothing window` — the `rate()` window (`[$smoothing]`) on **every** time-series panel (4, 5, 11,
  13–17). It changes how smooth those lines are (larger window → peaks averaged down, so the legend
  **Max** drops while the **Mean** stays put); it does **not** touch the stat tiles or tables, which
  use `$__range`. Keep it ≤ the dashboard time range or `rate()` runs short of data.

**Panels:**

| Panel | Question | Source |
| --- | --- | --- |
| 1 Avg response time | Mean full-request duration over the range; unmatched requests (`path=""`) excluded | `$__range` |
| 2 Request rate | Requests/s (excludes monitoring when `/health`,`/version` excluded, and unmatched requests) | `$__rate_interval` |
| 3 5xx error rate | Share of 5xx over the range; unmatched requests excluded | `$__range` |
| 4 Avg duration — global | Mean duration over time (deploy-regression line) | `[$smoothing]` |
| 5 Avg duration by route | Mean duration per route over time | `[$smoothing]` |
| 6 Routes ranked by avg duration | Slowest routes now + how often they run | `$__range` |
| 7 Slowest Gravsearch queries (traces) | Individual slow gravsearch executions, their target + scoped project, + the query | Tempo |
| 11 Avg duration by route group | One line per route group instead of one global average | `[$smoothing]` |
| 12 Routes ranked by total server time | Where the API spends its time; avg, requests, 4xx, 5xx; path links to Tempo | `$__range` |
| 13 Server time per route | Stacked `rate(duration_sum)` per route over time | `[$smoothing]` |
| 14 5xx responses per route | Which endpoint failed, when | `[$smoothing]` |
| 15 Triplestore p50/p95/p99 by kind | Fuseki round-trip latency as seen from dsp-api, Gravsearch vs other | `[$smoothing]` |
| 16 Share of triplestore round trips > 100 ms / > 1 s | Exact threshold share, traffic-mix independent | `[$smoothing]` |
| 17 Triplestore round trips/s by query type | SPARQL throughput by form and flags | `[$smoothing]` |

### Proposed comparison panels

Panels 11–17 were added **directly below** the panels they are meant to replace, so the two can be
compared on real data before anything is removed: 11 under 4, 12–14 after 5–6, then the triplestore
panels 15–17, then 7. The layout is one flat grid — the former row headers ("Global", "Per route", …)
were dropped because they got in the way of that comparison. Panels 6–7 are unchanged; the stat tiles
1–3 took the `path!=""` proposal directly (accepted, no side-by-side copies); panels 4 and 5 stay,
gained the deploy marker and were aligned with 11 (see below). What each proposal fixes:

- **`path!=""` everywhere (1–3, 11–14).** ~11k requests/day on prod carry an empty `path`: CORS
  `OPTIONS` preflights and `HEAD` probes that never matched an endpoint. They inflate the request-rate
  tile and appear as a blank row in the routes table. (They also leak `tapir_request_active`, which
  climbs monotonically until a restart — do not build an in-flight panel on that gauge.)
- **Route groups next to the global line (4, 11, 5 — all three kept).** The global average mostly
  tracks traffic mix (see Notes); per-group lines separate "reads got slower" from "searches got
  slower", and per-route lines name the culprit. Panel 11 reuses the Route group variable's regexes as
  fixed queries and deliberately ignores the Route group filter. The three are stacked and **their
  plot areas are pixel-aligned** so a feature at one x position is the same moment in all three: same
  height, a right-hand table legend with a fixed `width: 460` on each (panel 4 had a bottom legend and
  the other two right legends sized by the longest series name, which shifted the plots), and a fixed
  y-axis `axisWidth: 90` (log and linear axes otherwise produce different tick-label widths). Keep
  those three values identical when editing any of the three panels.
- **Total time, not just average (12, 13).** Slowest-average routes are rare exports; the routes that
  consume server time on prod are extended search (+ count), resources reads and ontology
  allentities. Panel 12 adds `Total time` (= `increase(duration_sum)`), `4xx`, `5xx`, sorts by total,
  and gives `path` an internal Tempo link (`span.http.route = ${__data.fields.path}`, server spans
  `> 1s`, scoped by `span.environment`/`span.stack`).
- **Errors per route (12, 14).** Status is a label we already have; only the global tile used it.
- **Same `[$smoothing]` window as panels 4/5, `phase="body"` only.** Every time series on the
  dashboard must follow the one Smoothing window knob, so the original and proposed panels smooth
  identically and stay comparable. (A fixed `15m` window and a range-adaptive `[$__interval]` window
  were both tried and rejected: the first is unreadable at 7 days, the second takes the choice away
  from the reader.) Per route the `headers`→`body` difference is < 0.1 ms everywhere, so the phase
  split carries no information.
- **Triplestore latency (15–17).** `fuseki_request_duration_bucket` is dsp-api's own histogram of
  SPARQL round trips, with `isGravsearch`/`isSearch`/`isMaintenance`/`type` labels and **decade buckets**
  (10 ms, 100 ms, 1 s, 10 s … in `millis`). Quantiles are therefore coarse within-decade
  interpolations — read them as "is it Fuseki or the API"; the threshold shares in 16 are exact.
- **Deploy markers.** A dashboard annotation ("dsp-api restart / deploy", purple) fires on a drop of
  the process CPU counter. No version/build-info metric is exported (`target_info` for this service has
  no `service_version`), and every restart gives the container a new `container_id`, so a plain
  `resets()` never fires — the query aggregates by `stack` first and compares with one step earlier:
  `count(min by (stack) (process_cpu_seconds_total{…}) < min by (stack) (… offset $__interval))`, min
  step `2m`. That is true for exactly one evaluation point per restart on any dashboard range. Two
  traps, both hit while building it: (1) `min`, not `sum` — a relabel that adds a label to a running
  container's series (seen on prod: `asserts_env` appeared) makes old and new series overlap for the
  5-minute staleness window, so a `sum` doubles and then halves and reads as a restart, whereas the
  `min` is unchanged; (2) not `resets()` over a subquery window — adjacent windows never share a
  sample pair, so a drop straddling two windows is invisible. It is filtered to the duration/latency
  time-series panels (4, 5, 11, 13–17); the stat tiles and tables carry no markers.

### Query-shape rationale (don't "simplify" these away)

- **Panel 6 is a Tempo-style two-query join.** Query A is avg duration, query B is `increase()` request
  count; both carry `format: "table"` **and** query `version: "v0"`. Without `format: table` the
  Prometheus results come back as time-series-wide frames and the `merge` transform produces one column
  per series (raw label header + NaN rows) instead of `method | path | Avg | Requests`. And the server
  **silently strips `format` when `version` is empty** — so both are load-bearing. Query A is also
  guarded with `and (<countB> > 0)` to drop routes with no traffic in the range (`0/0 = NaN`, which
  otherwise sorts to the top).
- **Requests is `increase()` (a count), not `rate()`.** The slow routes here are rare (export/candelete
  run a handful of times an hour); a per-second rate rounds to `0.00` and reads as broken.
- **Panel 12 is the same join with five queries.** A avg, B requests, C total time, D 4xx, E 5xx — all
  `format: table` + `version: v0`, merged on `path, method`. C/D/E are guarded with `and (<B> > 0)` like
  A, and D/E are filled with `or (<B> * 0)` so routes without errors show `0` rather than an empty cell
  (an empty cell would sort unpredictably and read as "unknown").
- **Panel 5 uses a log2 y-axis** (`scaleDistribution: log`). A single slow-but-rare route
  (`/v3/export/resources`, multiple seconds) otherwise compresses every other route into the baseline.
  An earlier `topk()` was removed — in a range graph it re-picks members every step and renders as
  flicker; the log axis is the real fix.
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
  `/health`+`/version` barely moves it. A per-route-group average (Route group filter) separates "are
  reads fast" from "are searches fast".
- Panel 7's **Project** and **Scoped to** columns are live (DEV-7031): `gravsearch.project_shortcodes` is a
  bounded, sorted, comma-separated set and `gravsearch.project_restriction` a single project IRI, both on the
  `gravsearch` root span, and both stay off the Alloy spanmetrics dimension list (which selects only
  `gravsearch.query.shape`, `stack`, `domain`, `environment`), so neither adds a Prometheus label. Remaining follow-up: once traces from before that release have aged out of retention, switch the
  project filter from the query text to the attribute — see the panel-7 bullet above for why not yet.
