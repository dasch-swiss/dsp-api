---
plan: docs/specs/2026-10-06-resource-fair-landing-metadata/03-feat-resource-public-view-plan.md
target_repo: /Users/raitisveinbahs/work/dsp-api
base_commit: 61e76b065
branch: feature/dev-7420-resource-fair-landing-metadata
started: 2026-10-06
problem: >
  Resource and value ARKs resolve to dsp-app's client-rendered resource page, which serves the same Angular
  shell to every request: no JSON-LD, no Dublin Core, no Signposting, so FAIR assessors and harvesters find
  nothing machine-readable for a resource (DEV-7420). S1 lays the foundation in dsp-api: resolving a
  shortcode/resource id/version into a resource IRI and reading the resource strictly as the anonymous user,
  with an access level that decides what may be published.
status: complete
---

# Execution Journal: 03-feat-resource-public-view-plan

## Chunk queue

| id | files | depends_on | checkboxes | acceptance | context | replaces |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | (none; dsp-repository `just fair-check`) | — | "Run … `just fair-check` … record both baselines" | F-UJI 3.5.0 scores for an 0803 and an 0868 resource ARK recorded in the plan index's Success Metrics | dsp-repository `justfile` § fair-check | — |
| 2 | `config/AppConfig.scala`, `resources/application.conf`, `config/AppConfigSpec.scala` | — | "Add `DspAppConfig`…", "Log a warning…", "config validation rejects…" | `app.dsp-app { url, internal-url, shell-cache-ttl }` loads, empty by default, validated; warning when url empty | `AppConfig.scala` § `DspIngestConfig`, validate block ~L267-290; application.conf § dsp-ingest | — |
| 3 | `slice/export/fair/ResourceLandingRef.scala` (new), its spec | — | "Add a typed `ResourceLandingRef`…", "Unit tests for ref parsing…" | raw strings → shortcode, resource id, optional `VersionDate`, or `Left` | `slice/api/v2/Models.scala` § `VersionDate`; `ResourceIri` | — |
| 4 | `slice/export/api/service/ExportService.scala`, new helper beside it | — | "Extract `findDescriptionProperty` and `fileLinkOf`…" | helper used by `ExportService`, `ExportServiceSpec` unchanged and green | `ExportService.scala` § fileLinkOf/findAssetInfo/findDescriptionProperty | — |
| 5 | `slice/export/fair/PublicResourceViewService.scala` (new), `ExportApiModule.scala`, spec | 3, 4 | "Add `PublicResourceViewService`…", "Classify the access level…", "Wire…", access-table and NotPublic tests | anonymous read; NotPublic mapping; Full Open / Restricted level; tests on incunabula fixtures | `ReadResourcesServiceLive.scala` § getResourcesWithDeletedResource; `KnoraSystemInstances.Users.AnonymousUser`; design § Access | — |

## Chunks

| id | status | commit(s) | summary | blocker |
| --- | --- | --- | --- | --- |
| 1 | complete | — (plan index) | F-UJI 3.5.0 baselines: 0803 and 0868 resource ARKs both 3/24 on production | none |
| 2 | complete | bba7f1473 | `app.dsp-app { url, internal-url, shell-cache-ttl }`, empty = off, validated, startup warning | none |
| 4 | complete | a17d6894b | `ResourceFileLinks` extracted from `ExportService`; link built per file value; export output unchanged | none |
| 3 | complete | b629e7291 | `ResourceLandingRef` parses shortcode/id/version into a `ResourceIri` + `VersionDate` | none |
| 5 | complete | 36722316c | `PublicResourceViewService`: anonymous read, NotPublic mapping (any deletion, before creation, forbidden/not found), FullOpen/Restricted, >1 file value → no file | none |
| 6 | complete | 2cb0058f0, b39fd5080, 86fa0e3dc, 172773468, 88d092da9 | review fixes: access level counts every file value (critical), URL validation, tests, `ResourceFileLinks` → `fair`, config docs, ARCH-MAP | none |

## Deferrals

- dsp-app settings for the local docker-compose stack → S4 (checkbox in `06-feat-resource-landing-plumbing-plan.md`)
- Access-table and NotPublic cases against real incunabula data (unit tests have no triplestore) → S3 E2E,
  added as a checkbox in `05-feat-resource-metadata-representations-plan.md`

## Side findings

- dsp-repository's `just fair-check` sets a scratch `DOCKER_CONFIG`, which drops the active Docker context; with a
  non-default context (Docker Desktop's `~/.docker/run/docker.sock`) it fails with "Cannot connect to the Docker
  daemon" unless `DOCKER_HOST` is exported.
- Parallel workers on one Bazel test target (`//modules/webapi:test`) block each other: one worker's half-done
  edit stops the others' targeted tests from compiling. Treat chunks in the same target as sequential.
- `--test_filter` is a regex; an alternation (`.*(A|B|C).*`) silently skipped one of three specs. Run each spec's
  filter separately and check the suite names in `test.log`.

## Closeout

- root_cause: dsp-app's resource route is a client-rendered shell, so nothing machine-readable exists for a
  resource; dsp-api had no read path that resolves a dsp-app resource URL and decides, as the anonymous user,
  what about the resource may be published.
- investigation: the export's OAI read skips retrieval checks and hardcodes "Full Open Access", so it could not be
  reused; `getResourcesWithDeletedResource` is the read that takes a version. Restricted-view file values come
  back with full file details, so the decision to expose a file has to be made in our code, not by the read. The
  first version judged the level from the resource alone when several file values existed; the security review
  caught it. Unit tests cannot load fixtures (no triplestore), so real-data cases moved to S3's E2E tests.
- solution: `app.dsp-app` settings (empty = off); `ResourceLandingRef` parses the URL parts; `ResourceFileLinks`
  extracted from `ExportService`; `PublicResourceViewService` reads as anonymous, maps forbidden/missing/deleted/
  before-creation to not public, counts every file value for the access level, and exposes `openFile` only for a
  single full-open file value.
- prevention: mutation-checked tests for the config validation and the multi-file access level; the S2 plan
  forbids serialising `values` wholesale and takes file details only from `openFile`.
