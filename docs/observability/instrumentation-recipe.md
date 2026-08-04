# Instrumentation Recipe

How to add per-stage tracing to a responder, using the pattern established for `SearchResponderV2`
(Gravsearch). Follow it to instrument a second vertical without re-deriving the design. The
reference implementation lives in
`modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala`.

The pattern in one sentence: open one **root** `INTERNAL` span named after the vertical, wrap each
pipeline stage in a child span via a small `stageSpan` helper, attach a **bounded shape fingerprint**
to the root as an attribute and the **raw submitted payload** to the root as an event, and make
failures and interruptions legible without widening what reaches span status or metric labels.

## 1. Wire `Tracing` into the service

Declare `tracing` as an **abstract member of the trait** (not only a constructor param of the live
impl) so that any default methods on the trait can open spans before delegating:

```scala
trait SearchResponderV2 {
  // Telemetry used to open the root span and its per-stage child spans. Declared as an abstract
  // member so the trait's default methods can open the root + parse spans before delegating.
  protected def tracing: Tracing
  // ...
}
```

Provide it in the live class and add `Tracing` to the module's `Dependencies` alias so
`ZLayer.derive` picks it up from the environment:

```scala
final class SearchResponderV2Live(
  // ...other deps...
  override protected val tracing: Tracing,
) extends SearchResponderV2

// SearchResponderV2Module.scala
type Dependencies = /* ...other deps... */ & Tracing
```

## 2. Add the `stageSpan` helper

Copy this helper (companion object of the responder). It opens an `INTERNAL` span that is
automatically a child of whatever span is active on the fiber, records a **sanitized** error on
failure, and marks interruptions — then maps the library status to `UNSET` so the library's own
status-setter is a no-op and never overwrites what we set.

```scala
def stageSpan[A](tracing: Tracing, name: String)(effect: Task[A]): Task[A] =
  tracing.span(name, SpanKind.INTERNAL, statusMapper = unsetOnFailure) {
    tracing.getCurrentSpanUnsafe.flatMap { span =>
      effect
        .tapErrorCause(cause => ZIO.succeed(markSanitizedError(span, name, cause)))
        .onExit {
          case Exit.Failure(cause) if cause.isInterrupted =>
            ZIO.succeed {
              val _ = span.setAttribute("gravsearch.exit_reason", "interrupted")
              val _ = span.setStatus(StatusCode.ERROR, "interrupted")
            }
          case _ => ZIO.unit
        }
    }
  }
```

A thin `protected final` wrapper on the trait lets methods call `stageSpan("name") { ... }` without
passing `tracing` each time:

```scala
protected final def stageSpan[A](name: String)(effect: Task[A]): Task[A] =
  SearchResponderV2.stageSpan(tracing, name)(effect)
```

The **root** span is opened with the same helper — there is no separate root helper. Open the root,
then open each stage inside it; FiberRef-carried context makes them children automatically.

## 3. Name the spans

- Root span = the vertical name: `gravsearch`.
- Stage spans = `<vertical>.<stage>`, lowercase, dotted, from a **bounded** set:
  `gravsearch.parse`, `gravsearch.type_inspection`, `gravsearch.prequery.generate`,
  `gravsearch.prequery.execute`, `gravsearch.mainquery.generate`, `gravsearch.mainquery.execute`,
  `gravsearch.result_transform`.
- Never put variable data (IRIs, counts, user input) in a span name — that explodes cardinality.
  Variable data goes in attributes, bounded data goes in the shape (step 5).

## 4. Wrap each stage — and omit stages that did not run

Wrap each stage effect in `stageSpan`. For example, the main-query trio runs only when the prequery
returned at least one resource — keep those spans **inside** the conditional so an empty result
simply has no main-query spans, rather than zero-duration placeholders:

```scala
mainQueryResults <-
  if (mainResourceIris.nonEmpty) {
    for {
      sparql   <- stageSpan("gravsearch.mainquery.generate")(/* build SPARQL */)
      response <- stageSpan("gravsearch.mainquery.execute")(/* triplestore.query(...) */)
      result   <- stageSpan("gravsearch.result_transform")(/* permission filter + assemble */)
    } yield result
  } else {
    ZIO.attempt(/* empty result */)
  }
```

Absent spans are a documented, legible signal — see the runbook's
[four absent-data topologies](gravsearch-trace-runbook.md#7-absent-spans-four-normal-topologies).
The triplestore `CLIENT` span nests automatically under the `*.execute` stage because it runs inside
that stage's effect.

## 5. Attach a bounded shape as an attribute, never raw payload

The single most important attribute rule: **never** set raw query text, instance IRIs, or user IDs
as span attributes. Derive a bounded *shape* from the parsed query and attach that to the root span
instead — the raw text has a different home, see [step 6](#6-capture-the-raw-payload-as-an-event):

```scala
def setShapeOnRoot(tracing: Tracing, query: ConstructQuery, resultType: QueryResultType): UIO[Unit] =
  tracing.getCurrentSpanUnsafe.map { span =>
    val shape = queryShape(query, resultType)
    val _     = span.setAttribute("gravsearch.query.shape", shape.label)
    val _     = span.setAttribute("gravsearch.schema_predicates", shape.predicates.mkString(","))
    shape.flags.foreach { case (flag, value) => val _ = span.setAttribute(s"gravsearch.shape.$flag", value) }
  }
```

Split the cardinality deliberately:

| Kind | Example | Cardinality | Use as |
| --- | --- | --- | --- |
| Composite shape label | `gravsearch.query.shape` = `resource-list\|has_filter\|patterns:4-7\|joins:1` | Bounded (enums + bucketed counts) | **Span attribute, safe as a metric label** |
| Per-flag booleans | `gravsearch.shape.has_filter` = `true` | Bounded (fixed flag set) | Span attribute (for TraceQL filtering) |
| Ontology predicate names | `gravsearch.schema_predicates` = `hasTitle,isPartOf` | Higher (but ontology-bounded, never instance IRIs) | **Span attribute only — never a metric label** |

Bucket open-ended counts (pattern count, join count) into ranges (`0`, `1`, `2-3`, `4-7`, `8+`) so
the shape label stays bounded. Set the shape on the root immediately after parse succeeds.

## 6. Capture the raw payload as an event

The shape tells you *what kind* of request this was; it does not tell you *which* request, so a slow
trace cannot be reproduced from the shape alone. Capture the raw submitted payload — the query string,
the request body — as a span **event** on the root span:

```scala
def recordQueryOnRoot(tracing: Tracing, query: IRI): UIO[Unit] =
  tracing.getCurrentSpanUnsafe.map { span =>
    val _ = span.addEvent("gravsearch.query", Attributes.of(DbAttributes.DB_QUERY_TEXT, query))
  }
```

Three rules, all load-bearing:

**Event, not attribute.** The Alloy `otelcol.connector.spanmetrics` dimension list reads span
attributes, so raw payload in an attribute is one config line away from becoming an unbounded
Prometheus label. An event attribute is unreachable from there — the cardinality accident becomes
impossible rather than merely forbidden — while staying fully searchable in Tempo via the `event:name`
/ `event.<key>` scope.

**Root span only, and never inside `stageSpan`.** Stage spans are asserted to carry *no* events at
all; that assertion is what locks the sanitized-error contract in step 7. Putting payload on a stage
span would quietly retire the lock.

**Record it before the first stage.** The shape is derived after parsing, so a payload that fails to
parse never gets one. Capturing at entry means the least-diagnosable trace gains the most, at no extra
cost.

Naming follows OpenTelemetry Semantic Conventions where they reach:

| Thing | Name | Why |
| --- | --- | --- |
| Payload attribute | `db.query.text` (use the generated `DbAttributes.DB_QUERY_TEXT`) | The stable semconv key for query text, and the rename target of the deprecated `db.statement`. Pairs with the shape attribute, which is `db.query.summary` semantics |
| Event name | `<vertical>.query`, e.g. `gravsearch.query` | Semconv defines no event name for "payload captured", so this one is ours. Keep it bounded and predictable |

Two caveats worth stating rather than glossing: semconv scopes `db.*` to database queries, and the
statement actually sent to the database here is the *generated* SPARQL, not the client's Gravsearch;
and the event carries **unredacted user input**, which is a deliberate, signed-off position for
Gravsearch (search terms, not record content) — re-decide it per vertical rather than inheriting it.

## 7. Errors and interruptions without leaks

**Use `SanitizedSpan.withSpan` rather than writing this yourself.**
`org.knora.webapi.slice.infrastructure.SanitizedSpan` implements everything in this section — the
`UNSET` failure mapper, the sanitized `ERROR` status, `error.type`, and the interruption branch — and
hands your effect the raw span so it can attach its own bounded attributes:

```scala
SanitizedSpan.withSpan(tracing, "admin.sparql.query", "sparql_passthrough.exit_reason") { span =>
  effect // attach bounded attributes to `span`; failures and interruptions are handled for you
}
```

The exit-reason key is the caller's, because it belongs to the caller's attribute namespace and is
what its own TraceQL queries select on. `SearchResponderV2.stageSpan` is a two-line wrapper over this
that supplies `gravsearch.exit_reason`. The rest of this section explains what the helper does and
why, so that a change to it is made knowingly — it is not an invitation to copy the snippets.

The error handling has one load-bearing invariant. `zio-telemetry` writes `cause.prettyPrint` into
the span status description on the `ERROR` branch — and for a SPARQL failure that string echoes the
offending FILTER literal (user data). To prevent the leak, the failure status mapper **must** map to
`UNSET` (which the OTel SDK no-ops), and we set our own sanitized status separately:

```scala
// LOAD-BEARING: must map to UNSET, never ERROR — UNSET is what stops cause.prettyPrint
// (which echoes the user's FILTER literal) from reaching the span status description.
private val unsetOnFailure: StatusMapper[Throwable, Any] =
  StatusMapper.failureNoException[Throwable](_ => StatusCode.UNSET)

/** Writes the sanitized ERROR status ("<stage>: <Class>", no message) + error.type onto the span. */
private def markSanitizedError(span: Span, stage: String, cause: Cause[Throwable]): Unit = {
  val kind = cause.failureOption.map(_.getClass.getSimpleName).getOrElse("defect")
  val _    = span.setStatus(StatusCode.ERROR, s"$stage: $kind")
  cause.failureOption.foreach { e => val _ = span.setAttribute("error.type", e.getClass.getSimpleName) }
}
```

- **Typed failure** → status `ERROR`, description exactly `"<stage>: <ClassName>"` (e.g.
  `gravsearch.prequery.execute: TriplestoreException`), plus `error.type`. No message, no stacktrace.
- **Defect** → status `ERROR`, description exactly `"<stage>: defect"`, no `error.type`. The status
  mapper alone does *not* achieve this: `zio-telemetry` reaches the mapper through
  `cause.failureOption`, which a `Cause.Die` does not have, so a defect falls through to the same
  `ERROR` + `cause.prettyPrint` default. `SanitizedSpan.withSpan` therefore carries a defect across
  the span boundary as a typed failure and re-throws it as a defect once the span has closed. Callers
  still observe a defect; the span never sees its message.
- **Interruption** → `<vertical>.exit_reason = interrupted` + status `ERROR "interrupted"` (set in
  `SanitizedSpan.withSpan`'s `onExit`, which is uninterruptible and so still runs during teardown).
  OTel has no `cancelled` status, so this attribute is what distinguishes an interrupted query from a
  typed failure and from a benign empty result. This is the one cause whose status *description* is
  still the library's, because a cause cannot be converted without swallowing the interrupt — safe
  rather than accepted, since an interrupt cause carries no message. Select on the attribute, not the
  description.

!!! danger "Do not relax the status mapper"
    Changing `unsetOnFailure` to map failures to `ERROR` re-introduces the `cause.prettyPrint` leak
    in one edit. It is guarded by a description-equality test — keep that test.

!!! note "What this covers, and what it does not"
    The helper protects the span it opens. Two things outside it are worth knowing, both API-wide and
    pre-existing. The outer HTTP SERVER span in `DspApiServer` uses a `StatusMapper.Success[Response]`,
    and nearly every failure arrives there as a 500 *response* — ztapir's `zServerLogic` wraps the logic
    in `.either.resurrect`, promoting a defect to a typed failure before the interpreter — so that span
    gets `ERROR` with no description. Only what stays in the routes' error channel (an interrupt, a
    non-`NonFatal` throwable) still takes zio-telemetry's `cause.prettyPrint` default. And tapir's
    default `serverLog` is not customised, so a defect reaching the server logic is additionally written
    to the **log** at ERROR with its raw message and stacktrace. Sanitizing the span does not sanitize
    that line.

## Checklist for a new vertical

- [ ] `tracing` is an abstract member of the trait; `Tracing` added to the module `Dependencies`.
- [ ] One root `INTERNAL` span named after the vertical; one child span per stage, bounded names.
- [ ] Stages that may not run are wrapped *inside* their conditional (no placeholder spans).
- [ ] A bounded shape on the root; no raw text / instance IRIs / user IDs as attributes.
- [ ] Cardinality split: composite label + booleans are metric-safe; predicate lists are drill-down only.
- [ ] Raw payload captured as an **event** on the root span, before the first stage — never an attribute, never inside `stageSpan`; `db.query.text` for query text.
- [ ] Spans opened through `SanitizedSpan.withSpan` (failure mapper `UNSET`, sanitized `ERROR` + `error.type`, interruption sets `exit_reason`).
- [ ] A test asserting the failure status description equals `"<stage>: <Class>"` (no message), and one
      asserting a **defect** yields `"<stage>: defect"` — the two go through different mechanisms.
- [ ] A test asserting the payload event carries the exact submitted text, on both the success and the parse-failure path.
