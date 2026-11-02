# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

Ingest is a library of dsp-api, served by it under `/ingest/`; build, test, and format it from the **repo root** via
the dsp-api build (see the root `CLAUDE.md`), not from this directory.

- `bazel test //modules/ingest:test` (or `just test-ingest`) - unit tests
- `just fmt` / `just check` - format / check formatting (whole repo)
- `bazel run //modules/webapi:app` - run dsp-api, which serves ingest under `/ingest/` (there is no standalone app)

### Testing

- Unit tests use the ZIO Test framework; run via `bazel test //modules/ingest:test`.
- End-to-end coverage of the `/ingest/` routes lives in `modules/test-e2e/` (`slice/ingest/IngestEndpointsE2ESpec`).

## Architecture

- Endpoints follow `*Endpoints.scala` (Tapir definitions) → `*EndpointsHandler.scala`
  (handlers); dsp-api's `slice/ingest/IngestServerEndpoints` mounts them under `/ingest/`.

### Key Domain Concepts

- **Assets** - Files being ingested (images, videos, documents)
- **Projects** - Organizational units for assets with shortcodes
- **Ingest Process** - File upload, validation, transcoding, and storage workflow
- **Asset References** - Unique identifiers following `{project}-{base62}` pattern

### Development Environment

- Served on the dsp-api port (3333) under `/ingest/`
- Uses JWT authentication with dsp-api's secret, issuer and `app.dsp-ingest.audience`
- Configuration in `src/main/resources/ingest.conf` (`INGEST_*` environment variables)
- Runs the Sipi CLI, `ffmpeg` and `ffprobe` from the dsp-api image; with `INGEST_SIPI_USE_LOCAL_DEV=true` it runs Sipi
  through `docker run` of `daschswiss/knora-sipi`

### Code Style

- Copyright headers required (Apache 2.0)
- Conventional Commits for commit messages
