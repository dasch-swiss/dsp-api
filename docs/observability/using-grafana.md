# Using Grafana

How to actually run the queries from the rest of this section — both in the **Grafana UI** and from
**Claude Code via the Grafana MCP server**. The [TraceQL Recipes](traceql-recipes.md) and
[Gravsearch Trace Runbook](gravsearch-trace-runbook.md) tell you *what* to ask; this page tells you
*where to type it*.

## Where Grafana lives

| Environment | URL | Tempo datasource | Auth |
| --- | --- | --- | --- |
| **Production / dev (Grafana Cloud)** | [dasch.grafana.net](https://dasch.grafana.net) | `grafanacloud-dasch-traces` | DaSCH SSO |
| **Local dev stack** | <http://localhost:3001> | Tempo (built into `otel-lgtm`) | anonymous (admin) |

The local stack ships a single `grafana/otel-lgtm` container (Grafana + Tempo + Loki + Prometheus +
Pyroscope) wired to the same OTLP endpoint the services export to, so traces you generate locally are
queryable with the *same* TraceQL — only the datasource name and URL differ. Bring it up with
`just stack-start` (or `docker compose up alloy`); the API, Sipi and Fuseki export to it
automatically. Grafana is on port **3001** locally (3000 is taken), login is anonymous with admin
rights.

## In the Grafana UI

The flow behind every recipe in this section:

1. Open **Explore** (compass icon in the left nav).
2. Pick the **Tempo** datasource — `grafanacloud-dasch-traces` in the cloud, `Tempo` locally.
3. Select the **TraceQL** query tab (not "Search") and paste a query from
   [TraceQL Recipes](traceql-recipes.md), e.g. `{ span:name = "gravsearch" && span:duration > 2s }`.
4. Adjust the **time range** (top right) — traces are short-lived, so widen it if a result set is
   empty.
5. Click a row to open the trace, then read the span tree per the
   [Runbook](gravsearch-trace-runbook.md).

A few UI affordances worth knowing:

- **TraceQL metrics** (the `| quantile_over_time(...)` recipes) render as a time-series graph rather
  than a trace list — switch the panel to the graph view.
- **Metrics** (Prometheus) and **logs** (Loki) are separate datasources in the same Explore view; a
  trace ID pivots to its logs via the trace-to-logs link (see the trace ↔ log correlation note in the
  [Overview](index.md)).
- The [Grafana Explore starter link](traceql-recipes.md#grafana-explore-starter) opens Explore with
  the datasource and query pre-filled — the fastest way in.

## From Claude Code (Grafana MCP)

The [Grafana MCP server](https://github.com/grafana/mcp-grafana) (`mcp-grafana`) lets Claude Code
query Grafana directly — run TraceQL, fetch a trace, compute baselines, query Prometheus, and read
dashboards — without you leaving the editor or hand-building Explore URLs. It is the fastest way to
turn "why was this Gravsearch slow?" into an answer while you are already in a coding session.

!!! note "Setup is per-developer, not in this repo"
    The MCP server is configured in your Claude Code settings, not committed here, because it needs a
    Grafana **service-account token**. Point an `mcp-grafana` server at `https://dasch.grafana.net`
    with a token that has at least Viewer + datasource-query access, and Claude Code will expose the
    tools below (prefixed `mcp__<server-name>__`, e.g. `mcp__grafana-cloud__`). A second server
    pointed at `http://localhost:3001` lets you query the local stack the same way.

### Mapping the recipes to MCP tools

Every recipe in [TraceQL Recipes](traceql-recipes.md) has a direct MCP equivalent. Hand Claude the
query — or just describe the goal — and it calls the right tool:

| Goal (see the recipe) | MCP tool | Notes |
| --- | --- | --- |
| Run a TraceQL search ([§1](traceql-recipes.md#1-slow-gravsearch-traces-the-main-recipe), [§3](traceql-recipes.md#3-drill-into-the-dominant-stage)–[§5](traceql-recipes.md#5-errors-and-interruptions)) | `tempo_traceql-search` | Pass the TraceQL string verbatim; returns matching traces |
| Open one trace and read its span tree ([Runbook §2](gravsearch-trace-runbook.md#2-the-span-tree)) | `tempo_get-trace` | Pass the trace ID from a search result |
| Compute the baseline p50/p95/p99 ([§2](traceql-recipes.md#2-compute-the-baseline-p50p95p99)) | `tempo_traceql-metrics-instant` / `tempo_traceql-metrics-range` | The `quantile_over_time(...)` queries; instant for a point, range for a series |
| Discover available span attributes / values | `tempo_get-attribute-names` / `tempo_get-attribute-values` | Useful to confirm a `gravsearch.shape.*` flag exists before filtering on it |
| Check TraceQL syntax | `tempo_docs-traceql` | The server's own TraceQL reference |
| Query a Prometheus metric (e.g. the [metrics endpoint](../03-endpoints/instrumentation/metrics.md) series) | `query_prometheus` / `query_prometheus_histogram` | PromQL; histogram helper for `_bucket` series |
| List metric names / labels | `list_prometheus_metric_names`, `list_prometheus_label_values` | Explore what is emitted |
| Find / read a dashboard | `search_dashboards`, `get_dashboard_by_uid`, `get_dashboard_panel_queries` | Reuse a panel's query as a starting point |
| Hand a result back as a clickable Explore link | `generate_deeplink` | Produces a shareable Grafana URL for a human to open |

### Example prompts

Phrase the goal; let Claude pick the datasource and tool. For example:

- *"Using the Grafana MCP, find Gravsearch traces slower than 2s in the last 6 hours and tell me which
  stage dominates."* — runs `tempo_traceql-search` with the
  [main recipe](traceql-recipes.md#1-slow-gravsearch-traces-the-main-recipe), opens the slowest with
  `tempo_get-trace`, and reads the time decomposition.
- *"What's the current p95 of the `gravsearch` span, broken down by query shape?"* — runs the
  [baseline metrics recipe](traceql-recipes.md#2-compute-the-baseline-p50p95p99) via
  `tempo_traceql-metrics-instant`.
- *"Show me Gravsearch traces that were interrupted in the last hour."* — runs the
  [interruption recipe](traceql-recipes.md#5-errors-and-interruptions)
  (`span.gravsearch.exit_reason = "interrupted"`).

!!! tip "Use the local stack for verifying instrumentation"
    When you add or change instrumentation (see the [Instrumentation Recipe](instrumentation-recipe.md)),
    point an MCP server at the local `otel-lgtm` stack, generate a request, and ask Claude to fetch the
    trace and confirm the span tree and attributes match what you intended — a fast feedback loop that
    does not touch production data.

## See also

- [TraceQL Recipes](traceql-recipes.md) — the queries to run here.
- [Gravsearch Trace Runbook](gravsearch-trace-runbook.md) — how to read what comes back.
- [Instrumentation Recipe](instrumentation-recipe.md) — what produces the spans in the first place.
