# Code Review Checklist

Agent reference card for the **review phase**. Pair with `CONVENTIONS.md` (work phase). Authoritative detail lives in `docs/development/`.

## Always Check

### Build & history

- [ ] `sbt check` passes (formatting + linting)
- [ ] `sbt test` passes; integration / E2E tests cover changed behaviour
- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/) — see `CONVENTIONS.md` for the prefix → changelog mapping enforced by `release-please`
- [ ] "Knora" not used in commit messages, PR titles, docs, comments, or human-readable text — keep it to legacy packages / class names only
- [ ] Every new source file has the Apache-2.0 + SPDX header (`sbt headerCheckAll`)

### Services & API layering

- [ ] New services are `final case class … private (…)` with `ZLayer.derive` in the companion — **no** new trait + `*Live` split for plain domain services
- [ ] Trait + `*Live` is acceptable only for repos and explicit test seams; any such new repo ships an in-memory companion at `webapi/src/test/.../service/<Name>InMemory.scala`
- [ ] Endpoints split across the three tiers: `*Endpoints` (definition) → `*ServerEndpoints` (wiring) → `*RestService` (logic) — and registered in `CompleteApiServerEndpoints`
- [ ] Secured RestService methods are curried `def x(user: User)(args…): Task[Resp]` (Tapir wiring depends on this)
- [ ] Secured RestService bodies follow **auth check → business logic → `format.toExternal(...)`** — `toExternal` is not skipped
- [ ] `AuthorizationRestService.ensure*` results are re-used (don't reload the project / group after the auth call returned it)
- [ ] Request / response DTOs live next to the `*Endpoints` (sibling object or `*RequestsAndResponses.scala`), not in a separate `model/` package

### IRI handling

- [ ] IRI conversion happens at the API boundary: `ZIO.fromEither(X.from(iri)).mapError(BadRequestException.apply)`
- [ ] **No `unsafeFrom` in RestServices or responders** for client-provided values
- [ ] No `.die` on `X.from(iri)` failures — malformed client IRIs map to `BadRequestException` (400, not 500)
- [ ] V3 endpoints accept SmartIri-backed IRIs as `IriDto` and convert via `IriConverter` in the RestService

### Error handling

- [ ] Recoverable errors travel in the ZIO error channel (`ZIO.fail`, `.someOrFail`, `.orElseFail`) — not `throw`
- [ ] `ZIO.die` / `.orDie` carries information: prefer `ZIO.dieMessage("invariant statement")` and `.orDieWith(e => …)` over the bare forms. The message documents the invariant — applies at config / boot time too
- [ ] New v3 endpoints use typed `IO[V3ErrorInfo, A]` with error variants declared on the endpoint
- [ ] Admin / v2 code uses the existing `RequestRejectedException` / `InternalServerException` hierarchy (`BadRequestException`, `NotFoundException`, …) — not bespoke error types
- [ ] New `throw` only appears inside non-effectful code wrapped by an upstream `ZIO.attempt`; new code does not introduce non-effectful chunks just to keep throwing

### SPARQL

- [ ] No string concatenation in SPARQL — use rdf4j SparqlBuilder via the helpers in `slice/common/repo` (see `docs/development/dsp-api-sparql-queries.md`)

### Tests

- [ ] New tests: `object XSpec extends ZIOSpecDefault`; layers via `.provide(...)`; `TestAspect.withLiveClock` for time-dependent tests
- [ ] Test data added in the right place — self-contained fixtures next to the component preferred over additions to shared `test_data/` sets
- [ ] When adding to a shared dataset, an actual *instance* of the scenario is added (not just relying on schema support)

### Docs

- [ ] Documentation updated where behaviour changed (`docs/`, plus `CLAUDE.md` / `CONVENTIONS.md` / `REVIEW.md` if a convention changed)

## Style

- [ ] No fully-qualified class names in code bodies — import at the top of the file
- [ ] Imports grouped: stdlib / ZIO → third-party → internal `org.knora.*` (alphabetical within group), single blank line between groups
- [ ] `final case class` preferred over `final class` / plain `case class` for new services and value objects
- [ ] Naming follows `CONVENTIONS.md`: `*Service`, `*RestService`, `*Endpoints`, `*ServerEndpoints`, `*Spec`, `*Exception`

## Skip

- Scalafmt-only diffs (enforced by `sbt check`)
- `CHANGELOG.md` and version-bump commits (managed by `release-please`)
- Generated / vendored sources
- Legacy `responders/` and pre-slice code paths, unless the PR is explicitly migrating them
