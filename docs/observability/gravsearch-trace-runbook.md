# Gravsearch Trace Runbook

A one-page guide to diagnosing a slow Gravsearch query from its trace. The goal is that you can open
a trace cold and name the dominant stage and read the time decomposition without prior briefing.

## 1. Find a slow trace

Open **Grafana → Explore → `grafanacloud-dasch-traces`** (Tempo), TraceQL tab, and run the
slow-query recipe:

```traceql
{ span:name = "gravsearch" && span:duration > 2s }
```

See **[TraceQL Recipes](traceql-recipes.md)** for the full set (threshold relative to the baseline
p95, drill-down by stage, filter by query shape, find errors and interruptions). Click a result to
open the trace, then find the `gravsearch` span — it is an `INTERNAL` span nested under the HTTP
`SERVER` span for the request.

## 2. The span tree

A full-path query (the prequery returned at least one main resource) produces this tree:

```mermaid
graph TD
  H["HTTP SERVER span<br/>(endpoint path template)"] --> G["gravsearch (INTERNAL, root)"]
  G --> P["gravsearch.parse"]
  G --> TI["gravsearch.type_inspection"]
  G --> PG["gravsearch.prequery.generate"]
  G --> PE["gravsearch.prequery.execute"]
  PE --> TSP["triplestore query<br/>(CLIENT span)"]
  G --> MG["gravsearch.mainquery.generate"]
  G --> ME["gravsearch.mainquery.execute"]
  ME --> TSM["triplestore query<br/>(CLIENT span)"]
  G --> RT["gravsearch.result_transform"]
```

All stage spans are direct children of the `gravsearch` root. The triplestore round-trips appear as
`CLIENT` spans nested **under** the two `*.execute` stages — that nesting is how you separate time
spent *in the triplestore* from time spent generating SPARQL or transforming results.

## 3. What each stage means

| Stage span | Measures | Typical cost driver |
| --- | --- | --- |
| `gravsearch.parse` | Parsing the Gravsearch string into a `ConstructQuery` AST | Negligible; only notable on parse failure |
| `gravsearch.type_inspection` | Inferring entity/value types for the query | Large or deeply-typed queries |
| `gravsearch.prequery.generate` | Building the prequery SPARQL (resource IRIs + ordering) | Complex WHERE clauses, many patterns/joins |
| `gravsearch.prequery.execute` | Running the prequery against Fuseki (CLIENT span nested here) | **Most common hotspot** — triplestore time |
| `gravsearch.mainquery.generate` | Building the main query for the page of resource IRIs | Many properties / large page |
| `gravsearch.mainquery.execute` | Running the main query against Fuseki (CLIENT span nested here) | Triplestore time for the value graph |
| `gravsearch.result_transform` | Permission filtering + assembling the API response | Large result pages, heavy markup |

A **count** query (`/v2/searchextended/count`) runs only the prequery side: `gravsearch.parse`,
`gravsearch.type_inspection`, `gravsearch.prequery.generate`, `gravsearch.prequery.execute` under
the root — four prequery-side stages, no main-query or result-transform spans. This is expected
(see [§7](#7-absent-spans-four-normal-topologies)), not a truncated trace.

## 4. Root-span attributes

The `gravsearch` root span carries a **query shape** — a bounded fingerprint of *what kind* of query
this was, with no user data in any attribute. Use it to group "queries like this one" without
aggregating over FILTER literals or instance IRIs. It also carries the **target project(s)**, so you
can tell whose data a slow query was reading without opening the query text. For the query *itself*,
see [§5](#5-read-the-submitted-query) — it is on the same span, as an event.

| Attribute | Example | Use |
| --- | --- | --- |
| `gravsearch.query.shape` | `resource-list\|has_filter\|has_order_by\|patterns:4-7\|joins:1` | Bounded label; safe to group/aggregate by. Format: result-type, then each true flag, then `patterns:<bucket>` and `joins:<bucket>` (buckets: `0`, `1`, `2-3`, `4-7`, `8+`) |
| `gravsearch.shape.has_filter` | `true` | Per-flag booleans for TraceQL filtering — also `has_optional`, `has_union`, `has_order_by`, `has_offset`, `has_link_traversal`, `is_fulltext` |
| `gravsearch.schema_predicates` | `hasTitle,isPartOf` | Sorted, de-duplicated **ontology** predicate names only (never instance IRIs). Drill-down detail, not a metric label |
| `gravsearch.project_shortcodes` | `0803` | Sorted, de-duplicated shortcodes of the projects the query **refers to**, inferred from its IRIs. Usually one; a cross-project query lists several. Always present — an empty value means no project was identifiable (a query over built-in ontologies only). Drill-down detail, not a metric label |
| `gravsearch.project_restriction` | `http://rdfh.ch/projects/0803` | The project the request was explicitly **scoped to** (`limitToProject` and the project-restricted entry points). Present only when the search was restricted |

The two project attributes answer different questions, which is why they are separate. `project_shortcodes`
is inferred from the query — "which projects does this query touch". `project_restriction` is stated by the
caller — "which project was this search scoped to". A cross-project query has several of the first and none
of the second.

!!! note "Why the restriction is an IRI, not a shortcode"
    `limitToProject` arrives from the request as a `ProjectIri`, and a project IRI created after the
    shortcode-shaped ones were retired is `http://rdfh.ch/projects/<base64-UUID>`. Turning it into a
    shortcode needs a project lookup on the pre-parse path of every Gravsearch. So for older projects the
    IRI ends in the shortcode and lines up with `project_shortcodes` by eye, and for newer ones it does
    not — a known limitation. To find every trace for one project, filter on `project_shortcodes`.

!!! note "Shortcodes come from data IRIs too, not only ontology IRIs"
    A project shortcode is carried by both `…/ontology/0803/incunabula/simple/v2#book` and
    `http://rdfh.ch/0803/c5058f3a`, and the derivation reads both. That is what makes the attribute useful
    on the internally generated searches ([§7](#not-every-captured-query-came-from-a-researcher)) — incoming
    links, still-image representations, incoming regions — whose queries reference built-in ontologies
    exclusively and interpolate the target resource IRI. Reading ontology IRIs alone would leave every one
    of those spans blank. Only the shortcode reaches the span, never the IRI it came from.

On a failed or interrupted stage span you may also see:

| Attribute / field | Meaning |
| --- | --- |
| span status `ERROR`, description `"<stage>: <ClassName>"` | A typed stage failure, e.g. `gravsearch.prequery.execute: TriplestoreException`. The message is sanitized — never the raw SPARQL or FILTER literal |
| `error.type` | The exception class simple name |
| `gravsearch.exit_reason = interrupted` | The fiber was interrupted (client disconnect / timeout / cancellation) — see [§7](#7-absent-spans-four-normal-topologies) |

## 5. Read the submitted query

The root `gravsearch` span carries the **query the client submitted, verbatim**, as a span **event**:

| Field | Value |
| --- | --- |
| Event name | `gravsearch.query` |
| Event attribute | `db.query.text` — the query string exactly as submitted, unredacted and untruncated |

Every Gravsearch execution carries it, client-submitted or internally generated (see
[§7](#not-every-captured-query-came-from-a-researcher)).

In the Grafana trace view, select the `gravsearch` root span and open its **Events** section (next to
Attributes). Copy the text and re-run it against dev or prod to reproduce the slow query — this is
the step that turns "this trace was slow" into "this query is slow".

In TraceQL, events have their own scope — `event:name` for the event name, `event.<key>` for its
attributes:

```traceql
{ span:name = "gravsearch" && event:name = "gravsearch.query" }
{ span:name = "gravsearch" && event.db.query.text =~ ".*incunabula:title.*" }
```

Regex matches are fully anchored, hence the leading and trailing `.*`.

It is recorded **before** the parse stage, so it is present even when the query is malformed. That is
deliberate: the parse-failure and shape-less-early-interrupt topologies in
[§7](#7-absent-spans-four-normal-topologies) have almost nothing else on them, and the query text is
what makes those traces diagnosable at all.

!!! note "Why an event and not a span attribute"
    Query text is unbounded user input. The Alloy `otelcol.connector.spanmetrics` dimension list reads
    **span attributes**, so an attribute is one config line away from becoming an unbounded Prometheus
    label. An event attribute is not reachable that way, and stays fully searchable in Tempo via the
    event scope — nothing is lost for diagnosis. Keep query text in the event.

## 6. Reading the time decomposition

1. Note the **root `gravsearch` duration** — that is the responder's total.
2. Walk the stage spans in order; the one with the largest duration is the dominant stage.
3. For an `*.execute` stage, compare the stage duration with its **nested triplestore `CLIENT`
   span**: if they are close, the time is in Fuseki; if the stage is much longer than the client
   span, the time is in DSP-API around the query.
4. Stage durations do not perfectly sum to the root (there is glue between stages), but one stage
   normally dominates. `gravsearch.prequery.execute` is the most common hotspot.

## 7. Absent spans: four normal topologies

The instrumentation deliberately **omits** spans for work that did not happen rather than emitting
zero-duration placeholders. So a trace with fewer than eight spans is usually *correct*. Four
distinct shapes look like "missing spans" but each means something specific — do not read any of
them as broken instrumentation, and do not mistake one for another.

| Topology | What you see | What it means | Tell-tale |
| --- | --- | --- | --- |
| **Empty result** | parse → type_inspection → prequery.generate → prequery.execute present; **no** `mainquery.*`, **no** `result_transform` | The prequery returned zero main resources, so there was nothing to fetch — "no rows", not an error | All present spans are `OK`; root has its shape attributes |
| **Parse failure** | root + `gravsearch.parse` only, parse span is `ERROR` | The Gravsearch string was malformed; the pipeline never started | Only the parse span exists and it is `ERROR` (`gravsearch.parse: <Class>`) |
| **Interruption / timeout** | early stages present, later stages absent, **last open span + root are `ERROR`** | The request fiber was interrupted (client disconnect, timeout, cancellation) mid-query | `gravsearch.exit_reason = interrupted` on the open span and the root |
| **Shape-less early interrupt** | root present but **without `gravsearch.query.shape` / `gravsearch.shape.*` / `gravsearch.project_shortcodes`**, little or nothing below it | Interrupted (or failed) *before* parse completed, so neither the shape nor the target project was ever derived | Missing shape attributes **and** `exit_reason = interrupted` / `ERROR` on the root — not a broken shape derivation. Note the difference from an *empty* `project_shortcodes`, which means the derivation ran and found no project |

How to tell them apart quickly:

- **Later stages missing + everything `OK` + shape present** → empty result. Benign.
- **Only the parse span + it is `ERROR`** → parse failure. Look at the client's query, not the
  instrumentation — read it off the root span's `gravsearch.query` event ([§5](#5-read-the-submitted-query)).
- **Later stages missing + an `ERROR` span carrying `exit_reason = interrupted`** → interruption.
  The query was probably slow and got cancelled — this is exactly the trace you are hunting; read
  the stages that *did* run to see where the time went before the cut.
- **Root has no shape attributes at all** → shape-less early interrupt. The interruption happened so
  early that parse/shape never ran; the absence of shape is expected, not a bug.

### Not every captured query came from a researcher

The `gravsearch.query` event is on **every** `gravsearch` root span, not only on client searches.
dsp-api runs Gravsearch internally for several features and deliberately routes those through the
same string entry point so they get the root span and the shape: incoming links, still-image
representations, incoming regions, resources-by-project-and-class, the XSLT-template resource fetch,
and the post-update resource read. Their query text is **generated by dsp-api**, with the target
resource IRI interpolated into it.

So when a captured query looks machine-written and hard-codes a resource IRI, that is an internal
search — read it as a dsp-api feature doing work on behalf of a request, not as a query some
researcher composed. It is still exactly the text that was executed, so it is still the thing to
re-run when the trace is slow.

!!! note "Why interruption is called out separately"
    OTel span status has no `cancelled` value (only `Unset`/`Ok`/`Error`). Without the
    `gravsearch.exit_reason = interrupted` attribute, an interrupted slow query — early stages
    present, later stages absent — would be indistinguishable from a benign empty result, and from a
    typed stage failure. The attribute is what disambiguates them.
