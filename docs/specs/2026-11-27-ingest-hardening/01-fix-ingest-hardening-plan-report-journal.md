---
plan: docs/specs/2026-11-27-ingest-hardening/01-fix-ingest-hardening-plan.md
target_repo: dasch-swiss/dsp-api
base_commit: 0719ec28555f5f7375aa76ce1348f918969665d1
branch: impl/ingest-hardening-report
started: 2026-11-27
unit: report
unit_state: done
curated_from: f7eeff4b67633f0aa36e4eec65a4175a3258077e
started_from: 0a8967babc22be26902135e385449d939687220d
problem: >
  An asset that was uploaded to ingest but never attached to a resource (or only to resources that were
  later deleted) cannot be found: no route lists a project's assets against the file values in its data
  graph. The rdu-13 import left such assets behind (DEV-8096). Ivan decided on 2026-11-27: a SystemAdmin
  report only, no delete.
status: complete
---

# Execution Journal: 01-fix-ingest-hardening-plan (unit report)

## Repos

| repo    | base_commit | branch                       | merge_strategy | status   | pr                                                        |
| ------- | ----------- | ---------------------------- | -------------- | -------- | --------------------------------------------------------- |
| dsp-api | 0719ec2     | impl/ingest-hardening-report | rebase         | complete | [#4464](https://github.com/dasch-swiss/dsp-api/pull/4464) |

## Phases

| phase | status   | phase_base      | review_fix_rounds |
| ----- | -------- | --------------- | ----------------- |
| 8     | reviewed | dsp-api@0719ec2 | 1                 |

## Chunk queue

- 8.1: `DspIngestClient.listAssets` (live, the two spec stubs, `DspIngestClientLiveSpec`); `AssetReferencesRepo`
  (`sparql"..."` over the data graph) and `UnreferencedAssetsService` with `UnreferencedAssetsServiceSpec` on the
  in-memory store; wired in `AdminDomainModule`. replaces: —
- 8.2: `GET /v3/projects/{shortcode}/assets/unreferenced` (`systemAdminPolicy`, `PageAndSize`, `PagedResponse`) in
  `V3ProjectsEndpoints`/`RestService`/`ServerEndpoints`; `AuthorizationMatrixE2ESpec` row; `UnreferencedAssetsE2ESpec`.
  depends on 8.1. replaces: —

## Chunks

| id         | repo    | status   | commit(s) | summary                                                                                                        | blocker |
| ---------- | ------- | -------- | --------- | -------------------------------------------------------------------------------------------------------------- | ------- |
| 8.1        | dsp-api | complete | 05fbf06   | listAssets, AssetReferencesRepo, UnreferencedAssetsService; spec fails when the previousValue* path is removed | none    |
| 8.2        | dsp-api | complete | 5dbeab3   | v3 route, matrix row, UnreferencedAssetsE2ESpec (compiled; not run: no Docker here)                            | none    |
| 8-review-1 | dsp-api | complete | beb2345   | paging offset in Long (no Int overflow on a large `page`); `NotFound.from(shortcode)`                          | none    |

## Deferrals

- none

## Deviations

- none

## Side findings

- Phase 8: the plan names `knora-base:fileValueHasFilename`; the stored predicate is `knora-base:internalFilename`
  (`fileValueHasFilename` is the knora-api name). The query uses `internalFilename`; same rule, right predicate.
- Phase 8: the plan says `TestDspIngestClient` gets `listAssets`. That class is an HTTP upload client, not a
  `DspIngestClient`; the two `DspIngestClient` stubs in `ProjectMigration{Import,Export}ServiceSpec` got it instead.
- Phase 8: the e2e case "a resource's asset from the loaded test data is not listed" is written as "an uploaded asset
  that a created resource references is not listed": the loaded test data has no assets in ingest storage, so the
  original case would pass vacuously.
- Phase 8: previous versions of a file value count as references (`previousValue*`), so a replaced file is not
  reported as an orphan. The plan's rule ("some file value in the data graph") already covers old versions; the
  path only finds their resource. A file value marked `isDeleted` on a live resource also counts as a reference
  (the plan's rule checks the resource only): a false negative in a report, never a false orphan.
- Phase 8: `listAssets` uses ingest's `ProjectService.findAssetInfosOfProject`, which wraps the plan's
  `AssetInfoService.findAllInPath` with the project-folder lookup.
- Phase 8: review (scala-zio, security, consistency, dune, simplicity; sonnet). Fixed: Int overflow of the page
  offset; hand-built `project_not_found` replaced by `NotFound.from(shortcode)`. Refuted: "unnamed path capture
  gives `{param1}`" (it has `.name("shortcode")`); bare `.orDie` and `ZIO.attempt` row parsing (the file's and
  `ViewRestrictionsRepo`'s pattern); repo without trait and InMemory (the carve-out is optional, and
  `ViewRestrictionsRepo` is the same shape); repo importing `ProjectService.projectDataNamedGraphV2` and admin
  reading the data graph (precedent `ViewRestrictionsRepo`, recorded in ARCH-MAP); identical domain and response
  types (the admin domain must not import API DTOs). Left for `final`: ARCH-MAP update (ship step 3), a
  docs page under `docs/03-endpoints/api-v3/` (not in the plan).
- Phase 8: `V3ProjectsRestServiceSpec` "a SystemAdmin's trigger records the admin as creator" failed once with
  `NoSuchFileException` on `/tmp/webapi_tmp/migration/exports/<id>/temp` and passed on two reruns; unrelated to
  this unit (export temp cleanup race).
- Phase 8: gates on Ivan's Mac at 400945d (tree of chunks 8.1 and 8.2): `just test-unit` passed (7 of 7 targets);
  `just check` failed only on markdownlint MD060 in this journal's tables, fixed by re-padding. The e2e tests
  (`UnreferencedAssets`, `AuthorizationMatrix`, `EndpointPolicy`) did not run: no Docker in the cloud container
  and Docker Desktop not running on the Mac. CI or `final` runs them.
- Phase 8: stacked on `impl/ingest-hardening-early-close` (0a8967b, above contract and tempdir); the rebase had no
  conflicts, the unit's diff did not change, and the targeted webapi tests and the test-e2e build pass on the new
  base. `unit_state` stays `stacked` in this file: the coordinator asked for no push after the PR opened, so the
  PR link is in the `done` message and `final` writes it here.
- Phase 8: no Docker and no Nix shell in the cloud container: e2e tests and markdownlint not run here.

## Owner decisions

- none

## Closeout

- root_cause: No route compared a project's assets in ingest storage with the file values in its data graph, so assets never attached, or attached only to deleted resources, could not be found (DEV-8096).
- investigation: The stored predicate is `knora-base:internalFilename`, not the plan's `fileValueHasFilename`; previous file value versions count as references through `previousValue*`.
- solution: `GET /v3/projects/{shortcode}/assets/unreferenced` (SystemAdmin) lists assets from `DspIngestClient.listAssets` that no live resource references, and marks those referenced only by deleted resources (`UnreferencedAssetsService`, `AssetReferencesRepo`). Final review: the 404 e2e case used shortcode `ffff`, the system project, and now uses `9999`.
- prevention: `UnreferencedAssetsServiceSpec`, `UnreferencedAssetsE2ESpec`, an `AuthorizationMatrixE2ESpec` row. A file value marked `isDeleted` on a live resource counts as a reference: a false negative, never a false orphan.
