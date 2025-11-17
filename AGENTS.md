# AGENTS.md

This document provides guidance to any AI coding agent working with this repository. It summarizes the project, the
development workflow, safe operating practices, and includes a checklist to quickly gather context before making changes.

## Checklist (ask these first)

Use these concise questions at the start of a session to align on scope, constraints, and desired outcomes. Ask only
what is relevant to the task at hand.

- Goal: What problem or feature should be completed first?
- Scope: Which files/areas are in or out of scope for this task?
- Branching: Should I work on a new branch name or directly on `main`?
- Run: How should I run the app locally (IDE vs. `./sbtx run`)? Any env vars?
- Tests: Which tests must pass? Any long-running or flaky tests to skip?
- Data: Do I need local test data seeded? Use `just stack-init-test`?
- Services: Should I start the local stack (Fuseki/Sipi) or mock services?
- Docker: Is Docker required for this task, or is pure `./sbtx` enough?
- Versions: Confirm JDK/Scala versions to use (Temurin 21, Scala 3.3.x)?
- Output: What deliverable is expected (code, docs, PoC, analysis, PR)?
- Review: Any coding standards or reviewers I should tailor the change for?
- Safety: Any destructive actions off-limits (migrations, `rm`, force pushes)?
- Secrets: Any credentials or tokens required, or should I operate offline?
- Timebox: Any time or token budget I should optimize for?
- Approval: Do I need explicit approval before running networked or destructive commands?

When context is insufficient, prefer asking 1–3 focused questions rather than proceeding on assumptions.

## Project Overview

DSP-API is the Digital Humanities Service Platform API — a Scala-based REST API for managing semantic data and digital
assets in the humanities. The project uses ZIO for functional programming, zio-http and tapir for the API, and
integrates with Apache Jena Fuseki triplestore and the Sipi media server.

## Build System & Commands

- Primary: `sbt` (use the repo’s `./sbtx` wrapper for consistency)
- Helpers: `just` (task runner) and `make` (Docker/docs tasks)

Essential commands:
- Testing
  - `sbt test` — Run unit tests
  - `sbt "testOnly *TestClassName*"` — Single test
  - `sbt "testOnly org.knora.webapi.slice.admin.*"` — Package tests
  - `make test-it` — Integration tests (requires Docker)
  - `make test-e2e` — End-to-end HTTP API tests (requires Docker)
  - `make test-all` — All tests
  - Integration tests use `latest` Sipi by default. To use exact git version locally, set `SIPI_USE_EXACT_VERSION=true` or build with `make docker-build-sipi-image`.
- Code Quality
  - `sbt fmt` — Format with Scalafmt
  - `sbt check` — Check formatting and linting
  - `sbt "scalafixAll --check"` — Enforce Scalafix rules
- Build
  - `sbt compile` — Compile
  - `make docker-build` — All Docker images
  - `make docker-build-dsp-api-image` — API image only
- Local Stack
  - `just stack-start` — Start Fuseki, Sipi, API
  - `just stack-start-dev` — Start without API (develop API locally)
  - `just stack-stop` — Stop stack
  - `just stack-init-test` — Seed stack with test data

## Architecture Snapshot

- Modules
  - `webapi/` — Main API application
  - `modules/testkit/` — Shared test utilities and base classes
  - `modules/test-it/` — Integration tests (service/repo/Sipi tests)
  - `modules/test-e2e/` — End-to-end HTTP API tests
  - `sipi/` — Sipi media server configuration
- Slice architecture (`webapi/src/main/scala/org/knora/webapi/slice/`)
  - `admin/`, `common/`, `infrastructure/`, `lists/`, `ontology/`, `resources/`, `search/`, `security/`, `shacl/`
  - Typical slice structure: `api/` (endpoints), `domain/` (models/logic), `repo/` (data access)
- Technology
  - Language: Scala 3.3.x
  - FP: ZIO 2.x
  - HTTP: ZIO HTTP + Tapir
  - Store: Apache Jena Fuseki (RDF)
  - Media: Sipi
  - JSON: ZIO JSON
  - Tests: ZIO Test (some ScalaTest remains)
- Design
  - Effects: ZIO effect systems and layers (DI)
  - Repository: Abstracted data access
  - Services: Business logic separation

## Development Environment

- Prerequisites: JDK Temurin 21, sbt, Docker Desktop, `just` (optional), Scala 3.3.x
- Run locally
  1. `just stack-start-dev` to start Fuseki (3030) and Sipi (1024)
  2. Run the API via IDE or `sbt run` (API on http://localhost:3333)
- Configuration
  - App: `webapi/src/main/resources/application.conf`
  - Test: `webapi/src/test/resources/test.conf`
  - Docker: `docker-compose.yml`

## API Structure

- Endpoints: defined via Tapir in `*Endpoints.scala`
- ServerEndpoints: in `*ServerEndpoints.scala`
- API areas: Admin API, API v2 (main), Management (health/metrics)
- Auth: JWT; scoped authorization; session management

## Common Agent Tasks

- Add a new endpoint
  1. Define endpoint in the appropriate `*Endpoints.scala`
  2. Connect endpoint definition with server logic in `*ServerEndpoints.scala`
  3. Register in `CompleteApiServerEndpoints.scala`
  4. Add unit/integration tests mirroring the main structure
- Code style and patterns
  - Use Scalafmt; prefer ZIO effects over side effects
  - Name things explicitly; encode invariants in types when reasonable
  - Keep modules cohesive; follow slice boundaries

## Testing Guidelines

- Location
  - Unit tests: `webapi/src/test/scala/`
  - Integration tests: `modules/test-it/src/test/scala/`
  - End-to-end tests: `modules/test-e2e/src/test/scala/`
  - Shared test utilities: `modules/testkit/src/main/scala/`
  - Test data: `test_data/` with `project_ontologies/` and `project_data/`
- Execution
  - Unit tests should use in-memory or test doubles
  - Integration tests use Testcontainers and Dockerized services

## Agent Operating Guidelines

- Safety first
  - Avoid destructive commands (e.g., `rm -rf`, force pushes, data resets) unless explicitly requested.
  - Assume network access may be restricted; prefer offline builds and local tools.
  - Do not introduce secrets into code or logs. Use configuration files and env vars appropriately.
- Minimal, focused changes
  - Keep diffs small and scoped to the task.
  - Follow existing project structure and naming conventions.
  - Update or add documentation when behavior or usage changes.
- Verification
  - Run the most specific tests for the changed area first.
  - Use formatting and lint checks (`sbt fmt`, `sbt check`).
  - For API changes, consider adding or updating endpoint tests.
- Collaboration
  - When requirements are unclear, ask 1–3 targeted /init questions.
  - Prefer adding comments to PR descriptions rather than inline noise in code.

## Troubleshooting

- Docker: Ensure Docker Desktop is running for integration tests.
- Fuseki: Available at `http://localhost:3030` when the stack is up.
- Logs: `make stack-logs` aggregates service logs; `make stack-status` shows container status; `make stack-health` checks API health.

## Quick References

- Run tests: `sbt test`
- Format: `sbt fmt`
- Compile: `sbt compile`
- Start dev stack: `just stack-start-dev`
- Run API locally: `sbt run` (API at `http://localhost:3333`)

---

If you are an AI coding agent new to this repository, begin with the checklist, confirm you can compile with
`./sbtx compile`, and then proceed to implement the smallest change that provides value while keeping the codebase’s
functional and slice architecture conventions.
