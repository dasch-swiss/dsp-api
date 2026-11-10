# dsp-api Code Conventions

Project-specific conventions for the dsp-api repository. These supplement the generic `scala-zio-patterns` skill — where they differ, these conventions take precedence.

## Stack

- Scala 3, ZIO 2, Tapir, zio-json, Bazel
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

The example shows the target shape. The real groups endpoints predate the policy factories and are still in the
endpoint baseline.

```scala
final class GroupsEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "admin" / "groups"

  val getGroups = baseEndpoints.publicPolicyEndpoint("group names and descriptions are public").get
    .in(base)
    .out(jsonBody[GroupsGetResponseADM].example(Examples.groupsResponse))
    .description("Return all groups. Does not require authentication.")

  val postGroup = baseEndpoints.projectAdminPolicyEndpoint("body project").post
    .in(base)
    .in(jsonBody[GroupCreateRequest].example(Examples.groupCreateRequest))
    .out(jsonBody[GroupGetResponseADM])
    .description(
      "Create a group. Requires SystemAdmin or ProjectAdmin permissions for the project given by the body project.",
    )
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
- `.serverLogic()` for secured endpoints (receives the security output: a `User`, or a `ProjectCandidate` for
  `ProjectAdmin`)

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

  def postGroup(candidate: ProjectCandidate)(request: GroupCreateRequest): Task[GroupGetResponseADM] =
    for {
      grant    <- auth.authorizeProjectAdminById(candidate, request.project)
      internal <- groupService.createGroup(request, grant.project).map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external
}
```

**Pattern:** Auth check → business logic → format response.

### Endpoint authorization policy

Every new endpoint declares its function-level authorization with a policy-declaring factory in `BaseEndpoints`
(ADR-0013, `docs/adr/0013-authorization-policy-enforcement.md`):

| Factory | Policy | Security output |
| --- | --- | --- |
| `publicPolicyEndpoint(reason)` | `Public`: no credentials read | none |
| `authenticatedPolicyEndpoint(reason)` | `Authenticated`: any active, non-anonymous user | `User` |
| `systemAdminPolicyEndpoint` | `SystemAdmin` | `User` |
| `projectAdminPolicyEndpoint(projectFrom)` | `ProjectAdmin` of the project named by `projectFrom` | `ProjectCandidate` |
| `projectMemberPolicyEndpoint(projectFrom)` | `ProjectMember` of the project named by `projectFrom` | `ProjectCandidate` |
| `selfOrSystemAdminPolicyEndpoint(userFrom)` | `SelfOrSystemAdmin`: the user named in the path, or a system admin | `User` and the target |
| `projectAdminOfAnyProjectPolicyEndpoint` | `ProjectAdminOfAnyProject`: a system admin or admin of at least one project | `User` |
| `objectAccessPolicyEndpoint(enforcedIn)` | `ObjectAccess`: per-object permissions, anonymous admitted | `User` |

- `withUserEndpoint` and `bearerSystemAdminEndpoint` declare no policy. Do not use them for new
  endpoints; `withUserEndpoint` remains only for the five count endpoints that wait on DEV-7718.
- A `Public` or `Authenticated` reason says why the endpoint needs no stronger rule; it is reviewed.
- A `ProjectAdmin` handler receives a `ProjectCandidate` and must obtain a `ProjectAdminGrant` from
  `AuthorizationRestService.authorizeProjectAdminById` before it acts. The domain write acts on `grant.project`.
  Once a domain write takes the grant itself, a handler that skips the check does not compile; until its write is
  migrated, it is only as safe as that call.
- The endpoint description contains the policy's `requirement` sentence (e.g. "Requires SystemAdmin permissions.").
- Never set `Policy.key` in a slice, and never call `withInfo` on an endpoint: it replaces all attributes,
  the policy included.
- `EndpointPolicyE2ESpec` (`modules/test-e2e`) fails for an endpoint without a policy that is not listed in
  `modules/test-e2e/src/test/resources/endpoints-without-policy.txt`. That baseline lists the endpoints that predate
  the factories and only shrinks: migrating an endpoint removes its line, and a new endpoint never adds one. The
  spec pins the baseline's size in `baselineSize`; the commit that removes lines lowers it.
- Every role-based endpoint (any policy except `Public`) has a row in `AuthorizationMatrixE2ESpec`, or an `exempt`
  entry with a reason; the spec fails for an endpoint with neither.

## Error Handling

See [`dsp-api-error-handling.md`](dsp-api-error-handling.md) for the full rules: when to use the ZIO error channel (recoverable failures) vs. the `die` family (invariant violations), the `RequestRejectedException` / `InternalServerException` hierarchy, typed `IO[E, A]` vs. `Task[A]`, the V3 typed-error pattern, and the narrow legacy carve-out for `throw`.

## Authentication

These bases declare no authorization policy; new endpoints use the factories in
[Endpoint authorization policy](#endpoint-authorization-policy).

- Bearer + Basic auth via Tapir security inputs
- The policy factories fail without credentials, except `publicPolicyEndpointWithUser` and the object-access ones
  that admit anonymous callers
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

### Golden snapshot tests

Extend a spec with `GoldenTest` and call `assertGolden(actual, "suffix")` to compare against a stored
snapshot. The golden file lands in the resources mirror of the spec's package:
`src/test/scala/.../FooSpec.scala` -> `src/test/resources/.../FooSpec__<suffix>.txt`.

There are three ways to regenerate a golden:

- `rewrite = true` on a single `assertGolden` call.
- `override val rewriteAll = true` on the spec.
- The `GOLDEN_REWRITE` environment variable, which needs no source edit:
  `bazel test <target> --test_filter='.*<Spec>.*' --test_env=GOLDEN_REWRITE=1`

`--test_filter` is compiled as a **Java regex**, so `.*<Spec>.*` is required; a leading bare `*` fails
with `PatternSyntaxException: Dangling meta character '*'`, which looks like a test failure rather than
a usage error.

A regeneration run **fails by design** and must be followed by a clean rerun without the env variable,
which must pass. Inspect the `git diff` of the golden files before accepting them.

**Scope caveat:** `GOLDEN_REWRITE` is only honoured by the testkit trait
(`modules/testkit/src/main/scala/org/knora/webapi/GoldenTest.scala`), which reaches `modules/test-it` and
`modules/test-e2e`. `modules/webapi` and `modules/sparql-builder` each carry their own same-named
`GoldenTest` without the switch, so there `rewrite = true` / `rewriteAll = true` is the only route.

**Placeholder rule for new golden cases:** Bazel runfiles entries are symlinks to the source files, so
rewriting an already-existing golden file lands in the source tree, but writing a file that does not
exist yet creates a plain file in the runfiles tree that is discarded after the run. So create an empty
placeholder file at the expected `src/test/resources/...` path *before* the regeneration run, and
afterwards use `git status` to confirm the source files actually changed.

Golden comparison is exact text, so the generated output must be deterministic — normalise UUIDs,
timestamps and other non-deterministic values before snapshotting.

**Shared golden suffixes.** Some tests deliberately point two inputs at the same golden file to assert they
must render identically — e.g. in `GravsearchToPrequeryTransformerE2ESpec`, the simple- and complex-schema
variants of a query share a suffix. Under a `GOLDEN_REWRITE` run every such test writes that file and the last
writer wins, so a genuine divergence between the two inputs only surfaces as one of the pair failing on the
next clean rerun. Treat that failure as a real bug to investigate — not a stale golden to re-rewrite — and do
not split the shared suffix just to make it pass.

**`<suffix>Shape` companion goldens.** Some prequery golden cases also assert a second golden rendered by
`GravsearchInferencePipelineTestSupport.shapeSummary`, which prints one line per top-level WHERE pattern. Add a
`<suffix>Shape` companion golden when the test's purpose is to pin the top-level pattern *order*, not merely the
rendered SPARQL text.

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

### SHACL shapes

Validation shapes live in `modules/webapi/src/main/resources/shacl/`. Their conventions —
file layering, `sh:description` on complex shapes, `sh:not` for single-case prohibitions,
`sh:message` placement, and testing — are in `docs/development/dsp-api-shacl-shapes.md`.

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

Do not build SPARQL with plain `s"..."` string concatenation. **New** queries use the
in-house `sparql"..."` interpolator in `modules/sparql-builder/` (package
`org.knora.sparqlbuilder`). Do not write new queries with RDF4J SparqlBuilder — it is
grandfathered for existing sites only, until they are migrated.
For more details see `dsp-api-sparql-queries.md`.

## Scala Idioms

### Option: prefer `fold` over `map` + `getOrElse`

Collapse an `Option` to a plain value with `fold`, not `map(...).getOrElse(...)` — one combinator instead of two, with the default sitting next to the transform:

```scala
// ✓ Correct
opt.fold(default)(f)
project.dataCopyrightHolder.fold("")(_.value)

// ✗ Incorrect — two combinators for a single collapse
opt.map(f).getOrElse(default)
project.dataCopyrightHolder.map(_.value).getOrElse("")
```

Note the argument order: `fold` takes the empty-case value first, then the mapping function.

## Formatting 

Run `just fmt` before pushing to the remote branch. This runs Scalafmt (via Bazel) to reformat all
Scala sources in-place and applies license headers. CI runs `just check` and will fail if formatting
is off. Import organization is handled by Scalafmt (see `.scalafmt.conf`); unused imports are caught
by the compiler (`-Wunused:all -Werror`).
