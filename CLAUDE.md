# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DSP-API is the Digital Humanities Service Platform API - a Scala-based REST API for managing semantic data and digital assets in the humanities. 
The project uses ZIO for functional programming, Pekko HTTP (Apache Pekko) for the API, and integrates with Apache Jena Fuseki triplestore and Sipi media server.

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

## Architecture

### Module Structure
The codebase is organized into several key modules:

**Core Modules:**
- `webapi/` - Main API application
- `modules/testkit/` - Shared test utilities and base classes
- `modules/test-it/` - Integration tests (service/repo/Sipi tests)
- `modules/test-e2e/` - End-to-end HTTP API tests
- `sipi/` - Custom Sipi media server configuration

**Slice Architecture** (`webapi/src/main/scala/org/knora/webapi/slice/`):
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
- **HTTP**: Pekko HTTP (Apache Pekko) with Tapir for endpoint definition
- **Database**: Apache Jena Fuseki (RDF triplestore)
- **Media Server**: Sipi (C++ media server)
- **Testing**: ZIO Test framework, some ScalaTests exist but will be migrated to ZIO Test
- **JSON**: ZIO JSON for serialization

### Key Design Patterns
- **Functional Programming**: Heavy use of ZIO effects
- **Repository Pattern**: Data access abstraction
- **Service Layer**: Business logic separation
- **Dependency Injection**: ZIO layers for service composition

## Testing Guidelines

### Test Organization
- Unit tests: `webapi/src/test/scala/`
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
- JDK Temurin 21
- sbt
- Docker Desktop
- just (optional)
- Scala 3.3.X

### Local Development
1. Start the development stack: `just stack-start-dev`
2. This provides Fuseki (port 3030) and Sipi (port 1024)
3. Run the API locally via IDE or `sbt run`
4. API will be available at http://localhost:3333

### Configuration
- Main config: `webapi/src/main/resources/application.conf`
- Test config: `webapi/src/test/resources/test.conf`
- Docker config: `docker-compose.yml`

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
1. Define endpoint in appropriate `*Endpoints.scala`
2. Implement handler in `*EndpointsHandler.scala`
3. Add route in `*Routes.scala`
4. Add to main router in `ApiRoutes.scala`

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