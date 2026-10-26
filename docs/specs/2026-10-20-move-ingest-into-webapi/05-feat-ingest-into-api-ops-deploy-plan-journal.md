---
plan: docs/specs/2026-10-20-move-ingest-into-webapi/05-feat-ingest-into-api-ops-deploy-plan.md
target_repo: /Users/raitisveinbahs/work/ops-deploy
base_commit: c4d9d52e
branch: feat/ingest-under-api-path
started: 2026-10-26
problem: >
  dsp-api now serves ingest in-process under /ingest/, so ops-deploy must stop running dsp-ingest as its own
  swarm service in the DSP_iiif stack. The api service in DSP_svc takes over the asset and temp mounts
  (read-write), ingest's surviving settings under the INGEST_ prefix, ingest's memory budget and the
  ingest.<domain> Traefik route (proxied to /ingest/ via addPrefix); dsp-app's ingestUrl moves to the API host.
symptoms:
status: in-progress
---

# Execution Journal: 05-feat-ingest-into-api-ops-deploy-plan

## Repos

| repo       | base_commit | branch                     | merge_strategy | status      | pr |
|------------|-------------|----------------------------|----------------|-------------|----|
| ops-deploy | c4d9d52e    | feat/ingest-under-api-path | squash         | in-progress | —  |

## Phases

| phase | status      | phase_base          | review_fix_rounds |
|-------|-------------|---------------------|-------------------|
| 1     | reviewed    | ops-deploy@c4d9d52e | 0                 |

## Chunk queue

| id  | repo       | files | depends_on | checkboxes | acceptance | context | replaces |
|-----|------------|-------|------------|------------|------------|---------|----------|
| 1.1 | ops-deploy | roles/dsp-deploy/templates/docker-compose-svc.yml.j2 | — | "replace api-img with ingest-img/ingest-tmp volumes"; "mount /opt/img rw and /opt/tmp on api" | rendered svc files mount /opt/img rw and /opt/tmp on both branches; NFS volumes ingest-img/ingest-tmp rw | svc.yml.j2 § volumes, § api.volumes | — |
| 1.2 | ops-deploy | roles/dsp-deploy/templates/docker-compose-svc.yml.j2 | 1.1 | "add INGEST_* and SIPI_SENTRY_* settings"; "remove KNORA_WEBAPI_DSP_INGEST_BASE_URL/_EXTERNAL_BASE_URL/_ASSET_DIR" | api env has the INGEST_*/SIPI_SENTRY_* vars, keeps _AUDIENCE, lacks the three removed | svc.yml.j2 § api.environment | — |
| 1.3 | ops-deploy | roles/dsp-deploy/templates/docker-compose-svc.yml.j2, roles/dsp-deploy/defaults/main.yml, roles/dsp-deploy/templates/docker-compose-iiif.yml.j2, host_vars/vre-{prod,stage,rdu}-01.yml | 1.2 | "memory limit = API + DSP_API_INGEST_MEMORY, -XX:MaxRAM"; "rename DSP_INGEST_MEMORY_LIMIT to DSP_API_INGEST_MEMORY" | sample render: memory 12884901888, -XX:MaxRAM=8589934592 | svc.yml.j2 § JAVA_TOOL_OPTIONS, § deploy.resources; defaults § DSP_INGEST_* | — |
| 1.4 | ops-deploy | roles/dsp-deploy/templates/docker-compose-svc.yml.j2 | 1.3 | "add {{ STACK }}-ingest router with addprefix"; "add {{ STACK }}-api-ingest router" | rendered labels carry both routers + addprefix middleware | svc.yml.j2 § api.labels traefik | — |
| 1.5 | ops-deploy | roles/dsp-deploy/templates/app/conf/config.json.j2 | — | "ingestUrl = https://{{ DSP_API_HOST }}/ingest, drop ingest host from tracingCorsUrls" | rendered config.json parses, ingestUrl on API host | config.json.j2:12,35 | — |
| 1.6 | ops-deploy | — (verification only) | 1.1-1.5 | "render offline twice"; "docker compose config -q + jq"; "just lint dsp-deploy" | renders and checks pass | scratch render harness | — |

## Chunks

| id  | repo | status | commit(s) | summary | blocker |
|-----|------|--------|-----------|---------|---------|
| 1.1 | ops-deploy | complete | 93cfca9e | api mounts /opt/img rw and /opt/tmp; NFS volumes ingest-img/ingest-tmp (rw) replace api-img | none |
| 1.2 | ops-deploy | complete | 1af2618d | api env gains INGEST_STORAGE_*/BULK_MAX_PARALLEL/ALLOW_ERASE_PROJECTS and SIPI_SENTRY_*; drops DSP_INGEST_BASE_URL/_EXTERNAL_BASE_URL/_ASSET_DIR, keeps _AUDIENCE | none |
| 1.3 | ops-deploy | complete | 3de88cba | api limit = DSP_API_MEMORY_LIMIT + DSP_API_INGEST_MEMORY (bytes, quoted), -XX:MaxRAM pins heap; DSP_INGEST_MEMORY_LIMIT renamed in defaults, 3 host_vars and the interim iiif ingest service | none |
| 1.4 | ops-deploy | complete | 8f435f30 | api labels: {{ STACK }}-ingest router on DSP_INGEST_HOST with addprefix /ingest, {{ STACK }}-api-ingest router (api host, /ingest/, priority 100, no rate limit) | none |
| 1.5 | ops-deploy | complete | adc02eae | config.json ingestUrl = https://{{ DSP_API_HOST }}/ingest; ingest host dropped from tracingCorsUrls | none |
| 1.6 | ops-deploy | complete | — | offline renders (NFS + local) give memory 12884901888 and -XX:MaxRAM=8589934592; docker compose config -q exits 0 on svc and iiif renders; config.json parses; lint: no new violations vs base | none |

## Deferrals

- Phase 1 checkbox "Run `just lint dsp-deploy`; it passes" split: the no-new-violations part is ticked; the
  "passes" remainder stays `[ ]` because `origin/main` already fails with 239 violations. Session decision:
  accept the no-new-violations form, or schedule a lint cleanup outside this plan.

## Deviations

## Side findings

- The plan says compose accepts a byte integer for `deploy.resources.limits.memory`; `docker compose config`
  rejects it ("must be a string"), so the rendered value is quoted (3de88cba).
- Between Phase 1 and Phase 2 the router `{{ STACK }}-ingest` is defined by both the api service (DSP_svc) and the
  still-present ingest service (DSP_iiif) with different services; Traefik drops a router defined twice with
  different configs. Harmless as long as both phases ship in one PR (H1), but a Phase-1-only deploy would lose
  ingest.<domain>.
- The interim iiif `ingest` service reads the renamed `DSP_API_INGEST_MEMORY` (3de88cba) so every commit renders;
  Phase 2 deletes that service.

- `just lint dsp-deploy` fails on `origin/main` (c4d9d52e) with 239 pre-existing violations (var-naming on every
  upper-case variable, fqcn, risky-file-permissions, also in dsp-setup and inspect-stacks), and no CI job runs
  it. Phase 1 holds it as "no violation beyond those on base" instead.
- Phase 1 review (consistency, simplicity, dune, devops, security; 0 fix rounds). All four lenses that saw it
  flagged the `{{ STACK }}-ingest` router-name collision with the iiif ingest service (above): it is transient and
  only matters for a Phase-1-only deploy, so it stays. Phase 2 removes the iiif router; ship and deploy both phases
  together (H1). Not fixed by renaming because the plan names the router.
- Review: the api limit plus the old ingest limit are both live until Phase 2, which over-commits memory only in a
  Phase-1-only deploy. The `api-ingest` and `api-from-app` routers tie at priority 100; both have no middleware and
  target the same service, so the tie is harmless.
- Review, for dsp-api, not ops-deploy: check that the in-process `/ingest/` routes accept Bearer tokens only (the
  api sets a cookie domain, so cookie auth would make ingest endpoints CSRF-able). Also check that they enforce the
  `KNORA_WEBAPI_DSP_INGEST_AUDIENCE` audience.
- Review suggestions not taken: compute the derived memory byte values once in defaults; mark the `DSP_INGEST_*`
  variables the api still uses (mount paths, `DSP_INGEST_TMP_DIR`, `DSP_INGEST_INGEST_BULK_MAX_PARALLEL`) as
  shared, so a Phase 2 cleanup keeps them; drop the duplicate memory comment in defaults.
