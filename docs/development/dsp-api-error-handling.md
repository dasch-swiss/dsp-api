# Error Handling

How dsp-api handles errors: when to use ZIO's typed error channel, when to die, and the narrow case where `throw` is still acceptable.

## Principle

Two categories, two mechanisms:

| Situation             | Mechanism                                         | Rust analogue       |
|-----------------------|---------------------------------------------------|---------------------|
| Recoverable failure   | ZIO error channel — `ZIO.fail`, typed `IO[E, A]`  | `Result::Err`       |
| Invariant violation   | `die` family — `ZIO.dieMessage`, `.orDieWith`     | `panic!` / `expect` |

A *recoverable failure* is one a caller could reasonably handle: a 4xx response, a business rule violation, a missing resource. An *invariant violation* is unreachable state — reaching it means our model is wrong. If you can describe under what conditions the caller would handle it, it's recoverable.

## Recoverable failures

### Exception hierarchy

dsp-api uses a sealed hierarchy of `Exception` subtypes as the failure values. These are not thrown — they're carried in the ZIO error channel.

```scala
sealed trait KnoraException extends Serializable

abstract class RequestRejectedException(msg: String) extends Exception(msg) with KnoraException
abstract class InternalServerException(msg: String) extends Exception(msg) with KnoraException

final case class BadRequestException(message: String)   extends RequestRejectedException(message)
final case class NotFoundException(message: String)     extends RequestRejectedException(message)
final case class ForbiddenException(message: String)    extends RequestRejectedException(message)
// …
```

- `RequestRejectedException` subtypes → 4xx responses
- `InternalServerException` subtypes → 5xx responses
- Each concrete error: `final case class` with smart constructors and a `DeriveJsonCodec.gen` companion

Use these — don't introduce a parallel set of errors.

### Style A — `Task[A]` with `ZIO.fail` (admin / v2)

The most common style in the codebase. The error channel is `Throwable`; the API edge inspects the subtype and maps to an HTTP status.

```scala
// webapi/src/main/scala/org/knora/webapi/slice/api/admin/AdminListRestService.scala:48
def listChange(user: User)(iri: ListIri, request: ListChangeRequest): Task[NodeInfoGetResponseADM] =
  for {
    _       <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
    project <- projectService.findById(request.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _       <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    response <- listsResponder.nodeInfoChangeRequest(request, uuid)
  } yield response
```

Helpers: `ZIO.fail`, `.someOrFail`, `.orElseFail`.

### Style B — typed `IO[E, A]`

When the error set is small and meaningful (one or two specific cases), prefer the typed form.

```scala
// webapi/src/main/scala/org/knora/webapi/slice/api/admin/model/ProjectsEndpointsRequestsAndResponses.scala:55
def toRestrictedView: IO[BadRequestException, RestrictedView] =
  (size, watermark) match {
    case (Some(size), None)      => ZIO.succeed(size)
    case (None, Some(watermark)) => ZIO.succeed(RestrictedView.Watermark.from(watermark.value))
    case _                       =>
      ZIO.fail(BadRequestException("Specify either the size or the watermark; if none was provided, include one."))
  }
```

The error type appears in the signature, so callers see exactly what they need to handle.

### Style C — V3 typed errors with explicit variants (mandatory for new v3 code)

V3 endpoints use `IO[V3ErrorInfo, A]` with error variants declared on the endpoint definition. The error type *is* the API contract: each variant maps to a status code and a `V3ErrorCode` enum value.

```scala
// webapi/src/main/scala/org/knora/webapi/slice/api/v3/projects/V3ProjectsEndpoints.scala:58
val postProjectIriExports = self.base
  .secured(
    oneOf(
      notFoundVariant(V3ErrorCode.project_not_found),
      conflictVariant(V3ErrorCode.export_exists),
    ),
  )
  .post
  .in(exportsBase)
  …
```

The RestService method returns `IO[V3ErrorInfo, DataTaskStatusResponse]` and fails with a `NotFound(projectIri)` or a `Conflict(...)` that matches one of the declared variants. New v3 endpoints **must** use this pattern; do not throw `BadRequestException` from a v3 handler.

### Prefer typed channels — but don't churn existing code

Where the error set is small and obvious, narrow the channel (Style B / C). Where admin / v2 code already uses `Task[A]` + `BadRequestException`, keep it — we're not retrofitting.

## Invariant violations

When a branch represents state we believe is unreachable — a `None` after we've just checked `nonEmpty`, an unknown variant we've already exhaustively matched, an IRI we just successfully constructed — use the `die` family. **Always carry information about the invariant.**

### Use `dieMessage` and `orDieWith`, not bare `die` / `orDie`

`ZIO.dieMessage(String)` and `.orDieWith(e => Throwable)` exist precisely to attach an explanation. The message states the invariant in code — like Rust's `expect("…")` vs `unwrap()`. No comment needed.

```scala
// ✓ Carries the invariant
ZIO.dieMessage("guard above ensures the list is non-empty")

result.orDieWith(_ => new Exception("cannot happen, checked nonEmpty"))

// from RepositoryUpdater.scala:96
.orDieWith(_ => InconsistentRepositoryDataException(s"Invalid repository version: $kb"))
```

```scala
// ✗ Loses information
ZIO.die(new RuntimeException)

result.orDie
```

This applies everywhere — including at config / boot time. `ZIO.config(...).orDie` is no exception: prefer `.orDieWith(e => …)` and explain what makes the config invalid.

### Never `.die` on IRI conversion

A malformed client IRI is a 400, not a 500. Mapping rules are in [`dsp-api-iri-handling.md`](dsp-api-iri-handling.md).

## `throw` — narrow legacy carve-out

`throw` is permitted **only** inside non-effectful code that is wrapped by an upstream `ZIO.attempt`. Two real cases:

1. **Legacy non-effectful blocks** that pre-date the ZIO migration and would be invasive to convert end-to-end — e.g. the `OntologyCache` builder body wrapped at `slice/ontology/repo/service/OntologyCache.scala:506`:

    ```scala
    ZIO.attempt {
      // … legacy code that internally `throw`s InconsistentRepositoryDataException …
    }
    ```

2. **Thin wrappers around exception-throwing Java APIs** (Apache Jena, etc.), where the wrapper exists precisely to convert into the effect type:

    ```scala
    // slice/common/jena/RdfDataMgr.scala
    ZIO.attempt(RDFDataMgr.read(ds, is, lang)).as(ds)
    ```

Rules:

- Do **not** introduce *new* non-effectful chunks just to keep throwing.
- New effectful code uses `ZIO.fail` (recoverable) or `ZIO.dieMessage` (invariant).
- When you touch one of the legacy `throw` sites, replacing it with the effectful equivalent is a welcome incidental cleanup — but not required as a precondition for unrelated changes.

## Quick reference

| You want to…                                             | Use                                                 |
|----------------------------------------------------------|-----------------------------------------------------|
| Reject a malformed request                               | `ZIO.fail(BadRequestException(...))`                |
| Signal a missing resource                                | `ZIO.fail(NotFoundException(...))` or `.someOrFail` |
| Narrow the error channel to one or two cases             | `IO[E, A]` with `ZIO.fail`                          |
| Fail a new v3 endpoint                                   | `IO[V3ErrorInfo, A]` matching a declared variant    |
| Mark a branch as unreachable                             | `ZIO.dieMessage("invariant statement")`             |
| Discard a typed error you can't recover from             | `.orDieWith(e => SomeException(s"…$e…"))`           |
| Bridge a non-effectful Java API or legacy throwing block | `ZIO.attempt { … }`                                 |
