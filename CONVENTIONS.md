# Conventions

Agent reference card for the **work phase**. Pair with `REVIEW.md` (review phase). Authoritative detail lives in `docs/development/`.

## Stack

Scala 3, ZIO 2, Tapir, zio-json, sbt. Package root: `org.knora.webapi`. Triplestore: Apache Jena Fuseki. Media server: Sipi.

## Code Conventions

### Services

- `final class ServiceName(val …)` with `val layer = ZLayer.derive[ServiceName]` in the companion. No trait + `*Live` split for new domain services.
- **Carve-out**: trait + `*Live` is allowed for repos and other seams that need in-memory test doubles (`KnoraGroupRepo` / `KnoraGroupRepoLive`, `OntologyRepo`, `JwtService`, `Authenticator`). New repo traits ship an in-memory companion at `webapi/src/test/.../service/<Name>InMemory.scala`.

### API layer — three-tier split (mandatory)

- `*Endpoints.scala` — Tapir endpoint definitions only.
- `*ServerEndpoints.scala` — wiring of endpoint to RestService method. `.zServerLogic(_ => …)` for public, `.serverLogic(restService.x)` for secured.
- `*RestService.scala` — handler logic. Every secured method has the curried shape `def x(user: User)(args…): Task[Resp]`. The body follows: **auth check → business logic → `format.toExternal(...)`**.
- Register new endpoints in `CompleteApiServerEndpoints`.
- Request / response DTOs live in a sibling object inside the `*Endpoints.scala` file (or a `*RequestsAndResponses.scala` next to it), not in a separate `model/` package.

### Value objects

- `final case class X private (value: I)` extending the appropriate `*Value` base, with `object X extends  WithFrom[I, X]` or a specialized companion e.g. StringValueCompanion[X]` in case `I` is `String`.
- Smart constructor: `def from(value): Either[String, X]`. `unsafeFrom` is for known-good inputs only, checks invariants and throws if invalid. Codecs declared with `given`. Full pattern in `docs/development/dsp-api-value-types.md`.
-  Factory functions should be named `makeNew`

### IRI handling

- At the API boundary: `ZIO.fromEither(X.from(iri)).mapError(BadRequestException.apply)`.
- **No `unsafeFrom` in RestServices or responders**, and **never `.die` on `X.from(...)` failures** — a malformed client IRI is a 400, not a 500.
- Layer-specific failure mapping (`BadRequestException` in RestServices, domain errors in services, `InconsistentRepositoryDataException` in repos): see `docs/development/dsp-api-iri-handling.md`.
- V3 endpoints accept SmartIri-backed IRIs as `IriDto` and convert via `IriConverter` in the RestService: see `docs/development/dsp-api-v3-iri-handling.md`.

### Error handling

- Recoverable failures travel in the ZIO error channel (`ZIO.fail`, `.someOrFail`); invariant violations use the `die` family with a message (`ZIO.dieMessage`, `.orDieWith`); `throw` only inside non-effectful code wrapped by an upstream `ZIO.attempt`.
- Full rules — fail vs die, typed `IO[E, A]` vs `Task[A]`, V3 typed errors, the legacy `throw` carve-out — in `docs/development/dsp-api-error-handling.md`.

### SPARQL

Never concatenate query strings. Use rdf4j SparqlBuilder via the helpers in `slice/common/repo`. See `docs/development/dsp-api-sparql-queries.md`.

### Imports & formatting

- No fully-qualified class names in code bodies — import at the top of the file.
- Order: stdlib / ZIO → third-party → internal `org.knora.*` (alphabetical within each group), single blank line between groups.
- Scalafmt via `sbt fmt`; CI runs `sbt check`.
- Every source file carries the Apache-2.0 + SPDX header (managed by `sbt headerCreateAll` / `headerCheckAll`).

### Naming in human-readable text

The legacy name "Knora" lives on in packages (`org.knora.webapi`) and class names — leave them. Do **not** use "Knora" in commit messages, PR titles, docs, comments, spec files, or learning documents. Use "dsp-api".

## Testing Conventions

- `object XSpec extends ZIOSpecDefault`; `suite("X should")(test("...") { … })`; layers via `.provide(...)`. Use `TestAspect.withLiveClock` for time-dependent tests.
- New repo traits ship an in-memory companion under `webapi/src/test/.../service/<Name>InMemory.scala`.
- Test data: prefer self-contained fixtures next to the component. Only fall back to shared `test_data/` sets when unavoidable. When adding to a shared set, verify an actual *instance* of the scenario exists — not just that the schema supports it.
- Test locations: unit → `webapi/src/test/scala/`; integration → `modules/test-it/`; ingest integration → `modules/test-ingest-integration/`; E2E → `modules/test-e2e/`; shared utilities → `modules/testkit/`.

## Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org/). The mapping is enforced by `release-please` (see `.github/workflows/create-release.yml`):

| Prefix       | Changelog section          |
|--------------|----------------------------|
| `feat:`      | Enhancements               |
| `fix:`       | Bug Fixes                  |
| `perf:`      | Performance Improvements   |
| `docs:`      | Documentation              |
| `test:`      | Tests                      |
| `deprecated:`| Deprecated                 |
| `refactor:`  | Maintenances               |
| `build:`     | Maintenances               |
| `chore:`     | Maintenances               |

Breaking changes use `!` (e.g. `feat!:`). Do not use "Knora" in commit messages; use "dsp-api".

## PR Template

See `.github/pull_request_template.md`.

## Where to go for depth

- `docs/development/dsp-api-conventions.md` — full code conventions (services, value objects, three-tier API, naming, imports, STM, ZIO Test)
- `docs/development/dsp-api-error-handling.md` — fail vs die, typed errors, the `throw` carve-out
- `docs/development/dsp-api-iri-handling.md` — universal IRI handling rules
- `docs/development/dsp-api-v3-iri-handling.md` — V3 IRI conventions
- `docs/development/dsp-api-value-types.md` — `StringValue` / `WithFrom` pattern
- `docs/development/dsp-api-sparql-queries.md` — SPARQL with rdf4j SparqlBuilder
- `docs/05-internals/design/adr/` — architectural decisions
