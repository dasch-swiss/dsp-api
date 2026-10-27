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
| 2 | reviewed | dsp-api@1c0547439 | 2 |

## Chunk queue

| id | repo | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{domain/MaintenanceActions.scala,api/MaintenanceEndpoints.scala,api/MaintenanceEndpointsHandler.scala} | — | "Remove MaintenanceActions.importProjectsToDb and ActionName.ImportProjectsToDb" | ImportProjectsToDb is no longer an action; ingest compiles and tests pass | MaintenanceActions.scala § importProjectsToDb; MaintenanceEndpoints.scala § enum ActionName | — |
| 1.2 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{infrastructure/DbHealthIndicator.scala,infrastructure/HealthCheckService.scala,Main.scala}, modules/ingest/src/test/scala/swiss/dasch/api/MonitoringEndpointsSpec.scala | — | "Delete infrastructure/DbHealthIndicator.scala and build HealthCheckServiceLive from FileSystemHealthIndicator alone"; "Add a MonitoringEndpointsSpec case: the health body has no db entry" | /health over the real HealthCheckServiceLive is 200 {"status":"UP"} with a healthy FS and lists only "folders" when unhealthy | HealthCheckService.scala § HealthCheckServiceLive.layer; MonitoringEndpointsSpec § healthEndpointsSuite | — |
| 1.3 | dsp-api | modules/ingest/src/main/scala/swiss/dasch/{db/*,domain/ProjectRepositoryLive.scala,domain/Project.scala,domain/ProjectService.scala,domain/BulkIngestService.scala,config/Configuration.scala,Main.scala,MigrateSizes.scala,MigrateMimeTypes.scala}, modules/ingest/src/main/resources/{application.conf,db/migration/V1_0_0__create_project_table.sql}, modules/ingest/BUILD.bazel, modules/ingest/docs/service-configuration.md, modules/ingest/src/test/scala/swiss/dasch/{util/TestUtils.scala,domain/ProjectRepositoryLiveSpec.scala, specs providing ProjectRepositoryLive/testDbLayer} | 1.1, 1.2 | Delete Db/DbMigrator; delete migration SQL; delete ProjectRepositoryLive + Project/ProjectId; ProjectService folder-only; remove SQLException; remove DbConfig/db block; remove DB layers from mains; remove three maven deps from BUILD; delete ProjectRepositoryLiveSpec and DB test setup | ingest module has no database code or SQLite/Flyway/HikariCP dependency; bazel test //modules/ingest:test passes | ProjectService.scala § findOrCreateProject/deleteProject/addProjectToDb; Configuration.scala § ApplicationConf; BUILD.bazel § ingest/ingest_classes deps | — |
| 1.4 | dsp-api | MODULE.bazel, maven_install.json | 1.3 | "Remove the three artifacts from maven.install in MODULE.bazel and re-pin" | the three coordinates are absent from MODULE.bazel and maven_install.json; build resolves | MODULE.bazel § "-- dsp-ingest db (Java) --" | — |
| 1.5 | dsp-api | — (verification) | 1.4 | "Run bazel test //modules/ingest:test"; "Run just test-ingest-integration"; "Run just check" | all three pass on the phase's last commit | plan Phase 1 tail | — |
| 2.0 | dsp-api | — (measurement) | — | "Record the baseline size of daschswiss/knora-api:latest" | baseline size recorded from an image built at 1c0547439 | plan Phase 2 | — |
| 2.1 | dsp-api | modules/webapi/BUILD.bazel, modules/sipi/BUILD.bazel | 2.0 | "Add runtime_fs to the tars of image_amd64/image_arm64"; "Add //modules/webapi:__pkg__ to the runtime_fs visibility"; "Note in the webapi oci_image comment that the image runs as root" | both webapi images layer runtime_fs; bazel build of both images succeeds | webapi BUILD.bazel § knora-api image; sipi BUILD.bazel § Sipi runtime bits | — |
| 2.2 | dsp-api | — (verification) | 2.1 | "Run just docker-build-dsp-api-image, then sipi/ffmpeg/ffprobe"; "Run /bin/sh oom_score_adj"; "Compare the image size"; "Run just check" | each command exits 0, prints 1000, growth within the runtime_fs layer, check passes | plan Phase 2 tail | — |

## Chunks

| id | repo | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- | --- |
| 1.1 | dsp-api | complete | aa1a0d115 | Removed the ImportProjectsToDb action; spec pins it as an unknown action (400) | none |
| 1.2 | dsp-api | complete | 1acda15d7 | Dropped DbHealthIndicator; HealthCheckServiceLive built from the FS indicator; spec runs the real health service (200 UP, 503 lists only folders) | none |
| 1.3 | dsp-api | complete | 488801b68 | Removed the SQLite project table, repository, Flyway/Hikari setup, db config and the three maven deps from both ingest library targets; ProjectService is folder-only | none |
| 1.4 | dsp-api | complete | 4d45a81a2 | Removed sqlite-jdbc, flyway-core and HikariCP from maven.install and re-pinned (deletions only) | none |
| 1.5 | dsp-api | complete | — | Verification: bazel test //modules/ingest:test and test-ingest-integration pass (integration needed TESTCONTAINERS_RYUK_DISABLED=true locally); just check fails only on markdownlint of the 03/04/05 plan journals, so its checkbox stays open | none |
| 2.0 | dsp-api | complete | — | Baseline daschswiss/knora-api:latest built at 1c0547439 (arm64): 589,137,905 bytes | none |
| 2.1 | dsp-api | complete | 14c6401c1 | Both webapi images layer //modules/sipi:runtime_fs_{arch} first; runtime_fs visible to //modules/webapi; root rationale noted in the oci_image comment | none |
| 2.2 | dsp-api | complete | — | Image 933,837,104 bytes (+344,699,199 = the 345 MB runtime_fs_arm64 layer); /sbin/sipi --help, ffmpeg -version, ffprobe -version exit 0; oom_score_adj prints 1000; runs as uid 0; image_amd64 builds; just check passes | none |
| 2.R1 | dsp-api | complete | 71094adfc | Review fix: runtime_fs repacks sbin/ as usr/sbin/ (new image_rootfs_extract `rewrites` attr) so the temurin /sbin -> usr/sbin link survives; /sbin/sipi still resolves; runtime_fs comment names both consumers | none |
| 2.R2 | dsp-api | complete | b3aa9c377 | Review fix: `rewrites` doc and the runtime_fs call site state that `paths`/`top_level` use pre-rewrite names; runtime_fs header names both images | none |

## Deferrals

- 2026-10-27: Phases 2-4 of the previous plan shape (slice move, rebuilt API layer, executor) were discarded by owner
  decision (minimal diff; `modules/ingest` keeps its structure). Their commits are preserved on branch
  `backup/docs/move-ingest-into-webapi-brief` (tip `11c2df207`); the Phases table rows 2-3 recorded there are reset,
  and Phase 2 onwards restarts from the rewritten plan at `99963d79e`.

- 2026-10-27 (Phase 2 review): `ARCH-MAP.md` still says `//modules/ingest` is the only consumer of `//modules/sipi:runtime_fs_*` (sipi "Used by" and visibility boundary rule). Deferred to Phase 6's dune-map refresh; added as a Phase 6 checkbox.

- 2026-10-27 (Phase 2 review, after the 2-round cap): dune re-review suggests making the sbin -> usr/sbin mapping structural (e.g. a build assertion that the runtime_fs tar has no `sbin/` member and has `usr/sbin/sipi`) instead of a documented `rewrites` string. Left as documented; for the session's final review.

## Deviations

- none

## Side findings

- Phase 1 review (consistency, code-simplicity, dune, scala-zio; sonnet): no verified Critical or Warning. Refuted: BulkIngestServiceSpec folder check vacuous (canary: making findOrCreateProject a no-op fails the test, the spec deletes the folder before each case); ImportProjectsToDb 400 could have another cause (canary: with the action restored the test fails); findOrCreateProject name misleading (createProjectFolder is idempotent, so it finds or creates). Out of scope, covered by later phases: DB_JDBC_URL in docker-compose.yml and both DspIngestTestContainer helpers, ingest-db volume (Phases 5/8), stale ARCH-MAP SQLite/ProjectRepository entries (Phase 9).
- Suggestions not applied: HealthCheckServiceLive.layer could build HealthCheckServiceLive(Chunk(fs)) directly; MonitoringEndpointsSpec duplicates part of its layer list across two provide blocks.
- just check fails on the branch because of markdownlint MD060/MD033 errors in the 03/04/05 plan journals (other executors' files); scalafmt, license headers and every other markdown file pass.
- ImportProjectsToDb removal is API-visible: an ops script calling POST /maintenance/ImportProjectsToDb now gets 400.
- test-ingest-integration fails locally with "Could not connect to Ryuk"; passes with --test_env=TESTCONTAINERS_RYUK_DISABLED=true (environment, not this diff).
- Phase 2 review (consistency, code-simplicity, dune, devops; sonnet): verified Warning fixed in 71094adfc (the runtime_fs layer replaced the base's /sbin -> usr/sbin symlink with a directory holding only sipi; measured in the built image). Refuted: the NFS-root clause in the webapi image comment is rationale only (the plan's checkbox requires that note); +345 MB image growth is unused until Phase 3 (accepted in the plan's risk table). Not applied: a shared macro for the per-arch runtime_fs selection; comment only on the amd64 tars entry (matches the ingest BUILD style).
- A Sipi base digest bump now rebuilds and changes the knora-api image too (Renovate Sipi bumps touch the API image).
- Phase 2 re-review (consistency, code-simplicity, dune): refuted anchoring the rewrite as `^sbin/` (the bare `sbin` directory entry would then stay and replace the base's /sbin link again); refuted reverting the webapi comment re-wrap (it fixes an over-long line from 14c6401c1). Measured: /usr/sbin in the layer is 0755 root, same as the base.
