# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DSP-API is the Digital Humanities Service Platform API - a Scala-based REST API for managing semantic data and digital assets in the humanities. 
The project uses ZIO for functional programming, zio-http as HTTP server and tapir for defining endpoints of the API, and integrates with Apache Jena Fuseki triplestore and Sipi media server.

## Naming Conventions

The legacy project name **"Knora"** still appears in the codebase (package `org.knora.webapi`, class names like `KnoraGroupService`). This is acceptable in code — do not rename packages or classes without an explicit refactoring task.

However, **do not use "Knora" in human-readable text**: PR titles, commit messages, documentation, comments, spec files, or learning documents. Use "dsp-api" instead.

## Build System & Commands

### Core Build Tool

- **Primary**: `just` (command runner) - the canonical entry point for build, test, image, CI, and
  local-dev tasks (stack lifecycle, DB init, cleanup); it wraps Bazel. Run `just --list`.
- **Build of record**: `bazel` (via the Nix dev shell). sbt has been removed; everything (compile,
  test, images, formatting, linting) runs through Bazel. For the fast edit → diagnostics loop, prefer
  the `metals` MCP tools (see below) over shelling out to `bazel build`.

### Essential Development Commands

**Testing:**

- Run a single test target: `bazel test //modules/webapi:test` (filter with `--test_filter=*TestClassName*`)
- Run one module's tests: `bazel test //modules/webapi:test`
- Integration tests use `latest` Sipi image by default. To use exact git version locally, set `SIPI_USE_EXACT_VERSION=true` or build with `just docker-build-sipi-image`.
- `just test-unit` - Run all pure-JVM unit tests (webapi, ingest, bagit, jwt, shacl-validator)
- `just test-it` - Run integration tests (requires Docker)
- `just test-e2e` - Run end-to-end HTTP API tests (requires Docker)

**Code Quality:**

- `just fmt` - Format Scala (scalafmt via Bazel) and apply Apache-2.0 SPDX headers
- `just check` - Check formatting + license headers (the CI gate; no writes)
- Import organization is handled by scalafmt (`.scalafmt.conf`); unused imports are caught by the
  compiler (`-Wunused:all -Werror`). scalafix was removed with sbt.

**Building:**

- `bazel build //modules/webapi:webapi` - Compile a module (or use the metals `compile-module` tool)
- `just docker-build` - Build the dsp-api/sipi/ingest Docker images (Fuseki excluded)
- `just docker-build-dsp-api-image` - Build only the API Docker image

**Local Development Stack:**

- `just stack-start` - Start the full stack (Fuseki, Sipi, API)
- `just stack-start-dev` - Start stack without API (for development)
- `just stack-stop` - Stop the stack
- `just stack-init-test` - Initialize with test data

### Bazel & the Nix dev shell

All four container images (`knora-sipi`, `knora-api`, `dsp-ingest`, `apache-jena-fuseki`) build with
**Bazel** (`rules_oci`).
Bazel is provided through a **Nix dev shell** (`flake.nix`) that puts `bazel` (a bazelisk wrapper;
the version is pinned in `.bazelversion`), a JDK 25, `just`, and `crane` on `PATH`.

- **Enter the shell:** with `direnv` it loads automatically on `cd` into the repo (`.envrc` runs `use flake`; run `direnv allow` once). Without direnv, prefix commands with `nix develop --command`, e.g. `nix develop --command just docker-build-sipi-image`.
- `just docker-build-sipi-image` / `just docker-build-dsp-api-image` / `just docker-build-ingest-image`
  build the image and load it into the local Docker daemon (`:latest` plus the git-describe
  version tag); each runs `bazel run //modules/<sipi|webapi|ingest>:load`. `just docker-publish-*`
  variants build + push the multi-arch image via `bazel run //modules/<m>:push`.
- `bazel build //modules/webapi:image_amd64` / `//modules/ingest:image_amd64` - build the knora-api /
  dsp-ingest images directly; `bazel run //modules/webapi:load` / `//modules/ingest:load` loads
  them into the local Docker daemon at the same `:latest` tags `docker-compose.yml` uses.
- Fuseki's recipes (`just docker-build-fuseki-image`/`just docker-publish-fuseki-image`) build and
  publish the fuseki image with Bazel (`//modules/fuseki:load`/`:push`), same as the other three.
- CI runs entirely through `just` and `bazel test`, pointed at the shared **NativeLink RBE backend**
  (`dasch-remotebuild-prod-01`) for a remote cache + executor. See `docs/development/dsp-api-rbe.md`.
  sbt has been fully removed: formatting/linting is the rules_scala scalafmt toolchain, license
  headers are `//tools/license`, and dependency updates run through Renovate (`.github/renovate.json`)
  with `MODULE.bazel` as the sole version source.

## Architecture

### Module Structure

The codebase is organized into several key modules:

**Core Modules:**

- `modules/webapi/` - Main API application
- `modules/bagit/` - BagIt library for creating, reading, and validating BagIt packages (RFC 8493, <https://www.rfc-editor.org/rfc/rfc8493>)
- `modules/testkit/` - Shared test utilities and base classes
- `modules/test-it/` - Integration tests (service/repo/Sipi tests)
- `modules/test-e2e/` - End-to-end HTTP API tests
- `modules/sipi/` - Custom Sipi media server configuration

**Slice Architecture** (`modules/webapi/src/main/scala/org/knora/webapi/slice/`):

- `admin/` - Administrative endpoints (users, groups, projects, permissions)
- `common/` - Shared utilities and base classes
- `infrastructure/` - Cross-cutting concerns (metrics, caching, JWT)
- `lists/` - List management functionality
- `ontology/` - Ontology management
- `resources/` - Resource and value management
- `search/` - Search functionality
- `security/` - Authentication and authorization
- `shacl/` - SHACL validation

Each slice typically contains:

- `api/` - REST endpoints and routes
- `domain/` - Business logic and domain models
- `repo/` - Data access layer

### Technology Stack

- **Language**: Scala 3.8.4
- **Framework**: ZIO 2.x for functional programming
- **HTTP**: zio-http as the HTTP server, with Tapir for endpoint definition
- **Database**: Apache Jena Fuseki (RDF triplestore)
- **Media Server**: Sipi (C++ media server)
- **Testing**: ZIO Test framework, some ScalaTests exist but will be migrated to ZIO Test
- **JSON**: ZIO JSON for serialization

### Key Design Patterns

- **Functional Programming**: Heavy use of ZIO effects
- **Repository Pattern**: Data access abstraction
- **Service Layer**: Business logic separation
- **Dependency Injection**: ZIO layers for service composition
- **Value Types**: Use `Value[A]` (e.g. `StringValue`, `BooleanValue`, `IntValue`) to make domain models type-safe with validated construction. See `docs/development/dsp-api-value-types.md` for details.

## Testing Guidelines

**If the implementation plan involves adding test data**, apply this check before choosing where to add it:

1. **Verify instances, not just schema.** Confirm whether existing test data instances already exercise the scenario — finding that the schema supports a case is not sufficient.
2. **Discover self-contained fixtures first.** Check if the component under test has its own fixture files before adding to a shared dataset. Shared datasets are loaded by many tests; a single new record can cause cascading failures across unrelated specs.

### Test Organization

- Unit tests: `modules/webapi/src/test/scala/`
- Integration tests: `modules/test-it/src/test/scala/`
- End-to-end tests: `modules/test-e2e/src/test/scala/`
- Shared test utilities: `modules/testkit/src/main/scala/`
- Tests are organized by module following the main source structure

### Test Execution

- Unit tests run against in-memory implementations
- Integration tests use Testcontainers for real database/service instances

### Test Data

- Test data located in `test_data/` directory
- Project ontologies in `test_data/project_ontologies/`
- Project data in `test_data/project_data/`

## Development Environment

### Prerequisites

- [Nix](https://determinate.systems) (with flakes) + `direnv` — the only toolchain install needed;
  the dev shell it loads provides `bazel`, JDK Temurin 25, `just`, and `crane` (see "Bazel & the Nix
  dev shell" above)
- Docker Desktop

Bazel (via the Nix dev shell) is the only build tool — sbt has been removed. Scala 3.8.4 is pinned in
`MODULE.bazel` (`scala_config`).

### Local Development

1. Start the development stack: `just stack-start-dev`
2. This provides Fuseki (port 3030) and Sipi (port 1024)
3. Run the API locally via IDE or `bazel run //modules/webapi:app`
4. API will be available at <http://localhost:3333>

### Testing Against the Dev Database

When changes are hard to test with local test data (e.g. they need realistic data), run the API against the remote dev Fuseki:

1. Create a `.env` file in the repo root with `DEV_DB_PASSWORD=<password>` (this file is git-ignored). Passwords can be found in [ops-deploy/host_vars](https://github.com/dasch-swiss/ops-deploy/tree/main/host_vars).
2. Run `just run-with-dev-db`
3. The API will start connected to `db.dev.dasch.swiss` via HTTPS

### Configuration

- Main config: `modules/webapi/src/main/resources/application.conf`
- Test config: `modules/webapi/src/test/resources/test.conf`
- Docker config: `docker-compose.yml`

### Scala language intelligence (Metals MCP)

This repo ships a checked-in `metals` MCP server intended to give agents Scala language intelligence
(compiler diagnostics, type-aware usage search, symbol docs and lookup). **When it is working, prefer
the `metals` MCP tools over shelling out to `bazel build`** — `compile-file` / `compile-module` and
`get-usages` are incremental and return structured results. For setup, the full tool list, and the
worktree/LOOM caveats see `docs/development/dsp-api-metals-mcp.md`.

**KNOWN LIMITATION (Bazel 9):** Metals connects to Bazel via **bazel-bsp**, but **bazel-bsp 4.0.x (the
server Metals 1.6.7 ships) is not compatible with Bazel 9.1.1** — its aspect uses removed Starlark globals
(`JavaInfo`) and a struct-returning aspect impl that Bazel 9 dropped, so the import runs but Scala targets
fail aspect analysis, `list-modules` is empty and type-aware navigation does not work. This is **not** a
rules_scala 7.x issue (that builds fine). There is no config-only fix on Bazel 9, so **fall back to
`grep`/read and `bazel build` for Scala work.** Tracked upstream at scalameta/metals#8268. See
`docs/development/dsp-api-metals-mcp.md` for the full root cause, the workaround, and the bootstrap flow
(`just metals-bootstrap` then the metals `import-build` tool; `.bazelproject` drives the import).

## API Structure

### Endpoint Definition

- Uses Tapir for type-safe endpoint definitions
- Endpoints defined in `*Endpoints.scala` files
- Handlers in `*EndpointsHandler.scala` files
- Routes in `*Routes.scala` files

### API Versions

- **Admin API**: Administrative functions
- **API v2**: Main application API
- **Management API**: Health checks and metrics

### Authentication

- JWT-based authentication
- Scopes for authorization
- Session management

## Common Development Tasks

### Adding New Endpoints

1. Define endpoint in the appropriate `*Endpoints.scala`
2. Connect endpoint definition with server logic in `*ServerEndpoints.scala`
3. Register in `CompleteApiServerEndpoints.scala`
4. Add unit/integration tests mirroring the main structure

### Bumping the SIPI version

The sipi version lives in **one place**: the two `oci.pull` digests in `MODULE.bazel`. There is no
duplicated tag string — the `/version` endpoint reads the tag from the pulled image's own
`org.opencontainers.image.version` OCI label (`//tools/buildinfo:oci_config_label`).

When applying a new `daschswiss/sipi` release, update `MODULE.bazel`: the two `oci.pull` blocks
(`sipi_base_amd64`, `sipi_base_arm64`) are pinned by **per-arch single-manifest digest**, not the tag.
The arm64 index entry carries a `v8` variant that a bare `linux/arm64` request does not match, so pull
each platform's manifest directly. Also update the tag and the index digest in the comment above them
(the comment tag is documentation + a Renovate anchor; the digests are what the build uses).

Get the digests for the target tag with:

```bash
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z --raw \
  | jq -r '.manifests[] | "\(.platform.os)/\(.platform.architecture)\(.platform.variant // "") \(.digest)"'
docker buildx imagetools inspect daschswiss/sipi:vX.Y.Z | grep -i digest   # index digest
```

`docker-compose.yml` needs no change — it uses `daschswiss/knora-sipi:latest` (the derived image built
from this base), not a pinned version. After bumping, remember to sync the same version in the
**ops-deploy** repo when deploying the DSP release.

(The **Fuseki** image tag is likewise single-sourced — in `MODULE.bazel` via `image_versions.fuseki`,
consumed by `modules/fuseki/BUILD.bazel` and the `/version` report through `@dsp_image_versions`.)

### Code Style

- Use Scalafmt for formatting
- Follow functional programming principles
- Prefer ZIO effects over side effects
- Use meaningful names and types

## Troubleshooting

### Common Issues

- **Docker**: Ensure Docker Desktop is running
- **Database**: Check Fuseki is accessible at localhost:3030
- **Tests**: Integration tests require built Docker images

### Debugging

- Use `just stack-logs` to view all service logs
- Check `just stack-health` for API health status
- Use `just stack-status` to see container status


### Writing SPARQL queries

When writing SPARQL queries do not use String concatenation.
Instead use rdf4j and the query helper in dsp-api.
For more details see `docs/development/dsp-api-sparql-queries.md`.

### Development Conventions

When writing code, follow the conventions outlined in `docs/development/dsp-api-conventions.md` for consistency across
the codebase. This includes structuring test suites, naming conventions, and using ZIO Test features effectively.

See also `CONVENTIONS.md` (work-phase agent reference card — code/testing/commit conventions, with pointers into
`docs/development/`) and `REVIEW.md` (review-phase checklist). Update these alongside `docs/development/` whenever a
convention changes.

### Observability

When working on observability — OpenTelemetry tracing, spans, metrics, or anything that emits or
reads telemetry (e.g. instrumenting a responder, adding span attributes, debugging a slow request via
traces) — read `docs/observability/` first. Key entry points:

- `docs/observability/index.md` — what is instrumented and where the traces live.
- `docs/observability/instrumentation-recipe.md` — the mandatory pattern for adding per-stage tracing
  to a responder (root + stage spans, bounded query shape, sanitized errors, `exit_reason`). Follow
  it rather than re-deriving; the load-bearing `UNSET` status-mapper rule prevents leaking user data
  into span status.
- `docs/observability/gravsearch-trace-runbook.md` and `docs/observability/traceql-recipes.md` — how
  to read traces and query them in Grafana.
- `docs/observability/using-grafana.md` — how to run those queries in the Grafana UI and from Claude
  Code via the Grafana MCP server.

### Markdown Formatting

After editing any markdown files, run the `/fix-markdownlint` skill to ensure proper formatting.

### IRI Handling

Universal rules for constructing typed IRI value objects (e.g. `ResourceIri`) from strings — see
`docs/development/dsp-api-iri-handling.md`. Key rules:

- Never call `unsafeFrom` in responders or RestServices — use `ZIO.fromEither(Xxx.from(...))`.
- Map conversion errors to the right failure type per layer: `BadRequestException` in RestServices,
  domain errors in services, `InconsistentRepositoryDataException` in repos.
- Never `.die` on an IRI conversion failure — a malformed client IRI is a 400, not a 500.
- Prefer converting at the API boundary so services/responders receive typed IRIs.

### V3 API — IRI Handling

When adding or modifying v3 endpoints that accept IRI parameters, follow the two-category model
described in `docs/development/dsp-api-v3-iri-handling.md`:

- **Simple IRIs** (e.g. `ProjectIri`, `UserIri`): use the typed value object directly in endpoints.
- **SmartIri-backed IRIs** (ontology, class, property IRIs): accept as `IriDto` in endpoints, convert via `IriConverter` in the RestService.
