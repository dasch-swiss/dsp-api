---
title: "feat: Ingest runs inside dsp-api under /ingest/"
type: feat
date: 2026-10-26
author: "Raitis Veinbahs"
status: reviewed
prd: 01-move-ingest-into-webapi-PRD.md
---

# feat: Ingest runs inside dsp-api under /ingest/

## Overview

dsp-ingest (`modules/ingest`, package `swiss.dasch`) becomes the `slice/ingest/` slice of the webapi module
(`org.knora.webapi.slice.ingest`), served by dsp-api under `/ingest/`. Its SQLite database goes away, its
configuration stays separate inside dsp-api, its work runs on a dedicated executor, and its child processes
(Sipi CLI, ffmpeg, ffprobe) ship in the dsp-api image and are the OOM killer's first choice. dsp-api's own HTTP
calls to ingest become in-process calls, new original-file links point at the API host, and the standalone
application, its image, its publishing and its local-stack container are removed.

This plan covers dsp-api only. The proxy, deployment and client changes are separate plans in ops-deploy,
dsp-app and dsp-tools (see Dependencies & Risks).

## Problem Statement / Motivation

See the PRD. In short: two services to build, deploy, configure and monitor, two token validators, a second
public address for clients, and a SQLite database that nothing reads to decide behaviour. dsp-api already
reaches into ingest's code (`//modules/webapi` depends on `//modules/ingest:ingest_classes` for
`AssetInfoService`) and over HTTP (`DspIngestClient`), and ingest calls back into dsp-api over HTTP
(`FetchAssetPermissions` -> `GET /admin/files/...`). The split costs more than it isolates.

## Proposed Solution

1. **SQLite goes first, in the standalone module.** The one write-only `project` table, its repository, the
   Flyway migration, the DB health indicator and the `ImportProjectsToDb` maintenance action are removed while
   the code still lives in `modules/ingest`, so the move carries no database code.
2. **Domain moves, API is rebuilt.** Domain, storage and process code move by `git mv` into
   `modules/webapi/src/main/scala/org/knora/webapi/slice/ingest/` with a package rename and nothing else. The
   HTTP layer is not moved: it is rebuilt on webapi's three-tier split (`*Endpoints` / `*ServerEndpoints` /
   `*RestService`), webapi's `RequiresMethod` endpoint bases and webapi's JWT settings. Until the standalone
   module is deleted, `//modules/ingest` keeps its own `Main`, server and API handlers and depends on
   `//modules/webapi` for the moved domain, so it stays buildable and its tests keep running.
3. **Auth maps onto webapi's JWT, not onto webapi's user lookup.** Ingest's acceptance rule is kept exactly:
   HS256 signature with dsp-api's secret, `iss` equal to dsp-api's issuer, `aud` containing the ingest audience,
   `sub` present, optional `scope` claim. The scope vocabulary (`admin`, `read:project:XXXX`,
   `write:project:XXXX`) is already webapi's `Scope`/`ScopeValue` (`slice/infrastructure/Scope.scala`), which is
   what dsp-api mints, so the slice reuses that type and adds the parser and the three checks ingest's
   `AuthScope` had. webapi's `BaseEndpoints.securedEndpoint` is not used as is: it resolves a `User` from the
   subject, accepts HTTP basic and consults the logout cache, so it would reject tokens ingest accepts (e.g. the
   service token minted by `JwtService.createJwtForDspIngest`, whose subject is the issuer, not a user IRI) and
   it would change error bodies. That breaks REQ-1.2/REQ-1.3.
4. **Separate configuration.** Ingest's settings live in their own resource, `ingest.conf`, read by their own
   loader into slice-local case classes. webapi keeps reading `ConfigFactory.load().getConfig("app")`; no key
   and no environment variable is read by both. Every ingest environment variable gets an `INGEST_` prefix,
   because the unprefixed names would collide (ingest's `ALLOW_ERASE_PROJECTS` is also webapi's
   `app.features.allow-erase-projects`).
5. **Contained workload.** An `IngestExecutor` service owns a fixed thread pool sized
   `max(1, floor(availableProcessors * cpu-share))` and a semaphore with the same number of permits. Ingest
   request handling and ingest jobs run `onExecutor` it; every child process and every CPU-heavy blocking call
   (checksums, BagIt zip and unzip) takes a permit, because `attemptBlocking*` shifts to ZIO's unbounded
   blocking pool, which the executor alone would not bound. Child processes start through
   `/bin/sh -c 'echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"'` so the kernel OOM killer picks them
   before the JVM.
6. **In-process calls both ways.** webapi's `DspIngestClient` trait stays as the seam its consumers
   (`ProjectEraseService`, `ProjectMigrationExportService`, `ProjectMigrationImportService`, `SipiServiceLive`)
   already use; the HTTP `DspIngestClientLive` is replaced by an in-process adapter in the ingest slice. Ingest's
   `FetchAssetPermissions` becomes a consumer-defined port (`slice/ingest/ports/`) implemented by the admin side
   over `AssetPermissionsCache`, per ADR-0011. The adapter `OriginalAccessLive` lives in
   `slice/admin/domain/service/` (ADR-0011 rule 3). Keeping `DspIngestClient` in `slice/admin/domain/service/`
   and implementing it from the ingest slice is a deliberate deviation from ADR-0011 rule 5 (ingest depends on
   admin's domain package, not a `ports` package): moving the seam would touch four consumers in three slices for
   no behaviour change, and belongs to the modularization work that extracts these contexts.
7. **Image and removal.** The dsp-api image layers `//modules/sipi:runtime_fs_{amd64,arm64}` (it already runs
   as root, which ingest needs for NFS-owned assets). Then `modules/ingest`, `modules/test-ingest-integration`,
   the ingest image, its just recipes, CI jobs and the compose service are deleted.

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
(`KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `..._EXTERNAL_BASE_URL`, `..._ASSET_DIR`) are removed, and
`AssetInfoServiceLayer` reads the asset directory from the ingest configuration.

`use-local-dev` now defaults to `false` (the standalone default was `true`, overridden in every container), so a
deployment that forgets the variable runs the binaries in the image rather than trying `docker run`. Local runs
outside a container (`bazel run //modules/webapi:app`, the testkit) set it to `true`.

## Alternative Approaches Considered

- **Use webapi's `securedEndpoint` and `User` for ingest routes.** Rejected for the reasons in Proposed
  Solution 3. A later change may tighten ingest to user-resolved tokens; that is a contract change for clients
  and belongs in its own PRD.
- **Unify ingest's refined value types (`ProjectShortcode`, `AssetId`, `AssetFilename`) with webapi's
  `KnoraProject.Shortcode` etc. during the move.** Rejected for this plan: their decode-failure messages differ,
  and they appear in `400` bodies, so swapping them changes responses (REQ-1.2). They move unchanged.
- **Move the API handlers by `git mv` too.** Rejected: they are rewritten for the three-tier split and the new
  bases, so a move would hide a rewrite inside a rename, and the standalone module would stop building three
  phases before it is deleted. The rebuilt endpoints are new files; the old ones die with the module.
- **Top-level `ingest { }` block inside webapi's `application.conf`.** Workable (webapi only reads `app`), but a
  separate `ingest.conf` makes the "no shared key" rule visible in the file layout.

## Technical Considerations

- **Slice layout** (CLAUDE.md, CONVENTIONS.md): `slice/ingest/api/` (endpoints, server endpoints, rest
  services, DTOs, `IngestBaseEndpoints`, `IngestAuthenticator`), `slice/ingest/domain/` (value types, models,
  `IngestService`, `BulkIngestService`, `ImportService`, `ProjectService`, `ReportService`, `MaintenanceActions`,
  `StillImageService`, `MovingImageService`, `OtherFilesService`, `CsvService`, migration services),
  `slice/ingest/repo/` (`StorageService`, `AssetInfoService`, `FileChecksumService`),
  `slice/ingest/infrastructure/` (`CommandExecutor`, `SipiClient`, `IngestExecutor`, health indicators; precedent
  `slice/fair/infrastructure/`), `slice/ingest/config/IngestConfig.scala`, `slice/ingest/ports/OriginalAccess.scala`,
  plus `IngestModule` (domain layer) and `IngestApiModule` (api layer) with `Dependencies`/`Provided` types like
  `ExportModule`. Existing classes keep their trait + `*Live` shape where they have one; new services follow
  `final class X(...)` + `ZLayer.derive`.
- **Error envelope.** Ingest's `ApiProblem` variants (`400/401/403/404/409/500/503` JSON) are declared on the
  ingest bases only. Do not add them to `BaseEndpoints.errorOutputs` (CONVENTIONS.md, "Never add a variant to the
  shared `BaseEndpoints.errorOutputs`").
- **Security input.** Bearer only: no basic auth and no cookie, the same as the standalone service. That also keeps
  the routes CSRF-safe under webapi's credentialed, reflected-origin CORS.
- **Body buffering.** webapi runs `RequestStreaming.Hybrid(1 MiB)` (`core/DspApiServer.scala`): bodies with a
  declared `Content-Length` above 1 MiB stream; bodies up to 1 MiB, and every chunked body, are aggregated before
  tapir runs, for any caller, and a chunked body over the threshold gets a bare `413` from Netty. The standalone
  service streamed everything. See
  `dasch-specs/learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`: the
  security logic authenticates before body decode, but buffering happens earlier still. No comment or doc may
  claim that only authenticated callers cause buffering.
- **Executor scope.** Request handling for every ingest endpoint except info, health and metrics runs on the
  ingest executor, as do the export, import and erase adapters and the forked jobs (bulk ingest, maintenance
  actions, asset-overview report). `getAssetInfo` for value creation and the FAIR/export sidecar reads stay on
  the default executor: they are short file reads serving non-ingest requests (REQ-3.2). Forked daemon fibers
  must be checked by a test to still run on the ingest executor. If they do not inherit it, wrap the forked body
  explicitly.
- **IRI/value handling.** Ingest's paths carry shortcodes, asset ids and filenames, not IRIs. Path decoding stays
  in tapir codecs (a malformed value is a `400`). `cpu-share` is validated when the configuration loads and fails
  startup if out of range; it is an operator error, not a request error.
- **Observability.** Ingest spans now come from the API's server span and `spanNameInterceptor`, so routes show
  as `GET /ingest/projects/{shortcode}/...` under the API's service name. If ingest-specific spans are added
  (e.g. around conversions), follow `docs/observability/instrumentation-recipe.md` (bounded names, `UNSET`
  status mapper, no file names or shortcodes in status descriptions).
- **Metrics.** `/ingest/metrics` serves the API process's `PrometheusPublisher`, which is the same data as the
  metrics server's `/metrics` on port 3339, because there is only one JVM.
- **Security review points.** The original-download route (`withUserEndpoint`) passes an optional token; an
  invalid token is anonymous for the endpoint, and the in-process permission check authenticates it with webapi's
  `Authenticator`. A failure there answers `500 "error fetching permissions"`, as it does today when dsp-api returns
  `401`. Erase stays gated by the ingest-side `allow-erase-projects` flag and admin scope.

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

### Phase 2: Move ingest domain code into the webapi slice

- [x] `git mv` the domain files from `modules/ingest/src/main/scala/swiss/dasch/domain/` into `modules/webapi/src/main/scala/org/knora/webapi/slice/ingest/{domain,repo}/` per the layout in Technical Considerations, changing only `package` and `import` lines, as its own commit
- [x] `git mv` `infrastructure/CommandExecutorLive.scala`, `Base62.scala`, `Health.scala`, `AggregatedHealth.scala`, `HealthCheckService.scala` and `FileSystemHealthIndicator.scala` into `slice/ingest/infrastructure/`, same rule, same commit
- [x] `git mv` `api/ApiProblem.scala` into `slice/ingest/api/`, same rule, same commit: the moved `BulkIngestService` imports `ApiProblem` and `ApiProblem.BadRequest` (`domain/BulkIngestService.scala:8-9`)
- [x] Move `ProjectsEndpointsResponses.UploadResponse` (from `api/ProjectsEndpoints.scala`) into `slice/ingest/api/UploadResponse.scala` and `ActionName` (from `api/MaintenanceEndpoints.scala`) into `slice/ingest/domain/ActionName.scala`, same commit: the moved `BulkIngestService` (`:10`) and `MaintenanceActions` (`:8`) import them, and webapi cannot depend on `//modules/ingest`
- [x] Point the standalone module's `api/` and `Main` at the moved `ApiProblem`, `UploadResponse`, `ActionName` and `AuthScope` (no copies remain in `modules/ingest`)
- [x] `git mv` the matching unit tests from `modules/ingest/src/test/scala/swiss/dasch/{domain,infrastructure}/` into `modules/webapi/src/test/scala/org/knora/webapi/slice/ingest/...`, same commit
- [x] Copy the test helpers the moved specs use (`test/SpecConfigurations.scala`, `test/SpecPaths.scala`, `test/SpecConstants.scala`, `util/TestUtils.scala`, `api/SipiClientMock.scala`, `infrastructure/CommandExecutorMock.scala`) into `modules/webapi/src/test/scala/org/knora/webapi/slice/ingest/testutil/`, adapted to the slice config types; the originals stay for the standalone API specs until Phase 8
- [x] Copy the test resources (`test-folder-structure/`, `test-import.zip`, `test.txt`) to `modules/webapi/src/test/resources/org/knora/webapi/slice/ingest/` and point the webapi copy of `SpecPaths` at the new classpath location (the standalone copies stay until Phase 8)
- [x] Add `slice/ingest/config/IngestConfig.scala`: case classes `StorageConfig`, `SipiConfig(useLocalDev, localDevImage)`, `BulkIngestConfig(bulkMaxParallel)`, `IngestFeatures(allowEraseProjects)`, `ExecutorConfig(cpuShare)` and a loader reading `ConfigFactory.parseResources("ingest.conf").resolve().getConfig("ingest")` with `deriveConfig(...).mapKey(toKebabCase)`
- [x] Add `modules/webapi/src/main/resources/ingest.conf` with the keys and `INGEST_*` env vars from the Configuration mapping table
- [x] Add an `IngestConfigSpec`: defaults load, each env override applies, `cpu-share` outside `(0, 1]` fails to load, and webapi's `AppConfig` carries no `ingest` key
- [x] Replace `swiss.dasch.version.BuildInfo.knoraSipiVersion` in `CommandExecutorLive` with `SipiConfig.localDevImage`
- [x] Point `store/iiif/impl/AssetInfoServiceLayer.scala` at the slice's `StorageConfig` from the ingest config instead of `AppConfig.dspIngest.assetDir`
- [x] Wire the ingest config layer in `core/LayersLive.scala` (`remainingLayer`'s `ZLayer.makeSome` input or a layer inside it) and in `modules/testkit/.../core/TestContainerLayers.scala`, so `AssetInfoServiceLayer`'s new `StorageConfig` requirement is met in this phase
- [x] Make the `IngestConfig` loader take the `Config` to read (default: `ConfigFactory.parseResources("ingest.conf")` resolved with system environment) so `IngestConfigSpec` can supply env overrides through `resolveWith` instead of the process environment
- [x] Update the `swiss.dasch` imports in `slice/export/api/ExportApiModule.scala`, `slice/export/api/service/ExportService.scala`, `store/iiif/impl/FairAssetsLive.scala` and their specs
- [x] Remove `//modules/ingest:ingest_classes` (and its comment) from `//modules/webapi:webapi` deps and delete the `ingest_classes` target
- [x] Make `//modules/ingest:ingest` depend on `//modules/webapi` and keep `Main`, `api/`, `IngestApiServer`, `Logger`, `Metrics`, `OtelSetup` and the two migration mains compiling against the moved classes, with `Configuration.scala` still reading its own `application.conf` into the moved config types (add `local-dev-image` to it)
- [x] Make the standalone `Configuration.layer` read its own keys from a classpath that now also carries webapi's `application.conf` (`ConfigFactory.defaultApplication()` merges both; the `ingest_classes` comment in `modules/ingest/BUILD.bazel` and `AssetInfoServiceLayer.scala:20-22` describe the collision), and add a standalone `ConfigurationSpec` case that loads the standalone keys with webapi on the classpath
- [x] Run `bazel test //modules/webapi:test`; it passes, including the moved ingest specs
- [x] Run `bazel test //modules/ingest:test`; it passes
- [x] Run `just test-ingest-integration`; it passes against the standalone image built with webapi on its classpath
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 3: Serve the ingest endpoints under /ingest/ with dsp-api's authentication

- [x] Add `Scope.parse(String): Either[String, Scope]` (inverse of `toScopeString`, unknown values ignored, invalid shortcode a failure) and `hasAdmin`, `projectReadable`, `projectWritable` to `slice/infrastructure/Scope.scala`, with `ScopeSpec` cases ported from ingest's `AuthScopeSpec`
- [x] Replace ingest's `AuthScope` usages in the slice's new code with webapi's `Scope`; leave `AuthScope` in the slice (the standalone `api/` still uses it) and delete it in Phase 8
- [x] Add `slice/ingest/api/IngestAuthenticator.scala`: decode with `JwtCodec` and `JwtConfig.secret`, require `iss == JwtConfig.issuer`, `aud` containing `DspIngestConfig.audience` and `sub`, parse `scope`, and return all failures together with ingest's existing messages
- [x] Add `IngestAuthenticatorSpec` with the cases of ingest's `AuthServiceLiveSpec`: valid token, wrong audience, wrong issuer, missing subject, bad signature, expired, scope parsing, plus a token from `JwtService.createJwt` and a service-shaped token (subject = issuer, audience = ingest audience only, `admin` scope) built in the spec with `JwtCodec`, so the case survives the removal of `createJwtForDspIngest` in Phase 5
- [x] Add `slice/ingest/api/IngestBaseEndpoints.scala`: `publicEndpoint`, `withPrincipalEndpoint` (optional bearer, invalid token -> anonymous) and `securedEndpoint` (bearer required, `401` otherwise), all under the `ingest` path prefix and returning webapi's `RequiresMethod`, with ingest's `ApiProblem` error outputs declared here
- [x] Add `IngestProjectsEndpoints`, `IngestProjectsServerEndpoints` and `IngestProjectsRestService` covering every route in ingest's `ProjectsEndpoints` with identical inputs, outputs, examples and descriptions
- [x] Add `IngestMaintenanceEndpoints`, `IngestMaintenanceServerEndpoints` and `IngestMaintenanceRestService` (actions `ApplyTopLeftCorrection`, `UpdateAssetMetadata`, plus the two `GET` checks; no `ImportProjectsToDb`)
- [x] Add `ActionName.MigrateSizes` and `ActionName.MigrateMimeTypes`, dispatched by `IngestMaintenanceRestService` to `AssetSizeMigrationService` / `AssetMimeTypeMigrationService` (`migrateAll` without `restrictToProjects`, `migrateProject` per listed shortcode), forked like the other actions, logging the migration report and its failure count; this replaces the standalone `MigrateSizes` / `MigrateMimeTypes` mains
- [x] Add `IngestMaintenanceEndpointsSpec` cases: `MigrateSizes` and `MigrateMimeTypes` answer `202` for an admin token and run the matching service (restricted to the given projects), and answer `401` with ingest's body without admin scope
- [x] Port the scope decisions of ingest's `api/AuthorizationHandlerSpec` (admin, project read, project write, `401` refusal bodies) into an `IngestAuthorizationSpec` over the checks the new rest services use
- [x] Add `IngestReportEndpoints`, `IngestReportServerEndpoints` and `IngestReportRestService` (`POST /ingest/report/asset-overview`)
- [x] Add `IngestMonitoringEndpoints` and `IngestMonitoringServerEndpoints` for `/ingest/info`, `/ingest/health` (`200` healthy, `503` with the `ApiProblem.Unhealthy` body otherwise) and `/ingest/metrics` (the API's `PrometheusPublisher`)
- [x] Add a `gitCommit` stamp field (`STABLE_GIT_COMMIT`) to the webapi `buildinfo` in `modules/webapi/BUILD.bazel` and build the `/ingest/info` response from webapi's `BuildInfo` with `name = "dsp-ingest"` and `sbtVersion = "bazel"`, so the body keeps its fields
- [x] Add `slice/ingest/ports/OriginalAccess.scala` (`isOriginalGranted(token: Option[String], shortcode, filename): Task[Boolean]`) and use it in the original-download rest method instead of `FetchAssetPermissions`
- [x] Add `OriginalAccessLive` on the admin side in `slice/admin/domain/service/` (ADR-0011 rule 3), over `AssetPermissionsCache` (`responders/admin/`): authenticate a present token with `Authenticator.authenticate`, fall back to `AnonymousUser` when absent, and return `getAssetAccess(user)(shortcode, filename).original` is granted
- [x] Add `OriginalAccessInMemory` under the ingest slice's tests and `OriginalAccessLiveSpec` (granted, denied, anonymous, invalid token fails)
- [x] Add `IngestServerEndpoints` aggregating the four groups and register it in `slice/api/Endpoints.scala`
- [x] Add `IngestModule` and `IngestApiModule` and wire them and `OriginalAccessLive` in `core/LayersLive.scala`
- [x] Delete `store/iiif/impl/AssetInfoServiceLayer.scala` and its `LayersLive` entry: `IngestModule` becomes the one provider of `AssetInfoService` for export and FAIR (two providers are an ambiguous-layer error in `ZLayer.makeSome`)
- [x] Run the file-system smoke test at API startup as a logged error, not `orDie`, so a missing ingest mount degrades `/ingest/health` instead of stopping dsp-api
- [x] Port ingest's `ProjectsEndpointSpec`, `MaintenanceEndpointsSpec` and `MonitoringEndpointsSpec` to the new endpoints under `modules/webapi/src/test/scala/org/knora/webapi/slice/ingest/api/`, sending tokens from `SpecJwtTokens` built with webapi's JWT settings
- [x] Set the testkit ingest config (`core/TestContainerLayers.scala`, wired in Phase 2): asset dir = `SharedVolumes.Images` host path, temp dir = `SharedVolumes.Temp` host path, `use-local-dev = true`, `local-dev-image` = the Sipi image `SipiTestContainer` runs
- [x] Add `modules/test-e2e/src/test/scala/org/knora/webapi/slice/ingest/IngestEndpointsE2ESpec.scala`: upload an image through `POST /ingest/projects/0001/assets/ingest/{filename}` with a login token and read its asset info back
- [x] Add a case to `IngestEndpointsE2ESpec`: a request without a token answers `401`, and one with a token lacking write scope answers `401` with ingest's body
- [x] Add a case to `IngestEndpointsE2ESpec`: an upload above 1 MiB with `Content-Length` succeeds and the stored checksum matches the source
- [x] Add a case to `IngestEndpointsE2ESpec`: `/ingest/health` answers `200` and `/ingest/info` returns the documented fields
- [x] Add a case to `IngestEndpointsE2ESpec`: the original download is `200` for a permitted user and `403` for an anonymous caller on a restricted asset, using an asset whose file value exists in the loaded test data (`AssetPermissionsCache` resolves permissions from the file value by filename; a freshly uploaded asset has none)
- [x] Port `modules/test-ingest-integration/.../FileUploadSpec.scala` as a test-e2e spec against the in-process API
- [x] Run `bazel test //modules/webapi:test`; it passes
- [x] Run `just test-e2e`; it passes, including `EndpointsE2ESpec` (every endpoint declares a method)
- [x] Run `just test-it`; it passes
- [x] Run `bazel test //modules/ingest:test`; it passes
- [x] Run `just test-ingest-integration`; it passes (the standalone module and image stay green until Phase 8)
- [x] Run `just check`; it passes
- [x] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 4: Contain ingest work on a dedicated executor with expendable child processes

- [ ] Add `slice/ingest/infrastructure/IngestExecutor.scala`: a scoped layer creating a fixed pool of `max(1, floor(availableProcessors * cpuShare))` threads named `ingest-<n>`, shut down on release, plus a `Semaphore` with as many permits; expose `run[R, E, A](zio: ZIO[R, E, A])` (`onExecutor`) and `withPermit`
- [ ] Log the computed pool size once at startup
- [ ] Wrap every ingest rest-service method except info, health and metrics in `IngestExecutor.run`
- [ ] Take an `IngestExecutor` permit around `CommandExecutorLive.execute`
- [ ] Take an `IngestExecutor` permit around every checksum computation: turn the static `FileChecksumService.createSha256Hash` into a service method that takes the permit, and route its callers through it (`AssetInfoService.scala:213,248,251` and `FileChecksumServiceLive`)
- [ ] Take an `IngestExecutor` permit around BagIt creation in `ProjectService.zipProject` and around BagIt validation and zip extraction in `ImportService.importZipFile`
- [ ] Keep every permit section leaf-level (no permit section calls code that takes a permit), because ZIO's `Semaphore` is not reentrant and nested sections deadlock once all permits are held; state this rule in `IngestExecutor`'s scaladoc
- [ ] Add an `IngestServiceSpec` case with an `IngestExecutor` of one permit: an image ingest, a project export and a project import each complete within a timeout (no nested-permit deadlock)
- [ ] In `CommandExecutorLive`, when `use-local-dev` is false, start the process as `/bin/sh -c 'echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"' <command> <params...>`
- [ ] Add `IngestExecutorSpec`: work passed to `run` executes on an `ingest-` thread, a `forkDaemon` inside `run` also does, and no more than the permit count of `withPermit` sections run at once
- [ ] Add a `BulkIngestServiceSpec` case asserting the bulk-ingest fiber runs on an `ingest-` thread
- [ ] Extend `CommandExecutorLiveSpec`: the built command carries the `oom_score_adj` wrapper outside local-dev mode and not inside it
- [ ] Extend `CommandExecutorLiveSpec` with a Linux-only test (`TestAspect.ifProp("os.name", _.contains("Linux"))`) where `cat /proc/self/oom_score_adj` run through the executor prints `1000`
- [ ] Add a `StillImageServiceLiveSpec` or `IngestServiceSpec` case where a failing `SipiClient` fails only that ingest and leaves a concurrent one unaffected (REQ-3.3)
- [ ] Add a `BulkIngestServiceSpec` case where a failing `SipiClient` is counted in `IngestResult.failed` and the bulk run continues (REQ-3.3)
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Run `just test-e2e`; it passes
- [ ] Run `bazel test //modules/ingest:test`; it passes
- [ ] Run `just test-ingest-integration`; it passes (the standalone module and image stay green until Phase 8)
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 5: Replace dsp-api's HTTP calls to ingest with in-process calls

- [ ] Add `slice/ingest/domain/service/DspIngestClientLive.scala` implementing `slice/admin/domain/service/DspIngestClient` in-process
- [ ] In `DspIngestClientLive`, implement `getAssetInfo` by mapping `AssetInfoService.findByAssetRef` to `AssetInfoResponse` (`NotFoundException` when absent), on the default executor
- [ ] In `DspIngestClientLive`, implement `exportProject` as `ProjectService.zipProject` moved to `outputFile` (`None` when the project folder does not exist), on the ingest executor
- [ ] In `DspIngestClientLive`, implement `importProject` through the same `ImportService` call the import endpoint uses, on the ingest executor, logging a failed import and still succeeding with `fileToImport`, as the HTTP client did (`DspIngestClient.scala:106-115` only logs the response); see Follow-ups
- [ ] In `DspIngestClientLive`, implement `eraseProject` through the erase logic the endpoint uses, keeping the ingest `allow-erase-projects` gate and logging, not failing, when it refuses, as the HTTP client did
- [ ] Delete the HTTP `DspIngestClientLive` and its sttp backend from `slice/admin/domain/service/DspIngestClient.scala`, keeping the trait and `AssetInfoResponse`
- [ ] Wire the new `DspIngestClientLive` in `core/LayersLive.scala`
- [ ] Replace `DspIngestClientLiveSpec` with a spec of the in-process adapter covering the four methods, including not-found export and refused erase
- [ ] Add a case to the adapter spec: an `importProject` whose `ImportService` call fails logs the failure and returns `fileToImport` without failing the caller
- [ ] Remove `JwtService.createJwtForDspIngest`, its `JwtServiceLiveSpec` cases (`:116`, `:140`) and its override in the replaced `DspIngestClientLiveSpec` (`TestDspIngestClient` switches to a login token)
- [ ] Remove `baseUrl` and `assetDir` from `DspIngestConfig` (`config/AppConfig.scala`) and `base-url` and `asset-dir` from `app.dsp-ingest` in `application.conf` (`externalBaseUrl` is removed in Phase 6)
- [ ] Drop `DspIngestTestContainer` from `modules/testkit/.../core/TestContainerLayers.scala` and the `dsp-ingest.base-url` override
- [ ] Delete `modules/testkit/src/main/scala/org/knora/webapi/testcontainers/DspIngestTestContainer.scala`
- [ ] Update `modules/testkit/.../core/TestContainerLayersSpec.scala` (drop the `DspIngestTestContainer` and `dspIngest.baseUrl` assertions, `:22-29`)
- [ ] Point `modules/testkit/.../testservices/TestDspIngestClient.scala` at the test API's `/ingest/` routes with a login token
- [ ] Update `modules/testkit/.../testservices/TestClientsModule.scala` (`DspIngestConfig` in its environment, `:11`, `:38`) and `modules/testkit/.../E2EZSpec.scala` (`DspIngestClient` in `sipiServiceLayer`, `:28-53`) for the reduced config and the in-process client
- [ ] Update `ProjectMigrationExportServiceSpec`, `ProjectMigrationImportServiceSpec`, `MetadataEndpointsE2ESpec`, `LegalInfoE2ESpec`, `KnoraSipiIntegrationV2ITSpec` and `StandoffEndpointsE2ESpec` for the removed container
- [ ] Update `AppConfigSpec` for the reduced `DspIngestConfig`
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Run `just test-it`; it passes
- [ ] Run `just test-e2e`; it passes, including the project export and import round-trip specs
- [ ] Run `bazel test //modules/ingest:test`; it passes
- [ ] Run `just test-ingest-integration`; it passes (the standalone module and image stay green until Phase 8)
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 6: Emit new original-file links on the API host

- [ ] Change `ResourceMetadataRules.originalFileUrl` to take the API's external base URL and build `<base>/ingest/projects/<shortcode>/assets/<assetId>/original`
- [ ] Pass `KnoraApi.externalKnoraApiBaseUrl` from `slice/resources/service/FairResourcesLive.scala` (replacing `ingestConfig.externalBaseUrl`) and drop its `DspIngestConfig` dependency
- [ ] Pass `KnoraApi.externalKnoraApiBaseUrl` from `slice/export/api/service/ExportService.scala` (`fileLinkOf`) instead of `appConfig.dspIngest.externalBaseUrl`
- [ ] Remove `externalBaseUrl` from `DspIngestConfig` and `external-base-url` from `application.conf`
- [ ] Update `FairResourcesLiveSpec`, `ExportServiceSpec` and any golden files they assert, regenerating the goldens and checking that the diff only changes the link host and path
- [ ] Update the E2E specs asserting original-file links (`MetadataEndpointsE2ESpec`, FAIR E2E specs)
- [ ] Run `bazel test //modules/webapi:test`; it passes
- [ ] Run `just test-e2e`; it passes
- [ ] Run `bazel test //modules/ingest:test`; it passes
- [ ] Run `just test-ingest-integration`; it passes (the standalone module and image stay green until Phase 8)
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 7: Ship Sipi CLI, ffmpeg and ffprobe in the dsp-api image

- [ ] Add `//modules/sipi:runtime_fs_amd64` / `runtime_fs_arm64` to the `tars` of `image_amd64` / `image_arm64` in `modules/webapi/BUILD.bazel`
- [ ] Change the `runtime_fs_*` visibility in `modules/sipi/BUILD.bazel` to `//modules/webapi:__pkg__` (plus `//modules/ingest:__pkg__` until Phase 8) and update its comment
- [ ] Keep the image's default (root) user and note in the `oci_image` comment that ingest needs it for NFS-owned assets
- [ ] Run `bazel build //modules/webapi:image_amd64 //modules/webapi:image_arm64`; both build
- [ ] Run `just docker-build-dsp-api-image`, then `docker run --rm --entrypoint /sbin/sipi daschswiss/knora-api:latest --help`, `... --entrypoint ffmpeg ... -version` and `... --entrypoint ffprobe ... -version`; each exits 0
- [ ] Run `docker run --rm --entrypoint /bin/sh daschswiss/knora-api:latest -c 'echo 1000 > /proc/self/oom_score_adj && cat /proc/self/oom_score_adj'`; it prints `1000`
- [ ] Run `bazel test //modules/ingest:test`; it passes
- [ ] Run `just test-ingest-integration`; it passes (the standalone module and image stay green until Phase 8)
- [ ] Compare the image size against the baseline in Success Metrics; the growth stays within the `runtime_fs` layer size
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 8: Remove the standalone ingest application, image and local-stack container

- [ ] Delete `modules/ingest/` entirely (sources, tests, `BUILD.bazel`, `application.conf`, `logback.xml`, `CLAUDE.md`, `README.md`, `mkdocs.yml`, `CHANGELOG.md`, `docs/`)
- [ ] Delete the standalone `MigrateSizes` / `MigrateMimeTypes` mains and their `scala_binary` targets with the module; the backfills stay available as the `MigrateSizes` / `MigrateMimeTypes` maintenance actions added in Phase 3, and their services and specs live in the slice since Phase 2
- [ ] Delete `slice/ingest/domain/AuthScope.scala` and its spec, now that the standalone `api/` is gone
- [ ] Delete `modules/test-ingest-integration/`
- [ ] Remove `//modules/ingest:license_srcs` and `//modules/test-ingest-integration:license_srcs` from `tools/license/BUILD.bazel` and `tools/lint/BUILD.bazel`
- [ ] Remove `//modules/ingest:__pkg__` from the `runtime_fs_*` visibility in `modules/sipi/BUILD.bazel` and update the stale "dsp-ingest image" comment
- [ ] Update the `MODULE.bazel` comment that names `modules/ingest`
- [ ] Remove ingest targets from `scalafmt_targets`, `test-unit` and `docker-load-test-images` in `justfile`
- [ ] Remove the `test-ingest`, `test-ingest-integration`, `docker-build-ingest-image`, `docker-publish-ingest-image` and `docs-ingest-build` recipes from `justfile`, and their uses in `docker-build`, `docker-publish` and `docs-build`
- [ ] Remove `ingest` from the `docker compose up` service lists and recipe comments in `justfile` (`stack-start-dev`, `stack-without-api`, `stack-without-app`, `stack-without-api-and-sipi`, `stack-up*`)
- [ ] Remove `--ignore modules/ingest/CHANGELOG.md` from the markdownlint invocation in `justfile`
- [ ] Remove the `ingest` service and the `ingest-db` volume from `docker-compose.yml`
- [ ] Change the `api` service in `docker-compose.yml`: mount `./modules/sipi/images:/opt/images` read-write and `./modules/sipi/tmp-dsp-ingest:/opt/temp`, remove `KNORA_WEBAPI_DSP_INGEST_BASE_URL` and `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR` and their comments, and raise the memory limit by ingest's 2G
- [ ] Add `INGEST_ALLOW_ERASE_PROJECTS=true` to the `api` service in `docker-compose.yml`, carrying over the removed ingest container's `ALLOW_ERASE_PROJECTS=true` so local project erase keeps erasing assets
- [ ] Remove the `modules/ingest/target` and `modules/test-ingest-integration/target` entries from `.bazelignore` and the `modules/ingest/docs/openapi/*.yml` entry from `.gitignore`
- [ ] Update the ingest mentions in `docs/development/dsp-api-rbe.md` (`//modules/{webapi,ingest,...}:test`, `//modules/test-ingest-integration:test`), `flake.nix` (dev-shell comment) and `tools/oci/defs.bzl` (module docstring)
- [ ] Remove the `test-ingest-integration` job and the `ingest-test.xml` collection from `.github/workflows/build-and-test.yml`, and update the "loads the PR's sipi/ingest/fuseki images" comments
- [ ] Rename the "dsp-api/dsp-ingest/sipi/fuseki" step in `.github/workflows/docker-publish.yml`
- [ ] Update the dsp-ingest comments in `modules/test-it/BUILD.bazel` (header and the `test-ingest-integration` note) and `modules/test-e2e/BUILD.bazel` (header)
- [ ] Run `git grep -n -e "modules/ingest" -e "dsp-ingest:latest" -e "test-ingest-integration" -- ':!docs/specs' ':!*CHANGELOG.md' ':!ARCH-MAP.md' ':!CLAUDE.md' ':!CONVENTIONS.md'`; it returns nothing (the three excluded files are updated in Phase 9)
- [ ] Run `bazel build //...`; it builds
- [ ] Run `just test-unit`; it passes
- [ ] Run `just test-it`; it passes
- [ ] Run `just test-e2e`; it passes
- [ ] Run `just stack-start`, then upload an image with `curl` to `http://localhost:3333/ingest/projects/0001/assets/ingest/<name>` with a login token and read `GET /ingest/projects/0001/assets/<id>`; both succeed in the containerized API
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 9: Update documentation and the architecture map

- [ ] Add `docs/03-endpoints/ingest/` pages (overview with the `/ingest/` prefix and the `ingest.<domain>` compatibility note, authentication and scopes, ingest flow, bulk ingest) from the useful content of the deleted `modules/ingest/docs/`, and add them to the `nav` in `mkdocs.yml`
- [ ] Document the `INGEST_*` settings, the reduced `KNORA_WEBAPI_DSP_INGEST_*` settings, the read-write image/temp mounts and the root user requirement in `docs/04-publishing-deployment/configuration.md`
- [ ] Update `docs/05-internals/development/third-party.md`, `docs/03-endpoints/api-v3/project-migration.md`, `docs/03-endpoints/api-v3/project-data-import.md`, `docs/03-endpoints/api-v2/editing-values.md`, `docs/03-endpoints/api-admin/overview.md` and `docs/03-endpoints/api-admin/files.md` where they describe dsp-ingest as a separate service or URL
- [ ] Update `docs/contexts/assets/CONTEXT.md` for the in-process ingest slice
- [ ] Update `CLAUDE.md` and `CONVENTIONS.md` (test locations, `dsp-ingest` image mentions, versioning paragraph)
- [ ] Run `dune:dune-map` to refresh the stale `ARCH-MAP.md` entries (the `ingest` component becomes the webapi ingest slice; the `DspIngestClient` "remote-owned" notes and the deployables list change)
- [ ] Load `eng:grafana-dashboards` and remove or re-point the dsp-ingest panels in `grafana-dashboards/dsp-api/dsp-backend.json` (`INGEST CPU usage %`, `INGEST JVM Memory`, the `DSP-INGEST` row, title/description/tags), since ingest no longer has its own JVM or service label
- [ ] Document the `MigrateSizes` and `MigrateMimeTypes` maintenance actions (admin scope, `restrictToProjects`) in the ingest maintenance docs page
- [ ] Run `git grep -n -e "modules/ingest" -e "dsp-ingest:latest" -e "test-ingest-integration" -- ':!docs/specs' ':!*CHANGELOG.md'`; it returns nothing
- [ ] Run `grep -rn "docs/specs/" --exclude-dir=specs docs CLAUDE.md CONVENTIONS.md ARCH-MAP.md`; it returns nothing
- [ ] Run `just docs-build`; it succeeds
- [ ] Run `just check`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Deploy the dsp-api release containing `/ingest/` to each environment before ops-deploy's cutover (proxy `ingest.<domain>` to `/ingest/`, removal of the ingest service) for that environment | Release owner / ops | after ship | Releasing and deploying are the owner's decision and need deployment credentials |

## Acceptance Criteria

- [ ] Every standalone ingest route answers under `/ingest/` with the same request and response shapes, verified by the ported endpoint specs and `IngestEndpointsE2ESpec` (REQ-1.1, REQ-1.2)
- [ ] `POST /ingest/maintenance/ImportProjectsToDb` is rejected as an unknown action (`400`), and `/ingest/health` has no database entry (REQ-1.2)
- [ ] Tokens minted by `JwtService.createJwt` and service tokens with the ingest audience are accepted with the same scopes; a token without the ingest audience, with a foreign issuer or without subject is `401` (REQ-1.3, REQ-1.4)
- [ ] A secured `/ingest/` route without a token answers `401` (REQ-1.4)
- [ ] An upload above 1 MiB with `Content-Length` streams to storage and keeps its checksum (REQ-1.5)
- [ ] FAIR metadata and export file links use `<api external base URL>/ingest/projects/.../original` (REQ-2.3)
- [ ] `/ingest/health` answers `200` while the asset and temp directories are usable (REQ-2.4)
- [ ] Ingest request handling, export, import, erase and forked ingest jobs run on `ingest-` threads; child processes and CPU-heavy blocking calls are bounded by the executor's permits (REQ-3.1, REQ-3.2)
- [ ] A failing Sipi/ffmpeg/ffprobe process fails only its request or bulk item (REQ-3.3)
- [ ] Child processes in the image start with `oom_score_adj` 1000 (REQ-3.4)
- [ ] dsp-api reads ingest settings only from `ingest.conf`/`INGEST_*`, and no key or variable is read by both configurations (REQ-4.1)
- [ ] dsp-api starts with no database setting and no SQLite, Flyway or HikariCP on the classpath (REQ-4.2)
- [ ] `DspIngestClient` has no HTTP implementation and dsp-api has no ingest base URL setting (REQ-4.3)
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
- The size and MIME-type backfills are no longer separate main classes: they run as
  `POST /ingest/maintenance/MigrateSizes` and `POST /ingest/maintenance/MigrateMimeTypes` (admin scope, optional
  `restrictToProjects`). The ops-deploy playbooks INFRA-1404/1413 that call the old main classes are not re-run.

**Resolved decisions:**

- `MigrateSizes`/`MigrateMimeTypes` backfills move into the slice as maintenance actions (Phase 3), not as
  separate `scala_binary` mains; the standalone mains go with the module in Phase 8.
- In-process `importProject` keeps the HTTP client's log-only failure handling (Phase 5).
- ADR-0011: `OriginalAccessLive` sits in `slice/admin/domain/service/` (rule 3). `DspIngestClient` staying in
  `slice/admin/domain/service/` and implemented by the ingest slice is a recorded deviation from rule 5 (see
  Proposed Solution 6).

**Follow-ups** (out of scope; name them in the execution's final report):

- `DspIngestClient.importProject` now runs in-process, so a failed asset import could fail the project-import
  request instead of only being logged. This plan keeps the log-only behaviour to match today; changing it is a
  behaviour change for project import and needs its own decision.

**Ordering:** H1 precedes the ops-deploy cutover; dsp-tools merges its `/ingest/` change only once prod serves
`/ingest/` (PRD constraint). dsp-app may switch once the environment it targets runs this release.

**Open questions** (defaults chosen; revisit in plan review):

- Logged-out tokens: the standalone service did not consult webapi's invalid-token cache, so a token used after
  logout still works on ingest. Default: keep that (REQ-1.3). Rejecting them would be a deliberate tightening.
- A token whose `scope` holds a malformed `read:project:`/`write:project:` shortcode is `401` either way, but the
  message text inside `"Invalid contents: ..."` changes to webapi's `Shortcode` message. Default: accept.
- ffmpeg and the Sipi CLI use several threads per process, so permits bound the process count, not cores. Default:
  no `-threads` cap (it can change encoder output). If the design review finds that one conversion can still
  saturate the CPUs, add `-threads <pool size>` to ffmpeg in a follow-up.
- `/ingest/info` reports dsp-api's version and commit, which equal what the standalone image reported for the same
  git revision. Default: accept.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Chunked uploads (no `Content-Length`) over 1 MiB get a bare `413` under Hybrid streaming, where the standalone service streamed them | M | H | Phase 3 E2E covers the declared-length case; dsp-app and dsp-tools plans confirm their uploads send `Content-Length`; documented in the ingest endpoint docs |
| Forked jobs or `attemptBlocking` work escape the ingest executor and compete with API traffic | M | M | Permit semaphore around processes, checksums and BagIt; `IngestExecutorSpec` asserts executor inheritance for forked fibers |
| A missing or read-only ingest mount stops dsp-api at startup | L | H | Smoke test logs instead of dying; `/ingest/health` reports `503`; ops mounts are a cross-repo contract |
| The JVM and conversions share the container memory; conversions trigger the OOM killer | M | M | `oom_score_adj` 1000 on children; ops raises the memory limit by ingest's 2G with JVM settings unchanged |
| Larger dsp-api image (Sipi tree, ffmpeg) slows pulls and CI | H | L | Measured against the baseline in Phase 7; accepted cost of one container |
| Phase 2's mass move hides a behaviour change | M | M | Pure-move commit kept separate from adaptation edits; reviewer diffs with `git log --follow -M` |
| Test runtime grows because ingest specs now run in the single `//modules/webapi:test` JVM | M | L | Accept; the ingest test target and its container are removed in exchange |
| Dashboards or alerts in ops keyed to the ingest service name go blank | M | L | Phase 9 updates the in-repo dashboard; ops-deploy plan covers its alerts |

## Success Metrics

- Baseline before Phase 7: the size of `daschswiss/knora-api:latest` (`docker image inspect --format '{{.Size}}'`)
  after `just docker-build-dsp-api-image` on the pre-Phase-7 commit. After Phase 7, the growth is within the
  combined size of the `runtime_fs` tree (`/sbin/sipi`, `/sipi`, ffmpeg, ffprobe, tini).
- Every route of the standalone service's OpenAPI (`ProjectsEndpoints`, `MaintenanceEndpoints` minus
  `ImportProjectsToDb`, `ReportEndpoints`, `MonitoringEndpoints`) has a matching `/ingest/` route in dsp-api's
  generated docs.
- The repository builds and tests with no `modules/ingest` and no `modules/test-ingest-integration`.

## References

- PRD: `01-move-ingest-into-webapi-PRD.md`; brief: `00-project-brief.md`
- Standalone auth: `modules/ingest/src/main/scala/swiss/dasch/api/AuthService.scala`, `domain/AuthScope.scala`,
  `api/BaseEndpoints.scala`
- Standalone config: `modules/ingest/src/main/scala/swiss/dasch/config/Configuration.scala`,
  `modules/ingest/src/main/resources/application.conf`
- Standalone image: `modules/ingest/BUILD.bazel` (`image_amd64` tars `//modules/sipi:runtime_fs_amd64`);
  `modules/sipi/BUILD.bazel` (`runtime_fs_*`)
- Child processes: `modules/ingest/src/main/scala/swiss/dasch/infrastructure/CommandExecutorLive.scala`
- Callback to dsp-api: `modules/ingest/src/main/scala/swiss/dasch/FetchAssetPermissions.scala`;
  `modules/webapi/src/main/scala/org/knora/webapi/slice/api/admin/FilesServerEndpoints.scala`
- webapi side: `slice/admin/domain/service/DspIngestClient.scala`, `slice/infrastructure/JwtService.scala`,
  `slice/infrastructure/Scope.scala`, `slice/common/api/BaseEndpoints.scala`, `core/DspApiServer.scala`
  (`maxAggregatedRequestBodySize`), `store/iiif/impl/AssetInfoServiceLayer.scala`,
  `slice/resources/service/FairResourcesLive.scala:57`, `slice/export/api/service/ExportService.scala:151`,
  `slice/resources/domain/ResourceMetadataRules.scala:62`, `config/AppConfig.scala` (`DspIngestConfig`)
- Build, stack, CI: `modules/webapi/BUILD.bazel`, `justfile`, `docker-compose.yml`,
  `.github/workflows/build-and-test.yml`, `.github/workflows/docker-publish.yml`
- Conventions: `CONVENTIONS.md`, `docs/development/dsp-api-conventions.md`,
  `docs/development/dsp-api-error-handling.md`, `docs/development/dsp-api-value-types.md`,
  `docs/adr/0011-cross-context-access-ports-and-adapters.md`
- Learning: `dasch-specs/learnings/design-decisions/security-logic-authenticates-body-buffering-precedes-it.md`
