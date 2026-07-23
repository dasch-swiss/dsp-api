# Code Review Checklist

Agent reference card for the **review phase**. Pair with `CONVENTIONS.md` (work phase). Authoritative detail lives in `docs/development/`.

## Always Check

### Build & history

- [ ] `sbt check` passes (formatting + linting)
- [ ] `sbt test` passes; integration / E2E tests cover changed behaviour
- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/) — see `CONVENTIONS.md` for the prefix → changelog mapping enforced by `release-please`
- [ ] "Knora" not used in free-form human-readable text (commit messages, PR titles, docs prose, comments) — exempt: legacy packages, class names, and identifiers naming the `knora-base` / `knora-admin` ontologies
- [ ] Every new source file has the Apache-2.0 + SPDX header (`sbt headerCheckAll`)

### Services & API layering

- [ ] New services are `final class (…)` with `ZLayer.derive` in the companion — **no** new trait + `*Live` split for plain domain services
- [ ] Trait + `*Live` is acceptable only for repos and explicit test seams; any such new repo ships an in-memory companion at `modules/webapi/src/test/.../service/<Name>InMemory.scala`
- [ ] Endpoints split across the three tiers: `*Endpoints` (definition) → `*ServerEndpoints` (wiring) → `*RestService` (logic) — and registered in `CompleteApiServerEndpoints`
- [ ] Secured RestService methods use multiple parameter lists `def x(user: User)(args…): Task[Resp]` — this lets the `ServerEndpoint` wire the method concisely without enumerating every param, so adding a param touches only the endpoint definition and the method
- [ ] Secured RestService bodies follow **auth check → delegate to service → `format.toExternal(...)`** — RestServices hold only auth / presentation logic; business logic lives in plain `*Service`s; `toExternal` is not skipped
- [ ] `AuthorizationRestService.ensure*` results are re-used (don't reload the project / group after the auth call returned it)
- [ ] Request / response DTOs live next to the `*Endpoints` (sibling object or `*RequestsAndResponses.scala`), not in a separate `model/` package

### IRI handling

- [ ] IRI conversion happens at the API boundary: `ZIO.fromEither(X.from(iri)).mapError(BadRequestException.apply)` (admin / v2; v3 maps to the typed `V3ErrorInfo` variant)
- [ ] **No `unsafeFrom` in RestServices or responders** for client-provided values
- [ ] No `.die` on `X.from(iri)` failures — malformed client IRIs are a 400, not a 500
- [ ] Only ontology, resource-class and property/value IRIs (which have schema-dependent representations) are taken as `IriDto` and converted via `IriConverter` in the RestService; simple IRIs like `ProjectIri`, `ResourceIri`, `ValueIri` are used directly in the endpoint definition

### Error handling

- [ ] Recoverable errors travel in the ZIO error channel (`ZIO.fail`, `.someOrFail`, `.orElseFail`) — not `throw`
- [ ] `ZIO.die` / `.orDie` carries information: prefer `ZIO.dieMessage("invariant statement")` and `.orDieWith(e => …)` over the bare forms. The message documents the invariant — applies at config / boot time too
- [ ] New v3 endpoints use typed `IO[V3ErrorInfo, A]` with error variants declared on the endpoint
- [ ] Admin / v2 code uses the existing `RequestRejectedException` / `InternalServerException` hierarchy (`BadRequestException`, `NotFoundException`, …) — not bespoke error types
- [ ] New `throw` only appears inside non-effectful code wrapped by an upstream `ZIO.attempt`; new code does not introduce non-effectful chunks just to keep throwing

### SPARQL

- [ ] No string concatenation in SPARQL — use rdf4j SparqlBuilder via the helpers in `slice/common/repo` (see `docs/development/dsp-api-sparql-queries.md`)
- [ ] Query builders are tested with **golden snapshots** (`GoldenTest`), not scattered `q.contains(...)` / `!q.contains(...)` substring assertions (brittle on serialization, blind to clause placement) — see `docs/development/dsp-api-sparql-queries.md` § Testing Query Builders
- [ ] Selective patterns precede `OPTIONAL` blocks **within the same flat group** — no restriction appended after OPTIONALs, no accidental nesting via `pattern.and(group)` (emits `{ pattern . { … } }`) — see `docs/development/dsp-api-sparql-queries.md` § Pattern Order and Query Performance (incident DEV-6796)
- [ ] Property paths (`zeroOrMore()`, `*`/`+`) are **anchored** — a path variable is bound by preceding patterns; seemingly redundant patterns adjacent to a path are treated as load-bearing anchors (DEV-6803: removing one took a 19ms tile query to ~15s); no large closures inlined as `VALUES`
- [ ] Per-row guards (e.g. `isDeleted`) use `FILTER NOT EXISTS`, not `MINUS`; scans (class extents, unbound predicates, aggregation inputs) are `GRAPH`-scoped where the graph is known — bound-term lookups need no `GRAPH` clause (DEV-6803)
- [ ] No redundant per-row work: `GRAPH`-scoped patterns don't also carry an `attachedToProject` join; no `DISTINCT` that provably can't dedup (always dead over `GROUP BY`); per-subject min/max via `GROUP BY` + aggregate, not nested `NOT EXISTS` anti-joins (DEV-6827)
- [ ] Changes that alter a builder's emitted SPARQL — including additions to `AbstractEntityRepo.entityProperties` — are reviewed as query changes and show up in a pinned/golden query spec diff; a hot-path builder without such a spec gets one in the same PR

### Ontology & RDF

- [ ] New overridable project-wide defaults are named `hasDefault*` (payload key `default*`) — see `docs/development/dsp-api-conventions.md` § Ontology Conventions
- [ ] Stored values that fail validation on read are skipped **with a logged warning** — not a 500, not a silent drop
- [ ] Built-in ontology changes follow the version-bump rules (`docs/05-internals/development/updating-repositories.md` § Changing the Built-in Ontologies); no duplicate bump for a change a stacked sibling PR already bumps for; generated ontology fixtures regenerated via `OntologyFormatsE2ESpec`, not hand-edited

### Observability

- [ ] Tracing/telemetry changes follow `docs/observability/instrumentation-recipe.md`: bounded span names, a bounded query shape on the root, **no raw query text / instance IRIs / user IDs in attributes**, failure status-mapper maps to `UNSET` (keeps `cause.prettyPrint` out of span status), interruptions set `exit_reason`

### Tests

- [ ] **Every new feature/endpoint is actually tested — flag it when it is not.** Logic (parsers, query builders, services) has unit tests, and user-facing behaviour has an integration/E2E round-trip (e.g. write → read-back reflects the change). A feature whose only "test" is that it compiles is under-tested; say so in the review.
- [ ] New tests: `object XSpec extends ZIOSpecDefault`; layers via `.provide(...)`; `TestAspect.withLiveClock` for time-dependent tests
- [ ] Test data added in the right place — self-contained fixtures next to the component preferred over additions to shared `test_data/` sets
- [ ] When adding to a shared dataset, an actual *instance* of the scenario is added (not just relying on schema support)
- [ ] Tests do not assert insertion order of repeated RDF literals (sorted lists or sets); if result order matters in production, it is `ORDER BY` in the query, not post-hoc sorting

### Docs

- [ ] Documentation updated where behaviour changed (`docs/`, plus `CLAUDE.md` / `CONVENTIONS.md` / `REVIEW.md` if a convention changed)

## Style

- [ ] `Option` collapsed with `opt.fold(default)(f)`, not `opt.map(f).getOrElse(default)` — see `CONVENTIONS.md` § Scala idioms
- [ ] No fully-qualified class names in code bodies — import at the top of the file
- [ ] `final class` preferred over plain `class` / `case class` for new services (non-public members surface as unused); `final case class private` for value objects
- [ ] Naming follows `CONVENTIONS.md`: `*Service`, `*RestService`, `*Endpoints`, `*ServerEndpoints`, `*Spec`, `*Exception`

## Skip

- Scalafmt-only diffs (enforced by `sbt check`)
- `CHANGELOG.md` and version-bump commits (managed by `release-please`)
- Generated / vendored sources
- Legacy `responders/` and pre-slice code paths, unless the PR is explicitly migrating them
