# Observability

DSP-API is instrumented with [OpenTelemetry](https://opentelemetry.io/) traces, exported to
[Grafana Tempo](https://grafana.com/oss/tempo/) and explored in Grafana. This section is the
engineer-facing layer on top of that instrumentation: how to read the traces, how to query them,
and how to add new instrumentation.

## What is instrumented

- **HTTP server spans** — every request served by the API produces a root `SERVER` span, named
  from the endpoint path template (low cardinality, not the concrete URL).
- **Trace-context propagation** — the `traceparent` header is propagated from DSP-APP through the
  API and on to the Fuseki triplestore, so a single trace spans the whole request path.
- **Trace ↔ log correlation** — log lines carry the trace ID, so a trace can be pivoted to its logs
  and back.
- **Per-stage Gravsearch spans** — the `SearchResponderV2` is instrumented at responder granularity:
  a `gravsearch` root span with one child span per pipeline stage. This is the first vertical
  instrumented this deeply and the worked example for the rest of this section.

## Where to look

Traces live in the **`grafanacloud-dasch-traces`** Tempo datasource. Open **Grafana → Explore**,
select that datasource, and use the **TraceQL** query tab. See [Using Grafana](using-grafana.md) for
the UI walkthrough, the local-stack equivalent, and how to run all of this from Claude Code via the
Grafana MCP server. The metrics endpoint
([Metrics Endpoint](../03-endpoints/instrumentation/metrics.md)) and health endpoint remain the
place for Prometheus-format metrics and liveness — tracing complements them, it does not replace
them.

## Guides

- **[Using Grafana](using-grafana.md)** — where Grafana lives (cloud and local stack), the Explore /
  TraceQL UI flow, and how to run every recipe from Claude Code via the Grafana MCP server.
- **[Gravsearch Trace Runbook](gravsearch-trace-runbook.md)** — find a slow Gravsearch trace, read
  the per-stage time decomposition, and interpret each span and attribute (including the cases where
  *absent* spans are normal, not broken instrumentation).
- **[TraceQL Recipes](traceql-recipes.md)** — ready-to-run TraceQL queries and a Grafana Explore
  starter for hunting slow Gravsearch queries.
- **[Instrumentation Recipe](instrumentation-recipe.md)** — the pattern used to instrument
  `SearchResponderV2`, written so a second vertical can be instrumented without re-deriving it.
