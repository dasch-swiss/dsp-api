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

- `modules/bagit/` implements BagIt packages per RFC 8493 (<https://www.rfc-editor.org/rfc/rfc8493>).
- Each slice under `modules/webapi/src/main/scala/org/knora/webapi/slice/` contains `api/` (REST
  endpoints and routes), `domain/` (business logic and domain models) and `repo/` (data access layer).

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

### Scala language intelligence (Metals MCP)

This repo ships a checked-in `metals` MCP server intended to give agents Scala language intelligence
(compiler diagnostics, type-aware usage search, symbol docs and lookup). **When it is working, prefer
the `metals` MCP tools over shelling out to `bazel build`** — `compile-file` / `compile-module` and
`get-usages` are incremental and return structured results. For setup, the full tool list, and the
worktree/LOOM caveats see `docs/development/dsp-api-metals-mcp.md`.

**Bazel-9 note:** Metals connects to Bazel via **bazel-bsp**, whose bundled aspect (in bazel-bsp 4.0.x)
isn't Bazel-9-compatible out of the box. Two local fixes make it work: a macOS-scoped
`--incompatible_autoload_externally` flag in `.bazelrc` (restores the `JavaInfo`/`CcInfo` globals Bazel 9
removed) and a `bazel_binary` wrapper (`tools/metals/bazel-bsp-wrapper.sh`) that re-applies a struct→provider
patch to bazel-bsp's `core.bzl` on each invocation. So `compile-file`/`compile-module`/`get-usages` all work.
This is **not** a rules_scala 7.x issue. It's a vendored patch to bazel-bsp — track scalameta/metals#8268 and
remove it once upstream supports Bazel 9. Bootstrap in a fresh worktree: `just metals-bootstrap`, then the
metals `import-build` tool. Full details in `docs/development/dsp-api-metals-mcp.md`.

## Common Development Tasks

### Adding New Endpoints

1. Define endpoint in the appropriate `*Endpoints.scala`
2. Connect endpoint definition with server logic in `*ServerEndpoints.scala`
3. Register in the API's aggregator (`AdminApiServerEndpoints.scala` for admin)
4. Add unit/integration tests mirroring the main structure

### Bumping the SIPI version

See "Docker Image Versions" in `docs/05-internals/development/third-party.md`.

## Development Guidelines

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
