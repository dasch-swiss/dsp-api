# dsp-api Code Conventions

Project-specific conventions for the dsp-api repository. These supplement the generic `scala-zio-patterns` skill — where they differ, these conventions take precedence.

## Stack

- Scala 3, ZIO 2, Tapir, zio-json, sbt
- Package root: `org.knora.webapi.slice`

## Service Pattern

Services use `final case class` with private constructor params and `ZLayer.derive`:

```scala
// ✓ Correct
final case class GroupService(
  private val knoraGroupService: KnoraGroupService,
  private val projectService: ProjectService,
) {
  def findById(id: GroupIri): Task[Option[Group]] =
    knoraGroupService.findById(id).flatMap(ZIO.foreach(_)(toGroup))

  private def toGroups(knoraGroups: Chunk[KnoraGroup]): Task[Chunk[Group]] =
    ZIO.foreach(knoraGroups)(toGroup)
}

object GroupService {
  val layer = ZLayer.derive[GroupService]
}
```

```scala
// ✗ Incorrect — trait + separate Live class (not the dsp-api pattern)
trait GroupService {
  def findById(id: GroupIri): Task[Option[Group]]
}
class GroupServiceLive(...) extends GroupService { ... }
```

**Rules:**

- Use `final case class` for service implementations
- Constructor parameters are `private val`
- Public methods return ZIO effects (`Task[A]` or `IO[Error, A]`)
- Companion object: `val layer = ZLayer.derive[ServiceName]`
- No trait abstraction — concrete `final case class` directly

## Value Objects — StringValue Pattern

All value objects use private constructors with smart `from()` factories:

```scala
final case class GroupIri private (override val value: String) extends StringValue

object GroupIri extends StringValueCompanion[GroupIri] {
  given JsonCodec[GroupIri] = ZioJsonCodec.stringCodec(from)
  given Codec[String, GroupIri, CodecFormat.TextPlain] = TapirCodec.stringCodec(from)

  def from(value: String): Either[String, GroupIri] = value match {
    case _ if value.isEmpty         => Left("Group IRI cannot be empty.")
    case _ if isGroupIriValid(value) => Right(GroupIri(value))
    case v                           => Left(s"Group IRI is invalid: $v")
  }

  def makeNew(shortcode: Shortcode): GroupIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://rdfh.ch/groups/${shortcode.value}/$uuid")
  }
}
```

**Rules:**

- `final case class ClassName private (value: Type)` extending `StringValue`
- Companion extends `StringValueCompanion[T]` (or `WithFrom[I, T]`)
- `from(value): Either[String, T]` — smart constructor with validation
- `unsafeFrom()` for known-good values (throws `IllegalArgumentException`)
- `makeNew()` factory for generating new instances
- Use `given` (not `implicit`) for codec instances

## Validation

Use `ZValidation` for accumulating multiple errors:

```scala
trait StringValueCompanion[A <: StringValue] extends WithFrom[String, A]

// Validators available:
StringValueCompanion.nonEmpty
StringValueCompanion.noLineBreaks
StringValueCompanion.maxLength(n)
StringValueCompanion.isUri
StringValueCompanion.absoluteUri
```

## API Layer — Three-Tier Separation

### 1. Endpoints (definitions only)

```scala
final class GroupsEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "admin" / "groups"

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(jsonBody[GroupsGetResponseADM].example(Examples.groupsResponse))
    .description("Return all groups.")

  val postGroup = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(jsonBody[GroupCreateRequest].example(Examples.groupCreateRequest))
    .out(jsonBody[GroupGetResponseADM])
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
```

### 2. ServerEndpoints (wiring only)

```scala
final class GroupsServerEndpoints(
  endpoints: GroupsEndpoints,
  restService: GroupRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getGroups.zServerLogic(_ => restService.getGroups),
    endpoints.postGroup.serverLogic(restService.postGroup),
  )
}
```

- `.zServerLogic()` for public endpoints (ignore auth param with `_`)
- `.serverLogic()` for secured endpoints (receives `User` parameter)

### 3. RestService (translation)

```scala
final case class GroupRestService(
  private val auth: AuthorizationRestService,
  private val format: KnoraResponseRenderer,
  private val groupService: GroupService,
) {
  def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM] =
    for {
      internal <- groupService.findById(iri)
                    .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
                    .map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def postGroup(user: User)(request: GroupCreateRequest): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminById(user, request.project)
      internal <- groupService.createGroup(request).map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external
}
```

**Pattern:** Auth check → business logic → format response.

## Error Handling

See [`dsp-api-error-handling.md`](dsp-api-error-handling.md) for the full rules: when to use the ZIO error channel (recoverable failures) vs. the `die` family (invariant violations), the `RequestRejectedException` / `InternalServerException` hierarchy, typed `IO[E, A]` vs. `Task[A]`, the V3 typed-error pattern, and the narrow legacy carve-out for `throw`.

## Authentication

```scala
val securedEndpoint: ZPartialServerEndpoint[...] =
  endpointWithBearerBasicAuthOptional.zServerSecurityLogic {
    case (Some(jwtToken), _) => authenticateJwt(jwtToken)
    case (_, Some(basic))    => authenticateBasic(basic)
    case _                   => ZIO.fail(BadCredentialsException("No credentials provided."))
  }
```

- Bearer + Basic auth via Tapir security inputs
- `securedEndpoint` fails without credentials
- `withUserEndpoint` succeeds with `AnonymousUser`

## Concurrency — STM

```scala
final case class BulkIngestService(
  semaphoresPerProject: TMap[ProjectShortcode, TSemaphore],
) {
  private def acquireSemaphore(key: ProjectShortcode): ZSTM[Any, Nothing, TSemaphore] =
    for {
      semaphore <- semaphoresPerProject.getOrElseSTM(key, TSemaphore.make(1))
      _         <- semaphoresPerProject.put(key, semaphore)
      _         <- semaphore.acquire
    } yield semaphore
}

object BulkIngestService {
  val layer = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[BulkIngestService]
}
```

- `TMap`, `TSemaphore`, `TRef` with `.commit` to run STM in ZIO
- `.forkDaemon` for fire-and-forget fibers
- Layer init: `ZLayer.fromZIO(...) >>> ZLayer.derive[...]`

## Testing

```scala
object GroupIriSpec extends ZIOSpecDefault {
  override val spec: Spec[Any, Nothing] = suite("GroupIri should")(
    test("not be created from an empty value") {
      assertTrue(GroupIri.from("") == Left("Group IRI cannot be empty."))
    },
    test("allow prefixed builtin GroupIris") {
      check(Gen.fromIterable(builtIn)) { it =>
        assertTrue(GroupIri.from(it).map(_.value) == Right(expected))
      }
    },
  )
}
```

```scala
// With layer composition
object AuthServiceLiveSpec extends ZIOSpecDefault {
  val spec = suite("AuthServiceLive")(
    test("expired token should fail") {
      for {
        token  <- expiredToken(expiration)
        result <- AuthService.authenticate(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(JwtProblem(...))))
    },
  ).provide(jwtConfigLayer, AuthServiceLive.layer) @@ TestAspect.withLiveClock
}
```

**Rules:**

- **Every feature is tested** — unit tests for logic (parsers, query builders, services) and an integration/E2E round-trip for user-facing behaviour (write → read-back reflects the change). A feature merged without tests is under-tested and should be flagged in review.
- `object XSpec extends ZIOSpecDefault`
- `suite("description")(test("...") { ... })`
- Use `.provide(layers...)` for dependency injection
- Use `@@ TestAspect.withLiveClock` for time-dependent tests
- Use `.exit` to capture `Exit[E, A]` for error testing
- Use `check(Gen[T])` for property-based testing
- **Query builders: prefer golden snapshots** (`GoldenTest` + `assertGolden`) over scattered `q.contains(...)` substring assertions — see `dsp-api-sparql-queries.md` § Testing Query Builders

## Naming Conventions

| Element | Convention | Example |
| ------- | ---------- | ------- |
| Services | `*Service` | `GroupService` |
| Implementations | `*Live` suffix | `AuthServiceLive` |
| Rest services | `*RestService` | `GroupRestService` |
| Endpoints | `*Endpoints` | `GroupsEndpoints` |
| Handlers | `*ServerEndpoints` | `GroupsServerEndpoints` |
| Tests | `*Spec` | `GroupIriSpec` |
| Value objects | Domain name | `GroupIri`, `GroupName` |
| Errors | `*Exception` / `*Error` | `NotFoundException` |

## Ontology Conventions

### Naming overridable project-wide defaults

A property that holds a **project-wide default that can be overridden per resource** is named
`hasDefault*` in the ontology, with `default*` as the JSON payload key — following the
`default_permissions` precedent (e.g. `knora-admin:hasDefaultDataAuthorship` / `defaultDataAuthorship`).
A property that applies directly (not overridable) does not take the prefix (e.g. `hasDataLicense`).

### RDF name vs wire key

The RDF property name and the JSON wire key may diverge when compatibility requires it.
Any divergence must be deliberate and stated at the serialization boundary, not accidental.

### Changing the built-in ontologies

Changes to `knora-base.ttl` / `knora-admin.ttl` follow the version-bump and upgrade-plugin
rules in `docs/05-internals/development/updating-repositories.md` (§ Changing the Built-in
Ontologies) — including when a bump is *not* needed and how test fixtures are regenerated.

## Import Organization

```scala
package org.knora.webapi.slice.admin.domain.service

import zio.*                                                  // 1. ZIO / stdlib
import sttp.tapir.*                                           // 2. Third-party

import org.knora.webapi.slice.admin.domain.model.Group        // 3. Internal (alphabetical)
import org.knora.webapi.slice.admin.domain.model.GroupIri
```

Order: stdlib/ZIO → third-party → internal `org.knora` (alphabetical). Single blank line between groups.

## Writing SPARQL queries

When writing SPARQL queries do not use String concatenation.
Instead use rdf4j and the query helper in dsp-api.
For more details see `dsp-api-sparql-queries.md`.

## Formatting 

Run `sbt fmt` before pushing to the remote branch. Scalafmt reformats all Scala sources in-place.
CI runs `sbt check` and will fail if formatting is off.
