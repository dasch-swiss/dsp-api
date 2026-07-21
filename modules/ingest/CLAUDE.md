# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

dsp-ingest is a module of dsp-api; build, test, and format it from the **repo root** via the
dsp-api build (see the root `CLAUDE.md`), not from this directory.

- `bazel test //modules/ingest:test` (or `just test-ingest`) - unit tests
- `bazel test //modules/test-ingest-integration:test` (or `just test-ingest-integration`) - integration tests (Docker/testcontainers)
- `just docker-build-ingest-image` - build the dsp-ingest image (Bazel + load into the local Docker daemon)
- `sbt fmt` / `sbt check` - format / check formatting (whole repo)
- `./sbtx "ingest/run"` - run the service locally via sbt

### Testing
- Unit tests use the ZIO Test framework; run via `bazel test //modules/ingest:test`.
- Integration tests live in `modules/test-ingest-integration/` and use testcontainers.

## Architecture Overview

This is a Scala 3 application using ZIO ecosystem for functional programming:

### Core Technologies
- **ZIO 2.x** - Effect system for async/concurrent programming
- **tAPIr** - Type-safe API endpoint definitions with auto-generated OpenAPI docs
- **ZIO HTTP** - HTTP server implementation
- **SQLite** - Database with plain JDBC (Flyway migrations in `src/main/resources/db/migration/`)

### Application Structure
- **Main.scala** - Application entry point with dependency injection layer
- **Endpoints.scala** - API endpoint aggregation with Swagger docs generation
- **Configuration.scala** - Application configuration using ZIO Config with Typesafe Config
- **IngestApiServer.scala** - HTTP server setup with CORS and metrics

### Domain Architecture
The application follows Domain-Driven Design patterns:

- **Domain Layer** (`swiss.dasch.domain`):
  - `IngestService` - Core business logic for file ingestion
  - `ProjectService` - Project management operations
  - `AssetInfoService` - Asset metadata and file operations
  - `StorageService` - File system operations
  - `SipiClient` - Integration with Sipi image server

- **API Layer** (`swiss.dasch.api`):
  - `ProjectsEndpoints` - Project CRUD operations
  - `MaintenanceEndpoints` - Administrative operations
  - `MonitoringEndpoints` - Health checks and metrics
  - `ReportEndpoints` - Asset reporting functionality

- **Infrastructure Layer** (`swiss.dasch.infrastructure`):
  - Health checks, metrics, command execution
  - Database connections and migrations

### Key Domain Concepts
- **Assets** - Files being ingested (images, videos, documents)
- **Projects** - Organizational units for assets with shortcodes
- **Ingest Process** - File upload, validation, transcoding, and storage workflow
- **Asset References** - Unique identifiers following `{project}-{base62}` pattern

### File Organization
- Main source: `src/main/scala/swiss/dasch/`
- Unit tests mirror source structure in `src/test/scala/swiss/dasch/`
- Integration tests in `modules/test-ingest-integration/`
- Database migrations: `src/main/resources/db/migration/`
- Configuration: `src/main/resources/application.conf`

### Development Environment
- Service runs on port 3340 by default
- Uses JWT authentication (can be disabled with `JWT_DISABLE_AUTH=true`)
- Local development storage in `localdev/storage/`
- Docker setup integrates with Sipi image server (`daschswiss/knora-sipi`)

### Code Style
- Scala 3 with `-old-syntax` flag for compatibility
- Copyright headers required (Apache 2.0)
- Scalafmt for formatting
- Conventional Commits for commit messages
