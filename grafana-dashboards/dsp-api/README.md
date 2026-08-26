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

**Averages, not percentiles.** Every duration figure is `rate(sum)/rate(count)` on `phase="body"`
(full-request duration). This metric has **no histogram buckets**, so percentiles are not available
here — use the Gravsearch dashboard / Tempo for tail latency.

**Filters (scope every panel unless noted):**

- `environment`, `stack` — from `label_values` on the metric.
- `Route group` — a coarse, single-select bucket (`Search`, `Resources`, `Admin`, `Export`, …); the
  value is a path regex applied as `path=~"${routegroup}"`.
- `Route (path)`, `Method` — fine multi-select; the per-route panels (5, 6) layer these on top of the
  route group.
- `Exclude routes` — multi-select of path regexes applied as `path!~"${exclude:pipe}"`. Default
  excludes `/health` + `/version`. Safe when empty: PromQL fully anchors regex matchers, so an empty
  exclude drops only empty-path series, not everything.
- `Smoothing window` — **only** the `rate()` window (`[$smoothing]`) on the two time-series panels
  (4, 5). It changes how smooth those lines are (larger window → peaks averaged down, so the legend
  **Max** drops while the **Mean** stays put); it does **not** touch the stat tiles or tables, which
  use `$__range`. Keep it ≤ the dashboard time range or `rate()` runs short of data.

**Panels:**

| Panel | Question | Source |
| --- | --- | --- |
| 1 Avg response time | Mean full-request duration over the range | `$__range` |
| 2 Request rate | Requests/s (excludes monitoring when `/health`,`/version` excluded) | `$__rate_interval` |
| 3 5xx error rate | Share of 5xx over the range | `$__range` |
| 4 Avg duration — global | Mean duration over time (deploy-regression line) | `[$smoothing]` |
| 5 Avg duration by route | Mean duration per route over time | `[$smoothing]` |
| 6 Routes ranked by avg duration | Slowest routes now + how often they run | `$__range` |
| 7 Slowest Gravsearch queries (traces) | Individual slow gravsearch executions, their target + scoped project, + the query | Tempo |

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
