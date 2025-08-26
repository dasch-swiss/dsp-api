# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

The project uses both SBT and Just for build and development tasks:

### SBT Commands (via `./sbtx` wrapper)
- `./sbtx run` - Run the service locally
- `./sbtx "~run"` - Run with auto-reload on file changes
- `./sbtx test` - Run unit tests
- `./sbtx integration/test` - Run integration tests
- `./sbtx fmt` - Format source code (scalafmt)
- `./sbtx fmtCheck` - Check code formatting
- `./sbtx headerCreateAll` - Add copyright headers to all files
- `./sbtx headerCheckAll` - Check copyright headers
- `./sbtx Docker/publishLocal` - Build Docker image locally

### Just Commands (modern task runner)
- `just` - List all available recipes
- `just localdev-run` - Start service locally with JWT auth disabled
- `just localdev-cleandb` - Remove SQLite database file
- `just build-docker` - Build Docker image
- `just build-and-run-docker` - Build and run with docker-compose
- `just run-integration-tests` - Run integration tests (builds Docker first)
- `just docs-openapi-generate` - Generate OpenAPI specs from tAPIr endpoints
- `just docs-serve` - Serve documentation locally with MkDocs

### Testing
- Unit tests use ZIO Test framework (`zio.test.sbt.ZTestFramework`)
- Integration tests are in separate `integration/` project using testcontainers
- Always run formatting checks before committing: `./sbtx fmtCheck`

## Architecture Overview

This is a Scala 3 application using ZIO ecosystem for functional programming:

### Core Technologies
- **ZIO 2.x** - Effect system for async/concurrent programming
- **tAPIr** - Type-safe API endpoint definitions with auto-generated OpenAPI docs
- **ZIO HTTP** - HTTP server implementation
- **Quill** - Database access with compile-time query generation
- **SQLite** - Database (with Flyway migrations in `src/main/resources/db/migration/`)

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
- Tests mirror source structure in `src/test/scala/swiss/dasch/`
- Integration tests in separate `integration/` subproject
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