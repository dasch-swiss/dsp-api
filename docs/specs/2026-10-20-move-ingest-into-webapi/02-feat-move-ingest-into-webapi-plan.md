---
title: "feat: Ingest runs inside dsp-api under /ingest/"
type: feat
date: 2026-10-26
author: "Raitis Veinbahs"
status: draft
prd: 01-move-ingest-into-webapi-PRD.md
---

# feat: Ingest runs inside dsp-api under /ingest/

## Revisions

- **2026-10-27, owner decision: minimal diff; the structure under `modules/ingest` is not touched.** The previous
  shape moved 67 files into `modules/webapi/.../slice/ingest/` with package renames, rebuilt the HTTP layer as new
  `Ingest*Endpoints`/`*ServerEndpoints`/`*RestService` files with re-ported specs, copied test helpers and binary
  fixtures, and kept the standalone module alive by making it depend on webapi (+11k/−7.5k without rename
  detection). It is rejected for diff size (see Alternative Approaches Considered) and preserved on branch
  `backup/docs/move-ingest-into-webapi-brief` (tip `11c2df207`). Phase 1 (done) is unchanged; Phases 2 and later
  are rewritten. Cross-repo contracts are unchanged, except that the size and MIME-type backfills stay main
  classes instead of becoming maintenance actions.

## Overview

`modules/ingest` (package `swiss.dasch`) stays where it is, with its file layout, and becomes a library that
`//modules/webapi` depends on. dsp-api serves ingest's existing tapir server endpoints under `/ingest/` with ingest's
own error bodies, token check, scopes and value types. Ingest reads its own `ingest.conf` (`INGEST_*` variables)
and gets dsp-api's JWT secret, issuer and ingest audience from webapi. Ingest's permission callback and dsp-api's
`DspIngestClient` become in-process calls. Child processes are the OOM killer's first choice and run under a
bounded number of permits, and ingest request handling runs on a dedicated executor. The SQLite database (Phase 1),
the standalone `Main`, HTTP server, image, publishing and local-stack container go.

This plan covers dsp-api only. The proxy, deployment and client changes are separate plans in ops-deploy, dsp-app
and dsp-tools (see Dependencies & Risks).

## Problem Statement / Motivation

See the PRD. In short: two services to build, deploy, configure and monitor, two token validators, a second
public address for clients, and a SQLite database that nothing reads to decide behaviour. dsp-api already
reaches into ingest's code (`//modules/webapi` depends on `//modules/ingest:ingest_classes` for
`AssetInfoService`) and over HTTP (`DspIngestClient`), and ingest calls back into dsp-api over HTTP
(`FetchAssetPermissions` -> `GET /admin/files/...`). The split costs more than it isolates.

## Proposed Solution

1. **SQLite goes first, in the standalone module** (Phase 1, done).
2. **Ingest is a library; webapi mounts its server endpoints.** Ingest's handlers already expose
   `List[ZServerEndpoint[Any, ZioStreams]]` (`api/*EndpointsHandler.scala`), the type webapi's
   `slice/api/Endpoints.scala` aggregates. A new webapi `IngestServerEndpoints` takes the four handlers and maps each
   endpoint with tapir's `ServerEndpoint.prependSecurityIn("ingest")`, so the routes, inputs, outputs, error bodies
   and docs are ingest's own. Ingest's `RequiresMethod` bases already force a method on every endpoint.
3. **Auth keeps ingest's acceptance rule.** Ingest's `AuthServiceLive` and `AuthorizationHandlerLive` are reused as
   they are. Webapi provides their `Configuration.JwtConfig` from `app.jwt` (secret, issuer) and
   `app.dsp-ingest.audience`, with `disableAuth = false`. Scope refusals stay `401` with the standalone bodies,
   logged-out tokens stay accepted (ingest never consulted webapi's logout cache), and `/ingest/metrics` stays public.
4. **Permission check in-process.** Ingest's `FetchAssetPermissions` trait stays in ingest; its HTTP
   `FetchAssetPermissionsLive` is deleted. Webapi implements the trait over `AssetPermissionsCache` and
   `Authenticator` (ADR-0011: the consumer defines the port, the provider's side implements it; no dependency cycle).
5. **Separate configuration.** `modules/ingest/src/main/resources/application.conf` becomes `ingest.conf` (root
   `ingest`), read by ingest's own `Configuration.layer` with `ConfigFactory.parseResources("ingest.conf")`. The
   `service`, `jwt` and `dsp-api` blocks go. webapi keeps `ConfigFactory.load()`, which never reads `ingest.conf`, so no
   key or variable is read by both. Every ingest variable gets the `INGEST_` prefix (see the mapping table).
6. **`DspIngestClient` in-process.** The trait and its four consumers stay. `DspIngestClientLive` is rewritten in
   place over ingest's `AssetInfoService`, `ProjectService` and `ImportService`, keeping the HTTP client's behaviour:
   not-found export is `None`, import failures are logged only, erase is gated by ingest's `allow-erase-projects`.
7. **Contained workload, smallest form.** A small `IngestExecutor` in ingest (`swiss.dasch.infrastructure`) owns a
   fixed pool of `max(1, floor(availableProcessors * cpu-share))` threads and a semaphore with as many permits.
   `CommandExecutorLive.execute` takes a permit and, outside local-dev mode, starts the process through
   `/bin/sh -c 'echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"'`. `IngestServerEndpoints` runs the logic of
   every ingest endpoint except info, health and metrics `onExecutor` that pool; forked jobs inherit it.
8. **Image and removal.** The dsp-api image layers `//modules/sipi:runtime_fs_{amd64,arm64}` (it already runs as
   root). The standalone `Main`, server wiring, `app` binary, image, publishing, `modules/test-ingest-integration`,
   the testkit ingest container, the compose service, just recipes and CI job go.

### Configuration mapping

| Standalone setting (env var) | After the move |
| --- | --- |
| `service.host`/`port`/`log-format`/`log-level` (`SERVICE_*`) | dropped; dsp-api's server and logging apply |
| `jwt.secret`/`issuer` (`JWT_SECRET`, `JWT_ISSUER`) | dropped; dsp-api's `app.jwt` is used |
| `jwt.audience` (`JWT_AUDIENCE`) | dropped; dsp-api's existing `app.dsp-ingest.audience` (`KNORA_WEBAPI_DSP_INGEST_AUDIENCE`), which it already mints into every token |
| `jwt.disable-auth` (`JWT_DISABLE_AUTH`) | dropped; tests send real tokens |
| `db.jdbc-url` (`DB_JDBC_URL`) | dropped |
| `dsp-api.url` (`DSP_API_URL`) | dropped; permission check is in-process |
| `storage.asset-dir` (`STORAGE_ASSET_DIR`) | `ingest.storage.asset-dir` (`INGEST_STORAGE_ASSET_DIR`), default `/opt/images` |
| `storage.temp-dir` (`STORAGE_TEMP_DIR`) | `ingest.storage.temp-dir` (`INGEST_STORAGE_TEMP_DIR`), default `/opt/temp` |
| `sipi.use-local-dev` (`SIPI_USE_LOCAL_DEV`) | `ingest.sipi.use-local-dev` (`INGEST_SIPI_USE_LOCAL_DEV`), default `false` |
| (image tag baked from BuildInfo) | `ingest.sipi.local-dev-image` (`INGEST_SIPI_LOCAL_DEV_IMAGE`), default `daschswiss/knora-sipi:latest`, read only in local-dev mode |
| `ingest.bulk-max-parallel` (`INGEST_BULK_MAX_PARALLEL`) | `ingest.bulk-max-parallel`, same env var |
| `features.allow-erase-projects` (`ALLOW_ERASE_PROJECTS`) | `ingest.features.allow-erase-projects` (`INGEST_ALLOW_ERASE_PROJECTS`), default `false` |
| (new) | `ingest.executor.cpu-share` (`INGEST_EXECUTOR_CPU_SHARE`), default `0.5`, valid range `(0, 1]` |

webapi's `app.dsp-ingest` block shrinks to `audience`: `base-url`, `external-base-url` and `asset-dir`
(`KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `..._EXTERNAL_BASE_URL`, `..._ASSET_DIR`) are removed, and the asset directory
for asset info comes from ingest's `StorageConfig`.

`use-local-dev` now defaults to `false` (the standalone default was `true`, overridden in every container), so a
deployment that forgets the variable runs the binaries in the image rather than trying `docker run`. Local runs
outside a container (`bazel run //modules/webapi:app`, the testkit) set it to `true`. `local-dev-image` exists
because the testkit runs the Sipi image `SipiTestContainer` uses, not the git-version tag in ingest's `BuildInfo`.

## Alternative Approaches Considered

- **Move ingest into a webapi slice and rebuild its API layer** (the shape before 2026-10-27): `git mv` the domain
  into `slice/ingest/` with package renames, rebuild the HTTP layer on webapi's three-tier split with webapi's `Scope`,
  an `OriginalAccess` port, ported endpoint specs, copied test helpers and fixtures, and backfills as maintenance
  actions. Rejected for diff size (+11k/−7.5k without rename detection) and review cost; the gain was layout
  conformance, not behaviour. Preserved on `backup/docs/move-ingest-into-webapi-brief`.
- **Use webapi's `securedEndpoint` and `User` for ingest routes.** Rejected: it resolves a `User` from the subject,
  accepts HTTP basic and consults the logout cache, so it would reject tokens ingest accepts (e.g. the service token
  from `JwtService.createJwtForDspIngest`, whose subject is the issuer) and change error bodies (REQ-1.2, REQ-1.3).
- **Unify ingest's refined value types with webapi's** (`ProjectShortcode` vs `KnoraProject.Shortcode` etc.).
  Rejected: their decode-failure messages appear in `400` bodies (REQ-1.2).
- **Top-level `ingest { }` block inside webapi's `application.conf`.** Workable, but `ingest.conf` keeps the file in
  the ingest module and makes the "no shared key" rule visible in the layout.
- **Prefix the paths inside ingest's `BaseEndpoints`.** Equally small, but it changes ingest's own specs' URLs;
  `prependSecurityIn` at registration leaves ingest untouched.

## Technical Considerations

- **Module layout.** Unchanged under `modules/ingest/src/{main,test}`. Webapi-side code is two files in
  `slice/ingest/` (`IngestServerEndpoints`, `IngestModule`), the permission adapter in `slice/admin/domain/service/`
  and the rewritten `DspIngestClientLive` in place. `//modules/webapi` depends on `//modules/ingest`; the
  `ingest_classes` target goes, because `:ingest` then carries only `ingest.conf` (no `application.conf`, no
  `logback.xml`) and no longer collides with webapi's resources.
- **Layer wiring.** `IngestModule` is one `ZLayer.makeSome` over the ingest layers `Main` used to list, exposing only
  `IngestServerEndpoints`, `AssetInfoService` (replacing `store/iiif/impl/AssetInfoServiceLayer.scala`) and
  `DspIngestClient`. Ingest types never reach `LayersLive`'s environment, so name clashes (`BaseEndpoints`,
  `Features`, `JwtConfig`) do not matter. Its inputs are `AppConfig`, `PrometheusPublisher`, `AssetPermissionsCache`,
  `Authenticator` and ingest's configuration, which `LayersLive.remainingLayer` takes as a parameter (default
  ingest's `Configuration.layer`) so the testkit can pass its own.
- **Error envelope.** Ingest's `ApiProblem` outputs stay on ingest's `BaseEndpoints`. Nothing is added to webapi's
  `BaseEndpoints.errorOutputs`.
- **Security input.** Bearer only, as before; that keeps the routes CSRF-safe under webapi's credentialed,
  reflected-origin CORS.
- **Body buffering.** webapi runs `RequestStreaming.Hybrid(1 MiB)` (`core/DspApiServer.scala`): bodies with a
  declared `Content-Length` above 1 MiB stream; smaller bodies and every chunked body are aggregated before tapir
  runs, for any caller, and a chunked body over the threshold gets a bare `413`. See
  `dasch-specs/learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`.
- **Executor scope.** `onExecutor` wraps the server logic, not the security logic, of every ingest endpoint except
  info, health and metrics, and the export, import and erase methods of `DspIngestClientLive`. Forked daemon fibers
  (bulk ingest, maintenance actions, asset-overview report) inherit the executor; a spec asserts it. Checksums run on
  the calling fiber, so they stay on the pool. BagIt zip and unzip use `attemptBlocking`, so they take a permit
  themselves (owner, 2026-10-27). Permit sections are leaf-level: `CommandExecutorLive.execute` and the two BagIt
  calls take one, and none calls another.
- **Observability.** webapi's server span and `spanNameInterceptor` apply; routes show as
  `GET /ingest/projects/{shortcode}/...` (the prefix is part of the security input, which `showPathTemplate`
  includes; a spec pins it).
- **Metrics.** `/ingest/metrics` serves the API process's `PrometheusPublisher`, the same data as `:3339/metrics`.
  Ingest's per-endpoint `ZioMetrics` interceptor is not carried over; webapi's `RequestMetrics` covers the routes.
- **Info.** `/ingest/info` keeps ingest's `BuildInfo` (`name = "dsp-ingest"`), stamped with the same git version
  and commit as dsp-api.
- **Security review points.** The original-download route passes an optional token; an invalid token is anonymous
  for the endpoint, and the in-process adapter authenticates it with webapi's `Authenticator`. A failure there
  answers `500 "error fetching permissions"`, as it does today when dsp-api returns `401`. Erase stays gated by
  ingest's `allow-erase-projects` and admin scope.

## Implementation Phases

### Phase 1: Remove the ingest SQLite database

- [x] Delete `modules/ingest/src/main/scala/swiss/dasch/db/Db.scala` and `db/DbMigrator.scala`
- [x] Delete `modules/ingest/src/main/resources/db/migration/V1_0_0__create_project_table.sql`
- [x] Delete `domain/ProjectRepositoryLive.scala` (trait + live) and the `Project`/`ProjectId` entity in `domain/Project.scala`, keeping `ProjectShortcode`
- [x] Make `ProjectService.findOrCreateProject` only create the project folder, and `deleteProject` only delete it; remove `addProjectToDb`
- [x] Remove `SQLException` from error types, including the `"Unable to access database"` branch in `BulkIngestService.startBulkIngest`
- [x] Remove `MaintenanceActions.importProjectsToDb` and `ActionName.ImportProjectsToDb` (`api/MaintenanceEndpoints.scala`, `api/MaintenanceEndpointsHandler.scala`)
- [x] Delete `infrastructure/DbHealthIndicator.scala` and build `HealthCheckServiceLive` from `FileSystemHealthIndicator` alone
- [x] Remove `DbConfig` and the `db` block from `config/Configuration.scala` and `src/main/resources/application.conf`
- [x] Remove `Db.dataSourceLive`, `DbMigrator`, `DbHealthIndicator` and `ProjectRepositoryLive` from `Main.scala`, `MigrateSizes.scala` and `MigrateMimeTypes.scala`
- [x] Remove `@maven//:org_flywaydb_flyway_core`, `@maven//:org_xerial_sqlite_jdbc` and `@maven//:com_zaxxer_HikariCP` from both `scala_library` targets in `modules/ingest/BUILD.bazel`
- [x] Remove the three artifacts from `maven.install` in `MODULE.bazel` (no other module uses them) and re-pin with `bazel run @unpinned_maven//:pin`
- [x] Delete `src/test/scala/swiss/dasch/domain/ProjectRepositoryLiveSpec.scala` and drop the DB setup (`DbConfig` and the SQLite file helpers) from `util/TestUtils.scala` and the specs that use it
- [x] Add a `MonitoringEndpointsSpec` case: the health body has no `db` entry and is `200` with a healthy file system
- [x] Run `bazel test //modules/ingest:test`; it passes
- [x] Run `just test-ingest-integration`; it passes
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: Ship Sipi CLI, ffmpeg and ffprobe in the dsp-api image

- [x] Add `//modules/sipi:runtime_fs_amd64` / `runtime_fs_arm64` to the `tars` of `image_amd64` / `image_arm64` in `modules/webapi/BUILD.bazel`
- [x] Add `//modules/webapi:__pkg__` to the `runtime_fs_*` visibility in `modules/sipi/BUILD.bazel` and update its "dsp-ingest image" comment
- [x] Note in the webapi `oci_image` comment that the image runs as root because ingest writes NFS-owned assets
- [x] Record the baseline size of `daschswiss/knora-api:latest` (`docker image inspect --format '{{.Size}}'`) after `just docker-build-dsp-api-image` on the commit before this phase
- [x] Run `just docker-build-dsp-api-image`, then `docker run --rm --entrypoint /sbin/sipi daschswiss/knora-api:latest --help`, `... --entrypoint ffmpeg ... -version` and `... --entrypoint ffprobe ... -version`; each exits 0
- [x] Run `docker run --rm --entrypoint /bin/sh daschswiss/knora-api:latest -c 'echo 1000 > /proc/self/oom_score_adj && cat /proc/self/oom_score_adj'`; it prints `1000`
- [x] Compare the image size against the baseline; the growth stays within the `runtime_fs` layer size
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Serve ingest from dsp-api under /ingest/ and drop the standalone application

Ingest library:

- [x] `git mv modules/ingest/src/main/resources/application.conf modules/ingest/src/main/resources/ingest.conf` and rewrite it under a root `ingest` block with the keys, defaults and `INGEST_*` variables of the Configuration mapping table; drop the `service`, `jwt` and `dsp-api` blocks
- [x] In `config/Configuration.scala`, read `ConfigFactory.parseResources("ingest.conf").resolve().getConfig("ingest")`; remove `ServiceConfig` and `DspApiConfig` and their projections; add `SipiConfig.localDevImage` and `ExecutorConfig(cpuShare)` (failing to load outside `(0, 1]`); keep `JwtConfig` as a type (no longer loaded) and project `IngestConfig(bulkMaxParallel)` from the root key
- [x] Use `SipiConfig.localDevImage` instead of `BuildInfo.knoraSipiVersion` in `CommandExecutorLive.buildCommand`
- [x] Delete `Main.scala`, `Endpoints.scala`, `infrastructure/IngestApiServer.scala`, `infrastructure/Logger.scala`, `infrastructure/Metrics.scala`, `infrastructure/OtelSetup.scala` and `src/main/resources/logback.xml`
- [x] Delete `FetchAssetPermissionsLive`, `AssetAccessResponse` and the HTTP layer from `FetchAssetPermissions.scala`, keeping the trait; switch `ProjectsEndpointSpec` from the sttp stub to `FetchAssetPermissionsMock`
- [x] Remove the `bootstrap` override (`Configuration.layer >+> Logger.layer`) from `MigrateSizes.scala` and `MigrateMimeTypes.scala`; they keep their `scala_binary` targets
- [x] In `modules/ingest/BUILD.bazel`, delete `ingest_classes`, the `app` binary and the image section (`runtime_jars` through `oci_push`); keep `ingest`, `ingest_buildinfo`, the two migration binaries, `test` and `license_srcs`
- [x] Add a `ConfigurationSpec`: defaults load from `ingest.conf`, an `INGEST_*` override applies, `cpu-share` outside `(0, 1]` fails, and webapi's `ConfigFactory.load()` sees no `ingest` key

dsp-api side:

- [x] Replace `//modules/ingest:ingest_classes` with `//modules/ingest` in `//modules/webapi:webapi` deps and drop its comment
- [x] Add `slice/ingest/IngestServerEndpoints.scala`: takes ingest's four `*EndpointsHandler`s and returns their endpoints with `prependSecurityIn("ingest")`
- [x] Add `slice/admin/domain/service/IngestFetchAssetPermissions.scala` implementing `swiss.dasch.FetchAssetPermissions`: authenticate a present token with `Authenticator.authenticate`, use `AnonymousUser` when absent, and return whether `AssetPermissionsCache.getAssetAccess(user)(shortcode, filename).original` is granted
- [x] Rewrite `DspIngestClientLive` in `slice/admin/domain/service/DspIngestClient.scala` in-process: `getAssetInfo` maps `AssetInfoService.findByAssetRef` to `AssetInfoResponse` (`NotFoundException` when absent); `exportProject` moves `ProjectService.zipProject`'s result to `outputFile` (`None` without a project folder); `importProject` runs `ImportService.importZipStream` over the file, logging a failure and returning `fileToImport`; `eraseProject` deletes the project folder only when ingest's `allow-erase-projects` is on, logging otherwise; drop the sttp backend
- [x] Add `slice/ingest/IngestModule.scala`: one `ZLayer.makeSome` over the ingest layers `Main` listed, plus `JwtConfig(app.jwt.secret, app.dsp-ingest.audience, app.jwt.issuer)`, `IngestFetchAssetPermissions` and `DspIngestClientLive`, exposing `IngestServerEndpoints & AssetInfoService & DspIngestClient`
- [x] Wire `IngestModule` in `core/LayersLive.scala` in place of `AssetInfoServiceLayer.layer` and `DspIngestClientLive.layer`, add the ingest-config parameter to `remainingLayer`, and delete `store/iiif/impl/AssetInfoServiceLayer.scala`
- [x] Register `IngestServerEndpoints` in `slice/api/Endpoints.scala`
- [x] Remove `baseUrl` and `assetDir` from `DspIngestConfig` (`config/AppConfig.scala`) and `base-url` and `asset-dir` from `app.dsp-ingest` in `application.conf`; update `AppConfigSpec`

Testkit and tests:

- [x] Delete `modules/testkit/.../testcontainers/DspIngestTestContainer.scala`; drop it and the `dsp-ingest.base-url` override from `core/TestContainerLayers.scala` and `TestContainerLayersSpec`
- [x] Pass the testkit ingest config to `remainingLayer` from `E2EZSpec`: asset dir = `SharedVolumes.Images` host path, temp dir = `SharedVolumes.Temp` host path, `use-local-dev = true`, `local-dev-image` = the Sipi image `SipiTestContainer` runs
- [x] Point `testservices/TestDspIngestClient.scala` at the test API's `/ingest/` (`KnoraApi` host and port) instead of `DspIngestConfig.baseUrl`, and update `TestClientsModule`
- [x] Update the specs that referenced the removed container or config (`ProjectMigrationExportServiceSpec`, `ProjectMigrationImportServiceSpec`, `MetadataEndpointsE2ESpec`, `LegalInfoE2ESpec`, `KnoraSipiIntegrationV2ITSpec`, `StandoffEndpointsE2ESpec`), changing only setup
- [x] Replace `DspIngestClientLiveSpec` with a spec of the in-process client: asset info found and not found, export with and without a project folder, refused erase, and a failing import that is logged and returns `fileToImport`
- [x] Add `IngestFetchAssetPermissionsSpec`: granted, denied, anonymous, invalid token fails
- [x] Add `IngestServerEndpointsSpec`: every handler endpoint is served under `/ingest/`, and the path template starts with `/ingest/`
- [x] Add `IngestModuleSpec` with a token from `JwtService.createJwt` and one from `createJwtForDspIngest`: both are accepted by `/ingest/projects`; a token with a foreign audience or issuer is `401`
- [x] Add `modules/test-e2e/.../slice/ingest/IngestEndpointsE2ESpec.scala`: upload above 1 MiB with `Content-Length` through `POST /ingest/projects/0001/assets/ingest/{filename}` and read its info back with a matching checksum; no token is `401`; a token without write scope is `401` with ingest's body; `/ingest/health` is `200` without a `db` entry; `/ingest/maintenance/ImportProjectsToDb` is `400`; the original download is `200` for a permitted user and `403` for an anonymous caller on a restricted asset from the loaded test data

Removal of the standalone run path:

- [x] Delete `modules/test-ingest-integration/` (the upload path is covered by `IngestEndpointsE2ESpec` and the E2E specs that upload through `TestDspIngestClient`)
- [x] Remove `//modules/test-ingest-integration:license_srcs` from `tools/license/BUILD.bazel` and `tools/lint/BUILD.bazel`, and its `.bazelignore` entry
- [x] Remove `//modules/ingest:load` from `docker-load-test-images` and the `test-ingest-integration`, `docker-build-ingest-image` and `docker-publish-ingest-image` recipes from `justfile`, with their uses in `docker-build` and `docker-publish`
- [x] Remove `ingest` from the `docker compose up` service lists and recipe comments in `justfile`
- [x] Remove the `ingest` service and the `ingest-db` volume from `docker-compose.yml`; give the `api` service `./modules/sipi/images:/opt/images` read-write and `./modules/sipi/tmp-dsp-ingest:/opt/temp`, `INGEST_ALLOW_ERASE_PROJECTS=true`, drop `KNORA_WEBAPI_DSP_INGEST_BASE_URL` and `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR`, and raise its memory limit by ingest's 2G
- [x] Remove the `test-ingest-integration` job from `.github/workflows/build-and-test.yml` and the "sipi/ingest/fuseki images" wording; rename the "dsp-api/dsp-ingest/sipi/fuseki" step in `.github/workflows/docker-publish.yml`
- [x] Remove `//modules/ingest:__pkg__` from the `runtime_fs_*` visibility in `modules/sipi/BUILD.bazel`

Checks:

- [x] Run `git grep -n -e "ingest_classes" -e "dsp-ingest:latest" -e "test-ingest-integration" -e "swiss.dasch.Main" -- ':!docs/specs' ':!*CHANGELOG.md'`; it returns only documentation updated in Phase 6
- [x] Run `bazel build //...`; it builds
- [x] Run `just test-unit`; it passes (includes `//modules/ingest:test`)
- [x] Run `just test-it`; it passes
- [x] Run `just test-e2e`; it passes, including `EndpointsE2ESpec` (every endpoint declares a method) and the project export and import round trips (1025/1026; `ValueHasXmlOnValueWriteE2ESpec` "6000 standoff tags" times out on the phase base too, not from this diff)
- [x] Run `just stack-start`, then upload an image with `curl` to `http://localhost:3333/ingest/projects/0001/assets/ingest/<name>` with a login token and read `GET /ingest/projects/0001/assets/<id>`; both succeed
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 4: Contain ingest work

- [x] Add `modules/ingest/.../infrastructure/IngestExecutor.scala` (port of `IngestExecutor` from `11c2df207`, without the nested-permit `FiberRef` guard): a scoped layer over `ExecutorConfig` with a fixed pool of `ingest-<n>` daemon threads, shut down on release, a `Semaphore` with as many permits, `run` (`onExecutor`) and `withPermit`; log the size once
- [x] Take a permit around the process in `CommandExecutorLive.execute`, and, when `use-local-dev` is false, start the process as `/bin/sh -c 'echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"' <command> <params...>` (port of `cfa61c4f9`)
- [x] Take a permit around BagIt creation in `ProjectService.zipProject` and around `BagIt.readAndValidateZip` and zip extraction in `ImportService.importZipFile` (port of `7ef3dd29c`; owner decision 2026-10-27: export and import are capped too)
- [x] Add an `IngestServiceSpec` (or `ProjectServiceSpec`/`ImportServiceSpec`) case with an `IngestExecutor` of one permit: an image ingest, a project export and a project import each complete within a timeout (no nested-permit deadlock)
- [x] Provide an `IngestExecutor` in the ingest specs that build `CommandExecutorLive` (`CommandExecutorLiveSpec`, `ProjectsEndpointSpec`, `IngestServiceSpec`)
- [x] In `IngestServerEndpoints`, run the server logic of every endpoint except monitoring's on the ingest pool (rebuild each as `ServerEndpoint(se.endpoint, se.securityLogic, m => p => i => executor.run(se.logic(m)(p)(i)))`); run `DspIngestClientLive`'s export, import and erase with `IngestExecutor.run`; add `IngestExecutor.layer` to `IngestModule`
- [x] Add `IngestExecutorSpec`: work in `run` executes on an `ingest-` thread, a `forkDaemon` inside `run` does too, and no more than the permit count of `withPermit` sections run at once
- [x] Extend `CommandExecutorLiveSpec`: the wrapper is present outside local-dev mode and absent inside it; a Linux-only case (`TestAspect.ifProp("os.name", _.contains("Linux"))`) prints `1000` for `cat /proc/self/oom_score_adj`
- [x] Add an `IngestServerEndpointsSpec` case: a bulk-ingest request's forked job runs on an `ingest-` thread, and `/ingest/health` does not
- [x] Add an `IngestServiceSpec` case where a failing `SipiClient` fails only that ingest and a concurrent one succeeds, and a `BulkIngestServiceSpec` case where it is counted in `IngestResult.failed` and the run continues (REQ-3.3)
- [x] Run `bazel test //modules/ingest:test //modules/webapi:test`; both pass
- [x] Run `just test-e2e`; it passes (all but the pre-existing `ValueHasXmlOnValueWriteE2ESpec` "6000 standoff tags" timeout)
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 5: Emit new original-file links on the API host

- [ ] Change `ResourceMetadataRules.originalFileUrl` to take the API's external base URL and build `<base>/ingest/projects/<shortcode>/assets/<assetId>/original`
- [ ] Pass `KnoraApi.externalKnoraApiBaseUrl` from `slice/resources/service/FairResourcesLive.scala` (replacing `ingestConfig.externalBaseUrl`) and drop its `DspIngestConfig` dependency
- [ ] Pass `KnoraApi.externalKnoraApiBaseUrl` from `slice/export/api/service/ExportService.scala` (`fileLinkOf`) instead of `appConfig.dspIngest.externalBaseUrl`
- [ ] Remove `externalBaseUrl` from `DspIngestConfig` and `external-base-url` from `application.conf`
- [ ] Update `FairResourcesLiveSpec`, `ExportServiceSpec` and any golden files they assert; the golden diff only changes the link host and path
- [ ] Update the E2E specs asserting original-file links (`MetadataEndpointsE2ESpec`, FAIR E2E specs)
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Run `just test-e2e`; it passes
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 6: Update documentation and the architecture map

- [ ] Add one `docs/03-endpoints/ingest.md` page (the `/ingest/` prefix, the `ingest.<domain>` compatibility note, `Content-Length` for uploads over 1 MiB, a pointer to the endpoints in the API docs) and add it to the `nav` in `mkdocs.yml`
- [ ] Document the `INGEST_*` settings, the reduced `KNORA_WEBAPI_DSP_INGEST_*` settings, the read-write image/temp mounts, the root user requirement and how to run `swiss.dasch.MigrateSizes`/`MigrateMimeTypes` in the api container in `docs/04-publishing-deployment/configuration.md`
- [ ] Update the passages in `docs/05-internals/development/third-party.md`, `docs/03-endpoints/api-v3/project-migration.md`, `docs/03-endpoints/api-v3/project-data-import.md`, `docs/03-endpoints/api-v2/editing-values.md`, `docs/03-endpoints/api-admin/overview.md` and `docs/03-endpoints/api-admin/files.md` that describe dsp-ingest as a separate service or URL
- [ ] Update `modules/ingest/README.md`, `modules/ingest/CLAUDE.md` and `modules/ingest/docs/` where they describe the standalone service (it is now a library served by dsp-api under `/ingest/`); keep `docs-ingest-build` only if the ingest docs site is still published
- [ ] Update `CLAUDE.md`, `CONVENTIONS.md`, `docs/contexts/assets/CONTEXT.md`, `docs/development/dsp-api-rbe.md`, `flake.nix` and `tools/oci/defs.bzl` comments that name the dsp-ingest image or service
- [ ] Run `dune:dune-map` to refresh the stale `ARCH-MAP.md` entries (ingest becomes a library of the API deployable; the `DspIngestClient` "remote-owned" notes change)
- [ ] In that refresh, record `//modules/webapi` as a build-time consumer of `//modules/sipi:runtime_fs_*` (the sipi component's "Used by" and its visibility boundary rule, and the webapi image entry)
- [ ] Load `eng:grafana-dashboards` and remove or re-point the dsp-ingest panels in `grafana-dashboards/dsp-api/dsp-backend.json`, since ingest no longer has its own JVM or service label
- [ ] Run `git grep -n -e "dsp-ingest:latest" -e "test-ingest-integration" -e "ingest:3340" -- ':!docs/specs' ':!*CHANGELOG.md'`; it returns nothing
- [ ] Run `grep -rn "docs/specs/" --exclude-dir=specs docs CLAUDE.md CONVENTIONS.md ARCH-MAP.md`; it returns nothing
- [ ] Run `just docs-build`; it succeeds
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Deploy the dsp-api release containing `/ingest/` to each environment before ops-deploy's cutover (proxy `ingest.<domain>` to `/ingest/`, removal of the ingest service) for that environment | Release owner / ops | after ship | Releasing and deploying are the owner's decision and need deployment credentials |

## Acceptance Criteria

- [ ] Every standalone ingest route answers under `/ingest/` through ingest's own handlers, verified by `IngestServerEndpointsSpec`, ingest's unchanged endpoint specs and `IngestEndpointsE2ESpec` (REQ-1.1, REQ-1.2)
- [ ] `POST /ingest/maintenance/ImportProjectsToDb` is `400`, and `/ingest/health` has no database entry (REQ-1.2)
- [ ] Tokens from `JwtService.createJwt` and `createJwtForDspIngest` are accepted with the same scopes; a foreign audience or issuer, or no subject, is `401` (REQ-1.3, REQ-1.4)
- [ ] A secured `/ingest/` route without a token answers `401` (REQ-1.4)
- [ ] An upload above 1 MiB with `Content-Length` streams to storage and keeps its checksum (REQ-1.5)
- [ ] FAIR metadata and export file links use `<api external base URL>/ingest/projects/.../original` (REQ-2.3)
- [ ] `/ingest/health` answers `200` while the asset and temp directories are usable (REQ-2.4)
- [ ] Ingest request handling (except info, health, metrics), export, import, erase and forked ingest jobs run on `ingest-` threads, and child processes are bounded by the executor's permits (REQ-3.1, REQ-3.2)
- [ ] A failing Sipi/ffmpeg/ffprobe process fails only its request or bulk item (REQ-3.3)
- [ ] Child processes in the image start with `oom_score_adj` 1000 (REQ-3.4)
- [ ] dsp-api reads ingest settings only from `ingest.conf`/`INGEST_*`, and no key or variable is read by both configurations (REQ-4.1)
- [ ] dsp-api starts with no database setting and no SQLite, Flyway or HikariCP on the classpath (REQ-4.2)
- [ ] `DspIngestClient` and `FetchAssetPermissions` have no HTTP implementation and dsp-api has no ingest base URL setting (REQ-4.3)
- [ ] No Bazel target, just recipe or workflow builds or publishes `daschswiss/dsp-ingest`, and `docker-compose.yml` has no ingest service (REQ-4.5)
- [ ] `just test-unit`, `just test-it`, `just test-e2e` and `just check` pass on the final commit

## Dependencies & Risks

**Cross-repo contracts this plan fixes** (the ops-deploy, dsp-app and dsp-tools plans must match):

- The api container needs `INGEST_STORAGE_ASSET_DIR`, `INGEST_STORAGE_TEMP_DIR` (read-write mounts), and
  optionally `INGEST_BULK_MAX_PARALLEL`, `INGEST_ALLOW_ERASE_PROJECTS`, `INGEST_EXECUTOR_CPU_SHARE`. It must not set
  `INGEST_SIPI_USE_LOCAL_DEV=true`. It keeps `KNORA_WEBAPI_DSP_INGEST_AUDIENCE` and drops
  `KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `KNORA_WEBAPI_DSP_INGEST_EXTERNAL_BASE_URL` and
  `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR`. The old unprefixed ingest variables (`STORAGE_*`, `SIPI_USE_LOCAL_DEV`,
  `JWT_*`, `DB_JDBC_URL`, `DSP_API_URL`, `SERVICE_*`) are ignored. `ALLOW_ERASE_PROJECTS` still drives dsp-api's
  own feature flag only.
- Health: `GET /ingest/health` on the API port (3333), `200` when healthy. Metrics: `GET /ingest/metrics` returns
  the whole API process's metrics, identical to `:3339/metrics`. Scraping both double-counts.
- The image runs as root and contains `/sbin/sipi`, `/usr/bin/ffmpeg`, `/usr/bin/ffprobe`, `/bin/sh`.
- The proxy forwards `ingest.<domain>/<path>` to `/ingest/<path>` unchanged, including `Authorization` and
  `Content-Length`. Uploads without `Content-Length` over 1 MiB get `413` from dsp-api (see risks).
- Ingest's Swagger UI (`ingest.<domain>/docs`) is not carried over; ingest endpoints appear in dsp-api's API
  docs served by the metrics server.
- Child processes inherit the api container's environment (`scala.sys.process` passes it on and the
  `sh -c '… exec "$0" "$@"'` wrapper keeps it), so `SIPI_SENTRY_*` set on `api` reach the Sipi CLI as they reached
  it in the ingest container.
- The size and MIME-type backfills stay main classes (`swiss.dasch.MigrateSizes`, `swiss.dasch.MigrateMimeTypes`)
  on the dsp-api image's classpath (`java -cp '/opt/docker/lib/*' ...`), reading `INGEST_*`. The ops-deploy
  playbooks INFRA-1404/1413 that ran them in the ingest container are not re-run.

**Resolved decisions:**

- 2026-10-27: minimal diff; `modules/ingest` keeps its structure and package (see Revisions).
- Scope refusals answer `401` with the standalone bodies; `/ingest/metrics` stays public; logged-out tokens are still
  accepted; all three follow from reusing ingest's handlers and `AuthService` unchanged.
- In-process `importProject` keeps the HTTP client's log-only failure handling (Phase 3).
- The backfills stay `scala_binary` mains of the library, not maintenance actions.

**Follow-ups** (out of scope; name them in the execution's final report):

- `DspIngestClient.importProject` now runs in-process, so a failed asset import could fail the project-import
  request instead of only being logged. Changing it is a behaviour change for project import and needs its own
  decision.

**Ordering:** H1 precedes the ops-deploy cutover; dsp-tools merges its `/ingest/` change only once prod serves
`/ingest/` (PRD constraint). dsp-app may switch once the environment it targets runs this release.

**Open questions** (defaults chosen; revisit in plan review):

- ffmpeg and the Sipi CLI use several threads per process, so permits bound the process count, not cores. Default:
  no `-threads` cap (it can change encoder output).

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Chunked uploads (no `Content-Length`) over 1 MiB get a bare `413` under Hybrid streaming, where the standalone service streamed them | M | H | Phase 3 E2E covers the declared-length case; dsp-app and dsp-tools plans confirm their uploads send `Content-Length`; documented in Phase 6 |
| Forked jobs or blocking work escape the ingest executor and compete with API traffic | M | M | Permits around child processes; `IngestExecutorSpec` asserts executor inheritance for forked fibers; permits around BagIt zip and unzip |
| A missing or read-only ingest mount breaks ingest routes | L | H | `/ingest/health` reports `503`; dsp-api itself starts (the standalone smoke test is not carried over); ops mounts are a cross-repo contract |
| The JVM and conversions share the container memory; conversions trigger the OOM killer | M | M | `oom_score_adj` 1000 on children; ops raises the memory limit by ingest's 2G with JVM settings unchanged |
| Larger dsp-api image (Sipi tree, ffmpeg) slows pulls and CI | H | L | Measured against the baseline in Phase 2; accepted cost of one container |
| Ingest's layer graph clashes with webapi's (two `AssetInfoService` providers, same-named config types) | M | L | `IngestModule` builds ingest internally and exposes three types; `AssetInfoServiceLayer` is deleted in the same commit |
| Dashboards or alerts in ops keyed to the ingest service name go blank | M | L | Phase 6 updates the in-repo dashboard; ops-deploy plan covers its alerts |

## Success Metrics

- **Diff budget.** `git diff -M --shortstat origin/main...HEAD -- . ':!docs/specs' ':!maven_install.json'` on the
  final commit stays within +1,500/−2,500 lines. Per phase (estimates):

  | Phase | Added | Removed |
  | --- | --- | --- |
  | 1 SQLite (done; measured, `maven_install.json` adds −221) | 71 | 482 |
  | 2 Image | 10 | 5 |
  | 3 Serve under `/ingest/`, drop standalone | 900 | 1,350 |
  | 4 Containment | 220 | 10 |
  | 5 Links | 40 | 40 |
  | 6 Docs and map | 200 | 200 |
  | Total | about 1,450 | about 2,100 |

  A phase that overshoots its estimate by more than half stops for an owner check before the next phase.
- Image growth after Phase 2 is within the size of the `runtime_fs` tree (`/sbin/sipi`, `/sipi`, ffmpeg, ffprobe,
  tini).
- Every route of the standalone OpenAPI (`ProjectsEndpoints`, `MaintenanceEndpoints` minus `ImportProjectsToDb`,
  `ReportEndpoints`, `MonitoringEndpoints`) has a matching `/ingest/` route in dsp-api's generated docs.
- No `swiss.dasch` file is moved or renamed, except `application.conf` -> `ingest.conf`.

## References

- PRD: `01-move-ingest-into-webapi-PRD.md`; brief: `00-project-brief.md`
- Discarded shape: branch `backup/docs/move-ingest-into-webapi-brief` (`11c2df207`); executor commits
  `e19fe71fd`..`e66a12069`
- Ingest handlers and bases: `modules/ingest/src/main/scala/swiss/dasch/api/` (`*EndpointsHandler.scala`,
  `BaseEndpoints.scala`, `AuthService.scala`, `AuthorizationHandler.scala`, `RequiresMethod.scala`)
- Ingest config and wiring: `modules/ingest/src/main/scala/swiss/dasch/config/Configuration.scala`,
  `modules/ingest/src/main/resources/application.conf`, `modules/ingest/src/main/scala/swiss/dasch/Main.scala`
- Child processes: `modules/ingest/src/main/scala/swiss/dasch/infrastructure/CommandExecutorLive.scala`
- Callback to dsp-api: `modules/ingest/src/main/scala/swiss/dasch/FetchAssetPermissions.scala`;
  `modules/webapi/src/main/scala/org/knora/webapi/slice/api/admin/FilesServerEndpoints.scala`,
  `responders/admin/AssetPermissionsCache.scala`, `slice/security/Authenticator.scala`
- webapi side: `slice/api/Endpoints.scala`, `core/DspApiServer.scala`, `core/LayersLive.scala`,
  `slice/admin/domain/service/DspIngestClient.scala`, `slice/infrastructure/JwtService.scala`,
  `store/iiif/impl/AssetInfoServiceLayer.scala`, `slice/resources/service/FairResourcesLive.scala:57`,
  `slice/export/api/service/ExportService.scala:151`, `slice/resources/domain/ResourceMetadataRules.scala:62`,
  `config/AppConfig.scala` (`DspIngestConfig`)
- tapir: `ServerEndpoint.prependSecurityIn` (tapir-core 1.13.25, `sttp/tapir/server/ServerEndpoint.scala`)
- Build, stack, CI: `modules/ingest/BUILD.bazel`, `modules/webapi/BUILD.bazel`, `modules/sipi/BUILD.bazel`,
  `justfile`, `docker-compose.yml`, `.github/workflows/build-and-test.yml`, `.github/workflows/docker-publish.yml`
- Conventions: `CONVENTIONS.md`, `docs/development/dsp-api-conventions.md`,
  `docs/adr/0011-cross-context-access-ports-and-adapters.md`
- Learning: `dasch-specs/learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`
