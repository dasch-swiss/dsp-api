# Instrumentation Recipe

How to add per-stage tracing to a responder, using the pattern established for `SearchResponderV2`
(Gravsearch). Follow it to instrument a second vertical without re-deriving the design. The
reference implementation lives in
`modules/webapi/src/main/scala/org/knora/webapi/responders/v2/SearchResponderV2.scala`.

The pattern in one sentence: open one **root** `INTERNAL` span named after the vertical, wrap each
pipeline stage in a child span via a small `stageSpan` helper, attach a **bounded shape fingerprint**
to the root, and make failures and interruptions legible without leaking user data.

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
[four absent-data topologies](gravsearch-trace-runbook.md#6-absent-spans-four-normal-topologies).
The triplestore `CLIENT` span nests automatically under the `*.execute` stage because it runs inside
that stage's effect.

## 5. Attach a bounded shape, not user data

The single most important attribute rule: **never** set raw query text, instance IRIs, or user IDs
as attributes. Instead derive a bounded *shape* from the parsed query and attach it to the root span:

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

## 6. Errors and interruptions without leaks

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
- **Interruption** → `gravsearch.exit_reason = interrupted` + status `ERROR "interrupted"` (set in
  `stageSpan`'s `onExit`). OTel has no `cancelled` status, so this attribute is what distinguishes an
  interrupted query from a typed failure and from a benign empty result.

!!! danger "Do not relax the status mapper"
    Changing `unsetOnFailure` to map failures to `ERROR` re-introduces the `cause.prettyPrint` leak
    in one edit. It is guarded by a description-equality test — keep that test.

## Checklist for a new vertical

- [ ] `tracing` is an abstract member of the trait; `Tracing` added to the module `Dependencies`.
- [ ] One root `INTERNAL` span named after the vertical; one child span per stage, bounded names.
- [ ] Stages that may not run are wrapped *inside* their conditional (no placeholder spans).
- [ ] A bounded shape on the root; no raw text / instance IRIs / user IDs as attributes.
- [ ] Cardinality split: composite label + booleans are metric-safe; predicate lists are drill-down only.
- [ ] Failure mapper maps to `UNSET`; sanitized `ERROR` + `error.type` set explicitly; interruption sets `exit_reason`.
- [ ] A test asserting the failure status description equals `"<stage>: <Class>"` (no message).
