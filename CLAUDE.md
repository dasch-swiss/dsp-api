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

- **Primary**: `sbt` (Scala Build Tool) - use `./sbtx` wrapper script
- **Alternative**: `just` (command runner) for common tasks
- **Alternative**: `make` for Docker and documentation tasks

### Essential Development Commands

**Testing:**

- Run a single test: `sbt "testOnly *TestClassName*"`
- Run tests in a specific package: `sbt "testOnly org.knora.webapi.slice.admin.*"`
- `sbt test` - Run unit tests
- Integration tests use `latest` Sipi image by default. To use exact git version locally, set `SIPI_USE_EXACT_VERSION=true` or build with `make docker-build-sipi-image`.
- `make test-it` - Run integration tests (requires Docker)
- `make test-e2e` - Run end-to-end HTTP API tests (requires Docker)
- `make test-all` - Run all tests

**Code Quality:**

- `sbt fmt` - Format code with Scalafmt
- `sbt check` - Check code formatting and linting
- `sbt "scalafixAll --check"` - Check Scalafix rules

**Building:**

- `sbt compile` - Compile the project
- `make docker-build` - Build Docker images
- `make docker-build-dsp-api-image` - Build only the API Docker image

**Local Development Stack:**

- `just stack-start` - Start the full stack (Fuseki, Sipi, API)
- `just stack-start-dev` - Start stack without API (for development)
- `just stack-stop` - Stop the stack
- `just stack-init-test` - Initialize with test data

### Bazel & the Nix dev shell

The custom Sipi Docker image is built with **Bazel** (`rules_oci`), not sbt. Bazel is provided through a **Nix dev shell** (`flake.nix`) that puts `bazel` (a bazelisk wrapper; the version is pinned in `.bazelversion`), a JDK 25, `just`, and `crane` on `PATH`.

- **Enter the shell:** with `direnv` it loads automatically on `cd` into the repo (`.envrc` runs `use flake`; run `direnv allow` once). Without direnv, prefix commands with `nix develop --command`, e.g. `nix develop --command make docker-build-sipi-image`.
- `make docker-build-sipi-image` - build the Sipi image and load it into the local Docker daemon (`:latest` plus the git-describe version tag); runs `bazel run //modules/sipi:load`.
- `make docker-publish-sipi-image` - build and push the multi-arch image; runs `bazel run //modules/sipi:push`.
- The dsp-api and dsp-ingest images stay on sbt. `make docker-build` / `make docker-publish` build all three in order (Sipi first, since dsp-ingest derives from it).

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

- **Language**: Scala 3.3.5
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

- JDK Temurin 25
- sbt
- Docker Desktop
- Nix (with flakes) + direnv — provides the Bazel dev shell used to build the Sipi image (see "Bazel & the Nix dev shell" above)
- just (optional)
- Scala 3.3.X

### Local Development

1. Start the development stack: `just stack-start-dev`
2. This provides Fuseki (port 3030) and Sipi (port 1024)
3. Run the API locally via IDE or `sbt run`
4. API will be available at <http://localhost:3333>

### Configuration

- Main config: `modules/webapi/src/main/resources/application.conf`
- Test config: `modules/webapi/src/test/resources/test.conf`
- Docker config: `docker-compose.yml`

### Scala language intelligence (Metals MCP)

This repo ships a checked-in `metals` MCP server giving agents real Scala language intelligence (compiler
diagnostics, type-aware usage search, symbol docs and lookup). **Prefer the `metals` MCP tools over direct
`sbt`/`sbtx` calls** — e.g. use `compile-file` / `compile-module` instead of `sbt compile`, and `get-usages`
instead of grep. It is much faster (incremental, no JVM/sbt startup per call) and more capable (structured
diagnostics, type-aware navigation). For setup, the full tool list, usage pattern, and the worktree/LOOM
caveats see `docs/development/dsp-api-metals-mcp.md`.

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

- Use `make stack-logs` to view all service logs
- Check `make stack-health` for API health status
- Use `make stack-status` to see container status


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
