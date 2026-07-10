# Conventions

Agent reference card for the **work phase**. Pair with `REVIEW.md` (review phase). Authoritative detail lives in `docs/development/`.

## Stack

Scala 3, ZIO 2, Tapir, zio-json, sbt. Package root: `org.knora.webapi`. Triplestore: Apache Jena Fuseki. Media server: Sipi.

## Code Conventions

### Services

- `final class ServiceName(val …)` with `val layer = ZLayer.derive[ServiceName]` in the companion. No trait + `*Live` split for new domain services.
- **Carve-out**: trait + `*Live` is allowed for repos and other seams that need in-memory test doubles (`KnoraGroupRepo` / `KnoraGroupRepoLive`, `OntologyRepo`, `JwtService`, `Authenticator`). New repo traits ship an in-memory companion at `modules/webapi/src/test/.../service/<Name>InMemory.scala`.

### API layer — three-tier split (mandatory)

- `*Endpoints.scala` — Tapir endpoint definitions only.
- `*ServerEndpoints.scala` — wiring of endpoint to RestService method. `.zServerLogic(_ => …)` for public, `.serverLogic(restService.x)` for secured.
- `*RestService.scala` — handler logic: auth and presentation only, with a minimal amount of business logic. The actual business logic lives in plain `*Service`s. Every secured method uses multiple parameter lists `def x(user: User)(args…): Task[Resp]`, which lets the `*ServerEndpoints` wire it concisely without enumerating every param. The body follows: **auth check → delegate to service → `format.toExternal(...)`**.
- Register new endpoints in `CompleteApiServerEndpoints`.
- Request / response DTOs live in a sibling object inside the `*Endpoints.scala` file (or a `*RequestsAndResponses.scala` next to it), not in a separate `model/` package.

### Value objects

- `final case class X private (value: I)` extending the appropriate `*Value` base, with `object X extends  WithFrom[I, X]` or a specialized companion e.g. StringValueCompanion[X]` in case `I` is `String`.
- Smart constructor: `def from(value): Either[String, X]`. `unsafeFrom` is for known-good inputs only, checks invariants and throws if invalid. Codecs declared with `given`. Full pattern in `docs/development/dsp-api-value-types.md`.
- Factory functions should be named `makeNew`

### IRI handling

- At the API boundary (admin / v2): `ZIO.fromEither(X.from(iri)).mapError(BadRequestException.apply)`. In v3, map the failure to the endpoint's typed `V3ErrorInfo` variant instead of `BadRequestException`.
- **No `unsafeFrom` in RestServices or responders**, and **never `.die` on `X.from(...)` failures** — a malformed client IRI is a 400, not a 500.
- Layer-specific failure mapping (`BadRequestException` in RestServices, domain errors in services, `InconsistentRepositoryDataException` in repos): see `docs/development/dsp-api-iri-handling.md`.
- V3 endpoints: only SmartIri-backed IRIs (ontology, resource-class, property/value IRIs, which have schema-dependent representations) are accepted as `IriDto` and converted via `IriConverter` in the RestService. Simple IRIs (`ProjectIri`, `UserIri`, `ResourceIri`, `ValueIri`, …) use the typed value object directly in the endpoint definition: see `docs/development/dsp-api-v3-iri-handling.md`.

### Error handling

- Recoverable failures travel in the ZIO error channel (`ZIO.fail`, `.someOrFail`); invariant violations use the `die` family with a message (`ZIO.dieMessage`, `.orDieWith`); `throw` only inside non-effectful code wrapped by an upstream `ZIO.attempt`.
- Full rules — fail vs die, typed `IO[E, A]` vs `Task[A]`, V3 typed errors, the legacy `throw` carve-out — in `docs/development/dsp-api-error-handling.md`.

### SPARQL

Never concatenate query strings. Use rdf4j SparqlBuilder via the helpers in `slice/common/repo`. See `docs/development/dsp-api-sparql-queries.md`.

### Ontology & RDF

- Overridable project-wide defaults: `hasDefault*` in the ontology, `default*` as payload key (precedent `default_permissions`, `hasDefaultDataAuthorship`). Details in `docs/development/dsp-api-conventions.md` § Ontology Conventions.
- Stored values that fail validation on read: skip + `ZIO.logWarning` — never 500 the whole read, never drop silently. See `docs/development/dsp-api-error-handling.md` § Reading persisted data leniently.
- Changing `knora-base.ttl` / `knora-admin.ttl`: version-bump rules (incl. when *not* to bump, and `MigrateOnlyBuiltInGraphs` for reload-only changes) in `docs/05-internals/development/updating-repositories.md` § Changing the Built-in Ontologies. Generated fixtures (e.g. `knoraApiOntologyWithValueObjects.jsonld`) are rewritten by `OntologyFormatsE2ESpec` — never hand-edited.
- Single-graph updates use `` .`with`(graph) `` so the WHERE is graph-scoped too — an ungraphed WHERE silently no-ops on stores without a union default graph. Invariant + `USING`/`GRAPH` caveat in `docs/development/dsp-api-sparql-queries.md`.

### Observability

When adding or changing tracing/telemetry (spans, span attributes, metrics), read `docs/observability/` first — don't re-derive the pattern. Follow `docs/observability/instrumentation-recipe.md`: one root `INTERNAL` span per vertical plus bounded-name stage spans, a **bounded** query shape on the root (never raw query text, instance IRIs, or user IDs as attributes), and the load-bearing `UNSET` failure status-mapper that keeps `cause.prettyPrint` (user data) out of the span status. `docs/observability/using-grafana.md` covers reading/querying traces in Grafana and from Claude Code via the Grafana MCP server.

### Scala idioms

- Collapse an `Option` with `opt.fold(default)(f)`, not `opt.map(f).getOrElse(default)`. Details + argument-order note in `docs/development/dsp-api-conventions.md` § Scala Idioms.

### Imports & formatting

- No fully-qualified class names in code bodies — import at the top of the file.
- Import order is handled automatically by `sbt fmt`; don't hand-order imports.
- Scalafmt via `sbt fmt`; CI runs `sbt check`.
- Every source file carries the Apache-2.0 + SPDX header (managed by `sbt headerCreateAll` / `headerCheckAll`).

### Static analysis (Codacy)

CI runs Codacy and reports new issues (pre-existing ones are grandfathered). It is **advisory** — not a required check — but keep new/changed **production** source (`src/main`) clear of its common triggers. Test sources are excluded (`.codacy.yml`), so test-only idioms never trip it.

- **ASCII only** in source — no smart punctuation (`…`, `→`, `≥`, curly quotes); write `...`, `->`, `>=`. (The `©` in the licence header is exempt.)
- **Prefer immutable `val`s and recursion / collection combinators** over `var` and `while`. This isn't an absolute ban — a mutable loop is fine where it is genuinely the clearest option (e.g. a tight byte-copy loop) — but Codacy flags every *new* occurrence, so reach for `@tailrec` / folds first and only keep a `var`/`while` when it really reads better.
- **Methods ≤ 50 lines** in production source — split long ones. (This rule is intentionally **not** applied to tests: long ZIO `spec` methods are fine, which is why `src/test/**` is excluded from Codacy.)

### Naming in human-readable text

The legacy name "Knora" lives on in packages (`org.knora.webapi`) and class names — leave them. The same applies to the `knora-base` / `knora-admin` ontologies, which were never renamed and are in active use: identifiers that refer to them (e.g. a `toKnoraBaseOntology` method) keep the name — renaming them would be misleading.

Do **not** use "Knora" in free-form human-readable text — commit messages, PR titles, docs prose, comments, spec files, or learning documents. Use "dsp-api". (Code identifiers that name a real Knora package, class, or ontology are exempt, per above.)

## Testing Conventions

- **Every feature is tested.** Logic (parsers, query builders, services) gets unit tests; user-facing behaviour gets an integration/E2E round-trip (write → read-back reflects the change). "It compiles" is not coverage — a feature merged without tests should be flagged in review (see `REVIEW.md`).
- `object XSpec extends ZIOSpecDefault`; `suite("X should")(test("...") { … })`; layers via `.provide(...)`. Use `TestAspect.withLiveClock` for time-dependent tests.
- New repo traits ship an in-memory companion under `modules/webapi/src/test/.../service/<Name>InMemory.scala`.
- Test data: prefer self-contained fixtures next to the component. Only fall back to shared `test_data/` sets when unavoidable. When adding to a shared set, verify an actual *instance* of the scenario exists — not just that the schema supports it.
- Repeated datatype triples are **unordered** — never assert their insertion order in tests (use sorted lists or sets; entity reads sort small literal lists at the read boundary, see `keywords` / `defaultDataAuthorship` in `KnoraProjectRepoLive`). If result order matters in production, it is `ORDER BY` in the query, not post-hoc sorting. See `docs/development/dsp-api-sparql-queries.md` § Repeated Properties Have No Order.
- Test locations: unit → `modules/webapi/src/test/scala/`; integration → `modules/test-it/`; ingest integration → `modules/test-ingest-integration/`; E2E → `modules/test-e2e/`; shared utilities → `modules/testkit/`.
- For large or generated string output (SPARQL, serialized responses), mix in `GoldenTest` and assert with `assertGolden(actual, "suffix")`. The expected value is stored next to the spec under `src/test/resources/`; regenerate with `rewrite = true` (or `rewriteAll = true`) and inspect the `git diff`. See `modules/webapi/src/test/scala/org/knora/webapi/GoldenTest.scala`. This is the **preferred** way to test query builders — not scattered `q.contains(...)` substring assertions (see `docs/development/dsp-api-sparql-queries.md` § Testing Query Builders).

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

An optional scope in parentheses marks the affected release artifact, e.g. `feat(dsp-api): …`, `fix(sipi): …`.

For how to group commits by user-visible impact and what belongs in a commit vs. a PR description vs. a learnings doc, see `docs/development/dsp-api-commit-conventions.md`.

## PR Template

See `.github/pull_request_template.md`. For the recommended section structure (Motivation, Challenges and Decisions, Gotchas, etc.) and why it matters for `/eng:workflows:compound`, see `docs/development/dsp-api-commit-conventions.md` § PR Description Format.

## Where to go for depth

- `docs/development/dsp-api-conventions.md` — full code conventions (services, value objects, three-tier API, naming, imports, STM, ZIO Test)
- `docs/development/dsp-api-error-handling.md` — fail vs die, typed errors, the `throw` carve-out
- `docs/development/dsp-api-iri-handling.md` — universal IRI handling rules
- `docs/development/dsp-api-v3-iri-handling.md` — V3 IRI conventions
- `docs/development/dsp-api-value-types.md` — `StringValue` / `WithFrom` pattern
- `docs/development/dsp-api-sparql-queries.md` — SPARQL with rdf4j SparqlBuilder
- `docs/05-internals/design/adr/` — architectural decisions
