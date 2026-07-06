# TraceQL Recipes

Ready-to-run [TraceQL](https://grafana.com/docs/tempo/latest/traceql/) queries for hunting slow
Gravsearch queries. Run them in **Grafana → Explore**, datasource **`grafanacloud-dasch-traces`**
(Tempo), TraceQL tab.

The `gravsearch` span is an `INTERNAL` span nested under the request's HTTP `SERVER` span, so
selecting on `span:name = "gravsearch"` targets the responder regardless of which endpoint triggered
it. See the [Gravsearch Trace Runbook](gravsearch-trace-runbook.md) for how to read the results.

## Grafana Explore starter

Open this Explore link (datasource and query pre-filled) and adjust the time range:

[Slow Gravsearch traces — open in Grafana Explore](https://dasch.grafana.net/explore?left=%7B%22datasource%22%3A%22grafanacloud-traces%22%2C%22queries%22%3A%5B%7B%22query%22%3A%22%7B+span%3Aname+%3D+%5C%22gravsearch%5C%22+%5Cu0026%5Cu0026+span%3Aduration+%5Cu003e+2s+%7D%22%2C%22queryType%22%3A%22traceql%22%2C%22refId%22%3A%22A%22%7D%5D%2C%22range%22%3A%7B%22from%22%3A%22now-6h%22%2C%22to%22%3A%22now%22%7D%7D)

## 1. Slow Gravsearch traces (the main recipe)

```traceql
{ span:name = "gravsearch" && span:duration > 2s }
```

!!! warning "Set the threshold relative to the baseline, not a constant"
    `2s` is a placeholder. The threshold should be **baseline-relative** — e.g. the production p95
    of `gravsearch` duration. Compute the live baseline with the metrics query in
    [§2](#2-compute-the-baseline-p50p95p99) and substitute its p95. (The production baseline is
    being captured under the Gravsearch performance-baseline work; until it lands, pick a value from
    §2 for your time range.)

## 2. Compute the baseline (p50/p95/p99)

TraceQL metrics turn the same selection into a time series — use it to derive the threshold above:

```traceql
{ span:name = "gravsearch" } | quantile_over_time(span:duration, .50, .95, .99)
```

Break the percentiles down by query shape (bounded, safe to group by) to see which *kinds* of query
are slow:

```traceql
{ span:name = "gravsearch" } | quantile_over_time(span:duration, .95) by (span.gravsearch.query.shape)
```

## 3. Drill into the dominant stage

Find traces where the triplestore prequery — the most common hotspot — is slow:

```traceql
{ span:name = "gravsearch.prequery.execute" && span:duration > 1s }
```

Or select slow `gravsearch` traces and return the main-query execution span using the descendant
operator (`>>` returns the right-hand spans that are descendants of the left):

```traceql
{ span:name = "gravsearch" && span:duration > 2s } >> { span:name = "gravsearch.mainquery.execute" }
```

## 4. Filter by query shape

The root span carries per-flag booleans, so you can isolate query *kinds* without any user data.
Slow queries that use a FILTER:

```traceql
{ span:name = "gravsearch" && span.gravsearch.shape.has_filter = true && span:duration > 2s }
```

Slow full-text or link-traversal queries:

```traceql
{ span:name = "gravsearch" && span.gravsearch.shape.is_fulltext = true && span:duration > 2s }
{ span:name = "gravsearch" && span.gravsearch.shape.has_link_traversal = true && span:duration > 2s }
```

Match on the composite shape label (regex is fully anchored — wrap with `.*`):

```traceql
{ span:name = "gravsearch" && span.gravsearch.query.shape =~ ".*has_union.*" }
```

## 5. Errors and interruptions

Any failed Gravsearch stage (the status description is sanitized — `"<stage>: <Class>"`, no SPARQL):

```traceql
{ span:name =~ "gravsearch.*" && span:status = error }
```

Queries that were interrupted (client disconnect / timeout / cancellation) — these are slow queries
that got cut off, and exactly what you are usually hunting:

```traceql
{ span:name =~ "gravsearch.*" && span.gravsearch.exit_reason = "interrupted" }
```

!!! note "Attribute name syntax"
    Custom span attributes use dot notation after `span.`, including dotted keys —
    `span.gravsearch.query.shape`, `span.gravsearch.shape.has_filter`,
    `span.gravsearch.exit_reason`. Intrinsics use a colon — `span:name`, `span:duration`,
    `span:status`, `trace:rootName`.
