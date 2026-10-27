---
plan: docs/specs/2026-10-20-move-ingest-into-webapi/02-feat-move-ingest-into-webapi-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: ce8c74195
branch: docs/move-ingest-into-webapi-brief
started: 2026-10-26
problem: >
  dsp-ingest runs as a second service next to dsp-api: two services to build, deploy, configure and
  monitor, two token validators, a second public address for clients, and a SQLite database that nothing
  reads to decide behaviour. dsp-api already reaches into ingest's code and over HTTP, and ingest calls back
  into dsp-api over HTTP. The work moves ingest into the webapi module as the slice/ingest/ slice served
  under /ingest/, removes its SQLite database, keeps its configuration separate, contains its work on a
  dedicated executor, and removes the standalone application, image and local-stack container.
symptoms:
status: in-progress
---

# Execution Journal: 02-feat-move-ingest-into-webapi-plan

## Repos

| repo | base_commit | branch | merge_strategy | status | pr |
| --- | --- | --- | --- | --- | --- |
| dsp-api | ce8c74195 | docs/move-ingest-into-webapi-brief | squash | in-progress | — |

## Phases

| phase | status | phase_base | review_fix_rounds |
| --- | --- | --- | --- |
| 1 | reviewed | dsp-api@a898b467d | 0 |
| 2 | reviewed | dsp-api@99963d79e | 2 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{domain/MaintenanceActions.scala,api/MaintenanceEndpoints.scala,api/MaintenanceEndpointsHandler.scala} | — | "Remove MaintenanceActions.importProjectsToDb and ActionName.ImportProjectsToDb" | ImportProjectsToDb is no longer an action; ingest compiles and tests pass | MaintenanceActions.scala § importProjectsToDb; MaintenanceEndpoints.scala § enum ActionName | — |
| 1.2 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{infrastructure/DbHealthIndicator.scala,infrastructure/HealthCheckService.scala,Main.scala}, modules/ingest/src/test/scala/swiss/dasch/api/MonitoringEndpointsSpec.scala | — | "Delete infrastructure/DbHealthIndicator.scala and build HealthCheckServiceLive from FileSystemHealthIndicator alone"; "Add a MonitoringEndpointsSpec case: the health body has no db entry" | /health over the real HealthCheckServiceLive is 200 {"status":"UP"} with a healthy FS and lists only "folders" when unhealthy | HealthCheckService.scala § HealthCheckServiceLive.layer; MonitoringEndpointsSpec § healthEndpointsSuite | — |
| 1.3 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{db/*,domain/ProjectRepositoryLive.scala,domain/Project.scala,domain/ProjectService.scala,domain/BulkIngestService.scala,config/Configuration.scala,Main.scala,MigrateSizes.scala,MigrateMimeTypes.scala}, modules/ingest/src/main/resources/{application.conf,db/migration/V1_0_0__create_project_table.sql}, modules/ingest/BUILD.bazel, modules/ingest/docs/service-configuration.md, modules/ingest/src/test/scala/swiss/dasch/{util/TestUtils.scala,domain/ProjectRepositoryLiveSpec.scala, specs providing ProjectRepositoryLive/testDbLayer} | 1.1, 1.2 | Delete Db/DbMigrator; delete migration SQL; delete ProjectRepositoryLive + Project/ProjectId; ProjectService folder-only; remove SQLException; remove DbConfig/db block; remove DB layers from mains; remove three maven deps from BUILD; delete ProjectRepositoryLiveSpec and DB test setup | ingest module has no database code or SQLite/Flyway/HikariCP dependency; bazel test //modules/ingest:test passes | ProjectService.scala § findOrCreateProject/deleteProject/addProjectToDb; Configuration.scala § ApplicationConf; BUILD.bazel § ingest/ingest_classes deps | — |
| 1.4 | dsp-api | MODULE.bazel, maven_install.json | 1.3 | "Remove the three artifacts from maven.install in MODULE.bazel and re-pin" | the three coordinates are absent from MODULE.bazel and maven_install.json; build resolves | MODULE.bazel § "-- dsp-ingest db (Java) --" | — |
| 1.5 | dsp-api | — (verification) | 1.4 | "Run bazel test //modules/ingest:test"; "Run just test-ingest-integration"; "Run just check" | all three pass on the phase's last commit | plan Phase 1 tail | — |
| 2.1 | dsp-api | modules/ingest/src/{main,test}/scala/swiss/dasch/** (git mv to modules/webapi/src/{main,test}/scala/org/knora/webapi/slice/ingest/{api,domain,repo,infrastructure,config,testutil}/), modules/webapi/src/test/resources/org/knora/webapi/slice/ingest/**, modules/{ingest,webapi}/BUILD.bazel, modules/webapi/src/{main,test}/scala/org/knora/webapi/{slice/export,store/iiif/impl}/** | — | git mv domain files; git mv infrastructure files; git mv ApiProblem; move UploadResponse and ActionName; point standalone api/Main at moved classes; git mv unit tests; copy test helpers; copy test resources; update swiss.dasch imports in export/FAIR; remove ingest_classes; make ingest depend on webapi | moved files change only package/import lines; webapi and ingest build; bazel test //modules/webapi:test and //modules/ingest:test pass | plan Phase 2 checkboxes 1-8, 16-18; Technical Considerations § Slice layout | — |
| 2.2 | dsp-api | modules/webapi/src/main/scala/org/knora/webapi/slice/ingest/config/IngestConfig.scala, modules/webapi/src/main/resources/ingest.conf, modules/webapi/src/test/scala/org/knora/webapi/slice/ingest/config/IngestConfigSpec.scala, BulkIngestService.scala | 2.1 | Add IngestConfig.scala loader; add ingest.conf; add IngestConfigSpec; loader takes the Config | defaults load, env overrides apply via resolveWith, cpu-share outside (0, 1] fails, webapi app config has no ingest key | plan § Configuration mapping | — |
| 2.3 | dsp-api | slice/ingest/infrastructure/CommandExecutorLive.scala and its spec, modules/ingest/src/main/{scala/swiss/dasch/config/Configuration.scala,resources/application.conf}, modules/webapi/BUILD.bazel | 2.2 | Replace BuildInfo.knoraSipiVersion with SipiConfig.localDevImage; standalone Configuration adds local-dev-image | webapi no longer depends on ingest_buildinfo; standalone keeps its version-tagged default image | CommandExecutorLive § buildCommand | — |
| 2.4 | dsp-api | store/iiif/impl/AssetInfoServiceLayer.scala, core/LayersLive.scala, modules/testkit/src/main/scala/org/knora/webapi/core/TestContainerLayers.scala, Main/E2EZSpec types | 2.2 | Point AssetInfoServiceLayer at ingest StorageConfig; wire ingest config in LayersLive and TestContainerLayers | AssetInfoServiceLayer reads ingest StorageConfig; webapi and testkit build | LayersLive § remainingLayer | — |
| 2.5 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/config/Configuration.scala, modules/ingest/src/test/scala/swiss/dasch/config/ConfigurationSpec.scala, modules/ingest/BUILD.bazel comment | 2.3 | Standalone Configuration.layer reads its own keys with webapi on the classpath, plus ConfigurationSpec | spec loads standalone keys while webapi's application.conf is on the classpath | Configuration.scala § layer | — |
| 2.6 | dsp-api | — (verification) | 2.1-2.5 | Run webapi tests; run ingest tests; run test-ingest-integration; run just check | all pass on the phase's last commit | plan Phase 2 tail | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | aa1a0d115 | Removed the ImportProjectsToDb action; spec pins it as an unknown action (400) | none |
| 1.2 | dsp-api | complete | 1acda15d7 | Dropped DbHealthIndicator; HealthCheckServiceLive built from the FS indicator; spec runs the real health service (200 UP, 503 lists only folders) | none |
| 1.3 | dsp-api | complete | 488801b68 | Removed the SQLite project table, repository, Flyway/Hikari setup, db config and the three maven deps from both ingest library targets; ProjectService is folder-only | none |
| 1.4 | dsp-api | complete | 4d45a81a2 | Removed sqlite-jdbc, flyway-core and HikariCP from maven.install and re-pinned (deletions only) | none |
| 1.5 | dsp-api | complete | — | Verification: bazel test //modules/ingest:test and test-ingest-integration pass (integration needed TESTCONTAINERS_RYUK_DISABLED=true locally); just check fails only on markdownlint of the 03/04/05 plan journals, so its checkbox stays open | none |
| 2.1 | dsp-api | complete | ad2c03867 | Moved ingest domain/repo/infrastructure code, ApiProblem, UploadResponse, ActionName and their specs into slice/ingest (package/import lines only, plus three forced edits named in the commit body); copied test helpers and fixtures; ingest now depends on webapi; ingest_classes deleted | none |
| 2.2 | dsp-api | complete | bea449197 | Added IngestConfig loader over ingest.conf with INGEST_ env vars and cpu-share validation; IngestConfigSpec covers defaults, overrides, range, no shared key or env var (range case canary-checked) | none |
| 2.3 | dsp-api | complete | 4b5c5707b | CommandExecutorLive runs SipiConfig.localDevImage (INGEST_SIPI_LOCAL_DEV_IMAGE, default knora-sipi:latest) instead of BuildInfo; webapi drops ingest_buildinfo; standalone keeps its git-version default via a config fallback | none |
| 2.4 | dsp-api | complete | b7d2d0c31 | AssetInfoServiceLayer takes the ingest StorageConfig; LayersLive loads IngestConfig beside AppConfig and remainingLayer takes it; TestContainerLayers provides it; MetadataEndpointsE2ESpec passes with the new bootstrap | none |
| 2.5 | dsp-api | complete | a95874a37 | ConfigurationSpec loads the standalone Configuration.layer with webapi application.conf on the classpath (canary: wrong default image fails it); layer comment states the disjoint-keys rule | none |
| 2.6 | dsp-api | complete | — | Verification on a95874a37: webapi:test (27 moved ingest specs run there) and ingest:test pass; test-ingest-integration passes against the rebuilt image carrying webapi.jar (with TESTCONTAINERS_RYUK_DISABLED=true); just check passes | none |
| 2.R1 | dsp-api | complete | 6ab6ff1a6 | Review fix round 1: comments on the now-unread app.dsp-ingest.asset-dir and DspIngestConfig.assetDir say they are unused; migration-service scaladoc no longer links to swiss.dasch mains; IngestConfigSpec asserts the cpu-share failure reason, the INGEST_ variable set by name, and closes its sources | none |
| 2.R2 | dsp-api | complete | 36b2e96fe | Review fix round 2: docker-compose comment on KNORA_WEBAPI_DSP_INGEST_ASSET_DIR says it is unused; wrapped a scaladoc line (comment-only, not re-reviewed) | none |

## Deferrals

- Phase 2 review, `scope`: the dune reviewer flagged that `slice/ingest/domain` and `slice/ingest/repo` import each other (`AssetInfo`, `Sha256Hash` and the service traits live in `repo` but are domain types). The plan fixes the layout and the "move unchanged" rule, so sorting the types, or adding a package-dependency check, is for the final review or a follow-up.

## Deviations

- none

## Side findings

- Phase 2: `util/TestUtils.scala`, named in the copy-helpers checkbox, no longer exists (Phase 1 deleted it), so nothing was copied for it.
- Phase 2: `CommandExecutorMock` stays in the `slice/ingest/infrastructure` test package and `AssetInfoFileTestHelper` in the `repo` test package, not in `testutil`, because they call package-private members (`Command`'s constructor, `AssetInfoFileContent`). The standalone module keeps copies of both under `modules/ingest/src/test/scala/org/knora/webapi/slice/ingest/` for the same reason, until Phase 8.
- Phase 2: `IngestConfig.load` merges the env over `ingest.conf` and resolves with the system environment off, instead of the `resolveWith` the plan names: `resolveWith` against an external source cannot resolve the `key = default, key = ${?VAR}` pattern.
- Phase 2: `app.dsp-ingest.asset-dir` / `DspIngestConfig.assetDir` / `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR` are no longer read (marked unused); Phase 5 and Phase 8 remove them. Until then a non-default value there is ignored in favour of `INGEST_STORAGE_ASSET_DIR`.
- Phase 2 review findings refuted as owned by later phases or mandated by the plan: unconsumed `IngestFeatures`/`ExecutorConfig` (Phases 3/4), duplicated test helpers and fixtures across modules (deleted in Phase 8), `AssetInfoServiceLayer` outside the slice (deleted in Phase 3), ARCH-MAP `ingest_classes` entries (Phase 9).
- Phase 2 suggestions not applied: validate `bulk-max-parallel > 0` (the standalone never validated it); `ConfigurationSpec` resolves against the process environment (clean under `bazel test`); standalone `Configuration.layer` builds its typesafe config at object init rather than inside the effect (as before this phase); `SpecPaths` throws at object init and leaves temp copies.
- Phase 1 review (consistency, code-simplicity, dune, scala-zio; sonnet): no verified Critical or Warning. Refuted: BulkIngestServiceSpec folder check vacuous (canary: making findOrCreateProject a no-op fails the test, the spec deletes the folder before each case); ImportProjectsToDb 400 could have another cause (canary: with the action restored the test fails); findOrCreateProject name misleading (createProjectFolder is idempotent, so it finds or creates). Out of scope, covered by later phases: DB_JDBC_URL in docker-compose.yml and both DspIngestTestContainer helpers, ingest-db volume (Phases 5/8), stale ARCH-MAP SQLite/ProjectRepository entries (Phase 9).
- Suggestions not applied: HealthCheckServiceLive.layer could build HealthCheckServiceLive(Chunk(fs)) directly; MonitoringEndpointsSpec duplicates part of its layer list across two provide blocks.
- just check fails on the branch because of markdownlint MD060/MD033 errors in the 03/04/05 plan journals (other executors' files); scalafmt, license headers and every other markdown file pass.
- ImportProjectsToDb removal is API-visible: an ops script calling POST /maintenance/ImportProjectsToDb now gets 400.
- test-ingest-integration fails locally with "Could not connect to Ryuk"; passes with --test_env=TESTCONTAINERS_RYUK_DISABLED=true (environment, not this diff).
