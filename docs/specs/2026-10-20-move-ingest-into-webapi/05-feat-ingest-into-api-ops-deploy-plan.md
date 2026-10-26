---
title: "feat: Run ingest inside the dsp-api container in ops-deploy"
type: feat
date: 2026-10-26
author: "Raitis Veinbahs"
status: draft
prd: 01-move-ingest-into-webapi-PRD.md
repositories:
  - name: ops-deploy
    path: /Users/raitisveinbahs/work/ops-deploy
---

<!-- markdownlint-configure-file { "MD024": { "siblings_only": true } } -->

# feat: Run ingest inside the dsp-api container in ops-deploy

## Overview

The ops-deploy part of moving dsp-ingest into dsp-api under `/ingest/`. The `ingest` service in the `DSP_iiif`
stack is removed. The `api` service in the `DSP_svc` stack takes over:

- the asset and temp mounts, both read-write;
- ingest's remaining settings;
- a memory limit raised by ingest's limit;
- the `ingest.<domain>` Traefik route, with an `/ingest` prefix added.

The local-web `/ingest/metrics` route is removed: the API's existing metrics already cover the process ingest now
runs in.

dsp-app's `ingestUrl` points at the API host. The `ingest-rsync` role and the Makefile purge target follow the
moved volumes. ingest's SQLite data stays on disk but is no longer mounted.

## Problem Statement / Motivation

PRD REQ-4.4 says the deployment runs no separate ingest container. REQ-2.1, REQ-2.2 and REQ-2.4 say
`ingest.<domain>` keeps working permanently as a proxy to `/ingest/`. Today ops-deploy runs dsp-ingest as its own
swarm service (`roles/dsp-deploy/templates/docker-compose-iiif.yml.j2:171-261`). It has its own image, JWT
settings, SQLite volume, Traefik router and memory limit. dsp-api reaches it over HTTP
(`KNORA_WEBAPI_DSP_INGEST_BASE_URL=http://ingest:3340`). Once dsp-api serves ingest in-process, that container,
its settings and the HTTP hop have to go. The public host and the asset and temp storage have to reach the `api`
service instead.

## Proposed Solution

All changes are in `roles/dsp-deploy` (templates, defaults, tasks), `roles/ingest-rsync`, the `host_vars` and
`group_vars` that set ingest-only variables, and the two READMEs that mention dsp-ingest. The PR ships as one
change and deploys only with a dsp-api version that serves `/ingest/` (H1).

### `api` service (`docker-compose-svc.yml.j2`)

**Mounts.** Swarm volumes are immutable: `docker stack rm` keeps a named volume, and a redeploy reuses it with its
original `driver_opts`. Flipping the existing `api-img` volume (`DSP_svc_api-img`, NFS option `ro`) to `rw` would
therefore leave it read-only on every NFS host that already has it. Use new volume names instead:

- `ingest-img`: NFS `<NFS_PATH>/media/images`, `rw`, mounted at `{{ DSP_INGEST_IMG_MOUNT_PATH }}` (`/opt/img`).
  It replaces `api-img`. The stale `DSP_svc_api-img` volume object stays on the host; nothing references it.
- `ingest-tmp`: NFS `<NFS_PATH>/media/ingest-tmp`, `rw`, mounted at `{{ DSP_INGEST_TMP_MOUNT_PATH }}`
  (`/opt/tmp`). These are the same share and path the ingest service used, so files in flight (and rsync drops
  under `import/`) stay where they are.
- Non-NFS hosts: `{{ DSP_IIIF_DATA_IMG_DIR }}:/opt/img` without `:ro`, plus
  `{{ DSP_INGEST_TMP_DIR }}:/opt/tmp`.
- The `api-tmp:/tmp` mount is unchanged.

**Environment.** Add ingest's surviving settings under the `INGEST_` prefix the dsp-api plan fixes (A1):

```yaml
      # ingest slice (own config inside dsp-api, INGEST_ prefix)
      - 'INGEST_STORAGE_ASSET_DIR={{ DSP_INGEST_IMG_MOUNT_PATH }}'
      - 'INGEST_STORAGE_TEMP_DIR={{ DSP_INGEST_TMP_MOUNT_PATH }}'
      - 'INGEST_BULK_MAX_PARALLEL={{ DSP_INGEST_INGEST_BULK_MAX_PARALLEL }}'
      - 'INGEST_ALLOW_ERASE_PROJECTS={{ DSP_ALLOW_ERASE_PROJECTS }}'
      # passed through to the Sipi CLI child process, as on the ingest service (A1)
      - 'SIPI_SENTRY_DSN={{ DSP_IIIF_SENTRY_DSN }}'
      - 'SIPI_SENTRY_ENVIRONMENT={{ STACK }}'
      - 'SIPI_SENTRY_RELEASE={{ DSP_VERSIONS["api"] }}'
```

- `ALLOW_ERASE_PROJECTS`, already set on `api` from `DSP_ALLOW_ERASE_PROJECTS`, stays as is; it drives only
  dsp-api's own erase flag. `INGEST_ALLOW_ERASE_PROJECTS` takes over the ingest service's value from the same
  variable.
- `INGEST_EXECUTOR_CPU_SHARE` is not set; dsp-api's default `0.5` applies. `INGEST_SIPI_USE_LOCAL_DEV` is not set
  (default `false`), so the Sipi CLI, ffmpeg and ffprobe in the image are used.
- Dropped: `SERVICE_HOST`, `SERVICE_PORT`, `SERVICE_LOG_FORMAT` (dsp-api logs JSON through
  `DSP_API_LOG_APPENDER`), `JWT_AUDIENCE`, `JWT_ISSUER`, `JWT_SECRET`, `DB_JDBC_URL`, `DSP_API_URL`,
  `SIPI_USE_LOCAL_DEV`, ingest's own `JAVA_TOOL_OPTIONS`, ingest's OTEL/Pyroscope block (`DSP_svc_api`
  instrumentation covers the process). dsp-api ignores the old unprefixed ingest names, so none is carried over.
- Removed from `api`: `KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `KNORA_WEBAPI_DSP_INGEST_EXTERNAL_BASE_URL` and
  `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR`. dsp-api builds original-file links from the API's external base URL plus
  `/ingest` (REQ-2.3) and reads the asset directory from `INGEST_STORAGE_ASSET_DIR`.
- Kept: `KNORA_WEBAPI_DSP_INGEST_AUDIENCE`, so tokens keep the same audience (REQ-1.3).

**Memory.** The container limit grows by ingest's limit per host, and the heap stays at its current absolute size
(see Technical Considerations):

```yaml
      # the existing {% if DSP_API_INSTRUMENTATION %}-javaagent:...{% endif %} part stays at the end
      - 'JAVA_TOOL_OPTIONS={{ DSP_API_JAVA_TOOL_OPTIONS }} -XX:MaxRAM={{ DSP_API_MEMORY_LIMIT | human_to_bytes }} ...'
...
    deploy:
      resources:
        limits:
          memory: {{ (DSP_API_MEMORY_LIMIT | human_to_bytes) + (DSP_API_INGEST_MEMORY | human_to_bytes) }}
```

`DSP_API_INGEST_MEMORY` replaces `DSP_INGEST_MEMORY_LIMIT`. The default is `"2G"`, and the hosts that override
it today keep their values: `vre-prod-01`, `vre-stage-01` and `vre-rdu-01` set `"4G"`.

**Traefik labels on `api`:**

```yaml
      # ingest.<domain>/<path> -> api /ingest/<path> (permanent compatibility proxy)
      - traefik.http.routers.{{ STACK }}-ingest.rule=Host(`{{ DSP_INGEST_HOST }}`)
      - traefik.http.routers.{{ STACK }}-ingest.service={{ STACK }}-api
      - traefik.http.routers.{{ STACK }}-ingest.entrypoints=websecure
      - traefik.http.routers.{{ STACK }}-ingest.tls=true
      - traefik.http.routers.{{ STACK }}-ingest.tls.certresolver=leresolver
      - traefik.http.routers.{{ STACK }}-ingest.middlewares={{ STACK }}-ingest-addprefix
      - traefik.http.middlewares.{{ STACK }}-ingest-addprefix.addprefix.prefix=/ingest
      # /ingest/ on the api host: same no-rate-limit behaviour ingest has today
      - traefik.http.routers.{{ STACK }}-api-ingest.rule=Host(`{{ DSP_API_HOST }}`) && PathPrefix(`/ingest/`)
      - traefik.http.routers.{{ STACK }}-api-ingest.priority=100
      - traefik.http.routers.{{ STACK }}-api-ingest.service={{ STACK }}-api
      - traefik.http.routers.{{ STACK }}-api-ingest.entrypoints=websecure
      - traefik.http.routers.{{ STACK }}-api-ingest.tls=true
      - traefik.http.routers.{{ STACK }}-api-ingest.tls.certresolver=leresolver
```

- The ingest-host router has no host-level rate limit, the same as today. Because of `addprefix`, nothing outside
  `/ingest/` is reachable through `ingest.<domain>`.
- The `api-ingest` router keeps uploads from dsp-tools and other non-app clients out of the `api` router's
  10 req/s rate limit (`docker-compose-svc.yml.j2:157-159`). Today those clients reach the unthrottled ingest
  host. dsp-app requests already bypass the limit via the `Referer` router (priority 100).
- The ingest service's local-web `{{ STACK }}-ingest-metrics-nolog` router (`Path(/ingest/metrics)`), its
  Traefik service and its `-rewrite-path` middleware (`docker-compose-iiif.yml.j2:244-250`) go away with the
  service and are not recreated on `api`. dsp-api's `/ingest/metrics` returns the
  whole API process's metrics, the same as the metrics port's `/metrics`, which the API's existing scrape already
  collects; a second scrape would double-count (A3).
- Traefik forwards `Authorization` and `Content-Length` unchanged. Uploads must declare `Content-Length`: dsp-api
  answers `413` to a chunked body over 1 MiB (dsp-app and dsp-tools send it).

### Removals

- `ingest` service, including its Traefik labels (the `ingest.<domain>` router and the local-web
  `ingest-metrics-nolog` router with its middleware), and its `ingest-data`, `ingest-img` and `ingest-tmp` volume
  definitions in `docker-compose-iiif.yml.j2`.
- The `DSP_INGEST_IMAGE` pull and the `DSP_INGEST_DATA_DIR` directory creation in `tasks/main.yml`.
- Ingest-only defaults: `DSP_INGEST_IMAGE`, `DSP_INGEST_SERVICE_HOST`, `DSP_INGEST_SERVICE_PORT`,
  `DSP_INGEST_DATA_DIR`, `DSP_INGEST_DATA_MOUNT_PATH`, `DSP_INGEST_JAVA_TOOL_OPTIONS`,
  `DSP_INGEST_INSTRUMENTATION`.
- The `DSP_INGEST_INSTRUMENTATION` overrides in `group_vars/dsp_dev.yml`, `group_vars/dsp_stage.yml` and
  `host_vars/vre-prod-01.yml`.

### SQLite data: left on disk

The SQLite file is not deleted. Nothing mounts it any more, but it stays where it is: NFS
`<NFS_PATH>/media/ingest-data` (volume object `DSP_iiif_ingest-data`) or `{{ DSP_INGEST_DIR }}/data` on local
hosts. Leaving it costs nothing, it keeps an audit trail, and deleting it can't be undone. Removing it later is a
separate housekeeping step, outside this plan.

### Other consumers

- **dsp-app** `app/conf/config.json.j2`: `"ingestUrl": "https://{{ DSP_API_HOST }}/ingest"` (A2). Drop the
  `DSP_INGEST_HOST` entry from `tracingCorsUrls`, which the API-host entry now covers.
- **Makefile** `ingest-purge`: depends on `down-svc` instead of `down-iiif` and empties only the temp volume
  (`DSP_svc_ingest-tmp`, or `DSP_INGEST_TMP_DIR` locally). Its confirmation prompt keeps the same text, so
  `roles/restore/tasks/main.yml`'s `expect` still matches.
- **ingest-rsync** `templates/docker-compose.yml`: the external volume names change from `DSP_iiif_ingest-tmp` and
  `DSP_iiif_ingest-img` to `DSP_svc_ingest-tmp` and `DSP_svc_ingest-img`. The container paths stay the same, so
  the `rsync --rsync-path` usage in its README is unchanged.
- **Migration playbooks** `maintenance/migrations/INFRA-1404` and `INFRA-1413`: these are one-off and already run.
  They pin old `dsp-ingest` image tags that still exist on Docker Hub and rely on the SQLite volume. Leave them
  unchanged apart from a header note that they target the pre-move layout (`DSP_iiif_ingest-*` volumes) and must
  not be re-run as-is.
- **Docs**: `roles/dsp-deploy/README.md:34` and `versions/README.md:26` stop listing dsp-ingest as a separately
  published image.

## Technical Considerations

- **Deploy mechanics.** `tasks/main.yml` runs `make down` with the *old* Makefile whenever a compose file changes
  (lines 182-186), then templates the new files and runs `make up`. The old `DSP_iiif_ingest` service is therefore
  removed by `docker stack rm DSP_iiif`, so no orphaned ingest service is left behind. `ingest.<domain>` is
  unavailable for the length of the stack restart, the same as any deploy.
- **Heap stays fixed while the container grows.** `DSP_API_JAVA_TOOL_OPTIONS` is `-XX:MaxRAMPercentage=66`, so
  raising the cgroup limit alone would grow the heap by 66% of the extra memory. That would defeat the PRD's intent
  ("conversions have headroom without touching the API's heap", REQ-3.4). Pinning `-XX:MaxRAM` to the old limit
  makes the JVM size its heap from the old value, and the cgroup limit grows. `DSP_API_JAVA_TOOL_OPTIONS` itself
  stays unchanged. `human_to_bytes` is an ansible filter, so the template renders byte integers, which both
  `-XX:MaxRAM` and compose's `memory` accept.
- **Container user.** Both images run as root. `modules/ingest/BUILD.bazel:228` says root is needed because Sipi
  reads NFS assets owned by orchestrator-controlled uids, and the dsp-api plan keeps knora-api running as root
  (A4). The `api` service sets no `user:`, so it writes the NFS shares with the same identity ingest used.
- **Healthcheck.** The ingest healthcheck (`/sbin/sipi health --port 3340`) goes away with the service. The `api`
  healthcheck is unchanged.
- **Metrics scrape.** The ingest container's `grafana.metrics.*` labels and its local `/ingest/metrics` router go
  with it. They scraped only JVM and process series (`up`, `jvm_memory_*`, `process_cpu_seconds_total`), which are
  now the api JVM's and already scraped via `/api/metrics`. No monitoring is added, per the user's instruction.
- **Security.** The ingest host gains no new reachable surface, because `addprefix` confines it to `/ingest/*`.
  The new `api-ingest` router exempts only `/ingest/` paths from the rate limit, and those paths were unthrottled
  before.

## Implementation Phases

### Phase 1: api service takes over ingest

#### ops-deploy

- [ ] `docker-compose-svc.yml.j2`: replace the `api-img` NFS volume with new `ingest-img` (`rw`, `media/images`) and
  `ingest-tmp` (`rw`, `media/ingest-tmp`) volumes
- [ ] `docker-compose-svc.yml.j2`: mount `/opt/img` read-write and `/opt/tmp` on `api` for both the NFS and the
  bind-mount branches
- [ ] `docker-compose-svc.yml.j2`: add the `INGEST_*` and `SIPI_SENTRY_*` settings listed under Proposed Solution to the
  `api` environment
- [ ] `docker-compose-svc.yml.j2`: remove `KNORA_WEBAPI_DSP_INGEST_BASE_URL`,
  `KNORA_WEBAPI_DSP_INGEST_EXTERNAL_BASE_URL` and `KNORA_WEBAPI_DSP_INGEST_ASSET_DIR` from `api`, keeping
  `KNORA_WEBAPI_DSP_INGEST_AUDIENCE`
- [ ] `docker-compose-svc.yml.j2`: set the `api` memory limit to `DSP_API_MEMORY_LIMIT + DSP_API_INGEST_MEMORY` (bytes)
  and append `-XX:MaxRAM=<DSP_API_MEMORY_LIMIT bytes>` to `JAVA_TOOL_OPTIONS`
- [ ] `docker-compose-svc.yml.j2`: add the `{{ STACK }}-ingest` router on `DSP_INGEST_HOST` with the
  `{{ STACK }}-ingest-addprefix` (`/ingest`) middleware, targeting `{{ STACK }}-api`
- [ ] `docker-compose-svc.yml.j2`: add the `{{ STACK }}-api-ingest` router
  (`Host(DSP_API_HOST) && PathPrefix(/ingest/)`, priority 100, no rate limit)
- [ ] `defaults/main.yml`: rename `DSP_INGEST_MEMORY_LIMIT` to `DSP_API_INGEST_MEMORY` (default `"2G"`) and update the
  overrides in `host_vars/vre-prod-01.yml`, `host_vars/vre-stage-01.yml` and `host_vars/vre-rdu-01.yml`
- [ ] `app/conf/config.json.j2`: set `ingestUrl` to `https://{{ DSP_API_HOST }}/ingest` (per A2) and drop the ingest
  host from `tracingCorsUrls`
- [ ] Render `docker-compose-svc.yml.j2` and `app/conf/config.json.j2` offline twice, once with `DSP_NFS_PATH` defined
  and once without, using `ansible localhost -c local -i localhost, -m ansible.builtin.template -a
  "src=... dest=<scratch>/..." -e @roles/dsp-deploy/defaults/main.yml -e @<scratch>/render-vars.yml` from the `.ansible-venv`.
  The vars file supplies `STACK`, `DSP_DOMAIN`, `DSP_VERSIONS`, `ENVIRONMENT`, `DSP_PROXY_NET`, `DSP_JWT_TOKEN`,
  `DSP_DB_PASSWORD`, `DSP_RELEASE` and `DSP_API_MEMORY_LIMIT: "8G"`, `DSP_API_INGEST_MEMORY: "4G"`. Expect a memory
  limit of `12884901888` and `-XX:MaxRAM=8589934592`
- [ ] Run `docker compose -f <scratch>/svc-nfs.yml config -q` and `docker compose -f <scratch>/svc-local.yml config -q`
  on the rendered files; both exit 0. Run `jq . <scratch>/config.json`; it parses
- [ ] Run `just lint dsp-deploy`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

### Phase 2: remove the ingest service and follow-on references

#### ops-deploy

- [ ] `docker-compose-iiif.yml.j2`: remove the `ingest` service (with its `ingest.<domain>` and local-web
  `ingest-metrics-nolog` routers and middleware) and the `ingest-data`, `ingest-img` and `ingest-tmp` volume definitions
- [ ] `tasks/main.yml`: remove the `DSP_INGEST_IMAGE` pull and the `DSP_INGEST_DATA_DIR` directory creation (keep
  `DSP_INGEST_DIR` and `DSP_INGEST_TMP_DIR`)
- [ ] `defaults/main.yml`: remove `DSP_INGEST_IMAGE`, `DSP_INGEST_SERVICE_HOST`, `DSP_INGEST_SERVICE_PORT`,
  `DSP_INGEST_DATA_DIR`, `DSP_INGEST_DATA_MOUNT_PATH`, `DSP_INGEST_JAVA_TOOL_OPTIONS` and `DSP_INGEST_INSTRUMENTATION`
- [ ] Remove the `DSP_INGEST_INSTRUMENTATION` overrides from `group_vars/dsp_dev.yml`, `group_vars/dsp_stage.yml` and
  `host_vars/vre-prod-01.yml`
- [ ] `Makefile.j2`: make `ingest-purge` depend on `down-svc` and empty only the temp volume (`DSP_svc_ingest-tmp`, or
  `DSP_INGEST_TMP_DIR`), keeping the prompt text unchanged; drop `INGEST_DATA_VOLUME`
- [ ] `roles/ingest-rsync/templates/docker-compose.yml`: change the external volume names to `DSP_svc_ingest-tmp` and
  `DSP_svc_ingest-img`
- [ ] `maintenance/migrations/INFRA-1404/migrate-sizes.yml` and `INFRA-1413/migrate-mime-types.yml`: add a header note
  that they target the pre-move layout and must not be re-run as-is
- [ ] `roles/dsp-deploy/README.md` and `versions/README.md`: stop listing dsp-ingest as an image published with dsp-api
- [ ] Run `git grep -n -e DSP_INGEST_IMAGE -e DSP_INGEST_SERVICE_PORT -e DSP_INGEST_DATA -e DSP_INGEST_MEMORY_LIMIT -e
  DSP_INGEST_INSTRUMENTATION -e DSP_iiif_ingest -e 'ingest:3340' -e ingest-metrics-nolog` over the repository, excluding
  `maintenance/migrations/`, `versions/RELEASE.json` and `docs/`; it returns nothing
- [ ] Render `docker-compose-iiif.yml.j2` and `Makefile.j2` offline as in Phase 1, for both the NFS and the local
  branches; `docker compose -f <scratch>/iiif-*.yml config -q` exits 0, and the rendered iiif file has no `ingest`
  service or volume and no `ingest-metrics-nolog` router
- [ ] Run `just lint dsp-deploy`; it passes
- [ ] Run `just lint ingest-rsync`; it passes
- [ ] Phase review: adversarial review of this phase's commits; verified findings fixed before the next phase starts

## Human Actions

| Id | Action | Who | When | Why not the agent |
| --- | --- | --- | --- | --- |
| H1 | Merge the ops-deploy PR only once a dsp-api release that serves `/ingest/` (with Sipi/ffmpeg in the image) exists and the PR's env var names, `ingestUrl` and the dsp-app version match it. From the merge on, every deploy from `main` (dev auto-deploys on `versions/RELEASE-DEV.json` changes) needs an `api` version that serves `/ingest/` | DSP release owner | after ship | Merge and release ordering are the owner's decision |
| H2 | Deploy the release carrying that dsp-api version to dev, stage and prod (`dsp.yml` via Jenkins/`make deploy-to-*`). Then check that `https://ingest.<domain>/health` and `https://api.<domain>/ingest/health` return 200 and a dsp-app upload succeeds | Ops | after ship | Running playbooks against servers needs credentials and production approval |

## Acceptance Criteria

- [ ] The rendered `DSP_iiif` stack has no `ingest` service and no ingest volumes; the deploy pulls no `dsp-ingest`
  image
- [ ] The rendered `api` service mounts `/opt/img` read-write and `/opt/tmp` from the same storage ingest used, on both
  the NFS and the bind-mount branches, with NFS volumes under new names
- [ ] The `api` environment carries `INGEST_STORAGE_ASSET_DIR`, `INGEST_STORAGE_TEMP_DIR`, `INGEST_BULK_MAX_PARALLEL`
  and `INGEST_ALLOW_ERASE_PROJECTS` and keeps `KNORA_WEBAPI_DSP_INGEST_AUDIENCE`; it has no
  `KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `..._EXTERNAL_BASE_URL` or `..._ASSET_DIR`, no `INGEST_SIPI_USE_LOCAL_DEV`, and
  none of ingest's unprefixed storage, JWT, DB, service host/port or `DSP_API_URL` settings
- [ ] The `api` memory limit equals the old API limit plus the host's former ingest limit, and `-XX:MaxRAM` keeps the
  heap at its former size
- [ ] Traefik routes `ingest.<domain>/<path>` to `api` at `/ingest/<path>` with method, query, body and `Authorization`
  untouched (REQ-2.1, REQ-2.2); `ingest.<domain>/health` reaches `/ingest/health` (REQ-2.4)
- [ ] `/ingest/` on the API host is not subject to the `api` router's rate limit
- [ ] No local-web `/ingest/metrics` router remains; API metrics, which include ingest, are scraped as before
- [ ] dsp-app's rendered `config.json` has `ingestUrl` on the API host under `/ingest`
- [ ] The ingest SQLite data is left on disk and no longer mounted
- [ ] `ingest-rsync` and `make ingest-purge` use the volumes the `api` service now mounts
- [ ] `just lint dsp-deploy` and `just lint ingest-rsync` pass; the rendered compose files pass `docker compose config
  -q`

## Dependencies & Risks

**Contract with the parallel plans** (A1 to A5 are settled; only the A1 Sentry point is checked before H1):

- **A1. Ingest env var names (dsp-api plan, settled).** dsp-api reads ingest's settings under an `INGEST_` prefix:
  `INGEST_STORAGE_ASSET_DIR`, `INGEST_STORAGE_TEMP_DIR` (both mounted read-write), and optionally
  `INGEST_BULK_MAX_PARALLEL`, `INGEST_ALLOW_ERASE_PROJECTS`, `INGEST_EXECUTOR_CPU_SHARE` (default `0.5`).
  `INGEST_SIPI_USE_LOCAL_DEV` must stay unset or `false`. The old unprefixed ingest names are ignored, and
  unprefixed `ALLOW_ERASE_PROJECTS` drives only dsp-api's own flag. `KNORA_WEBAPI_DSP_INGEST_AUDIENCE` stays;
  `KNORA_WEBAPI_DSP_INGEST_BASE_URL`, `..._EXTERNAL_BASE_URL` and `..._ASSET_DIR` are removed. Still to verify:
  the dsp-api plan does not say how the Sipi CLI gets `SIPI_SENTRY_*`. This plan assumes the CLI child process
  inherits the `api` container's environment, as it did in the ingest container, and keeps passing them through.
  If dsp-api drops them from the child environment, or the CLI no longer reports to Sentry, remove the three
  variables.
- **A2. `ingestUrl` shape (dsp-app plan, settled).** `https://{{ DSP_API_HOST }}/ingest`, with no trailing slash;
  dsp-app keeps the `ingestUrl` key and appends paths such as `/projects/...`.
- **A3. Health and metrics (dsp-api plan, settled).** dsp-api serves `GET /ingest/health` on port 3333, `200`
  when healthy. `/ingest/metrics` returns the whole API process's metrics, identical to `:3339/metrics`, so no
  separate ingest scrape or local router is kept.
- **A4. Image user (dsp-api plan, settled).** The knora-api image runs as root and ships `/sbin/sipi`, ffmpeg and
  ffprobe, so the Sipi CLI can read and write NFS assets owned by orchestrator-controlled uids.
- **A5. Memory semantics (settled).** `host_vars` on `origin/main` set `DSP_INGEST_MEMORY_LIMIT: "4G"` on
  `vre-prod-01`, `vre-stage-01` and `vre-rdu-01`, so the API container grows by 4G there and by 2G elsewhere.
  `DSP_API_JAVA_TOOL_OPTIONS` stays unchanged, and `-XX:MaxRAM=<old limit>` keeps the heap at today's size;
  without it, `MaxRAMPercentage=66` would grow the heap with the container.
- **A6. OOM behaviour (REQ-3.4).** Making the kernel OOM-kill the conversion subprocess rather than the JVM is a
  dsp-api concern, for example through a raised `oom_score_adj` on child processes, which needs no capability. If
  the dsp-api plan needs anything from the container (capabilities, `oom_score_adj` on the service), note that
  swarm stack deploy ignores some of these keys. Add them here if required.

**Other risks:**

- **Version coupling.** After the merge, deploying any environment pinned to an `api` version without `/ingest/`
  breaks uploads and `ingest.<domain>`. One option is a version-guard `assert` in `tasks/main.yml`; it is not in
  this plan, because the minimum version is not known yet.
- **Stale volume options.** The plan uses new volume names because existing swarm volumes keep their original
  `driver_opts`. If anyone renames the volumes back to `api-img`, NFS hosts silently stay read-only.
- **Rsync drop location.** Operators using `ingest-rsync` on NFS hosts get the new volume names after the role is
  re-applied (`ingest-rsync.yml`). The underlying NFS path is unchanged.
- **Out of scope.** Deleting the old SQLite data and the stale `DSP_iiif_ingest-*` and `DSP_svc_api-img` volume
  objects; the legacy Jenkins `ingest_version` badge (`jenkins/jobs/dsl/utilities/Descriptions.groovy:16`, already
  stale like the `cpe` badge); historical `ingest` keys in `versions/RELEASE.json`.

## Success Metrics

- Rendered `api` memory limit for the sample vars (`8G` + `4G`): `12884901888` bytes; `-XX:MaxRAM=8589934592`.
- After H2: no `DSP_iiif_ingest` service on any host (`docker service ls`), and `ingest.<domain>/health` and
  `api.<domain>/ingest/health` return HTTP 200.

## References

- `roles/dsp-deploy/templates/docker-compose-iiif.yml.j2:8-35,171-261` (ingest volumes and service)
- `roles/dsp-deploy/templates/docker-compose-svc.yml.j2:8-21,44-69,100,149-201` (api mounts, env, routers, memory)
- `roles/dsp-deploy/defaults/main.yml:27-28,75-89`
- `roles/dsp-deploy/tasks/main.yml:43-49,80-82,182-200`
- `roles/dsp-deploy/templates/Makefile.j2:1-11,105-110`
- `roles/dsp-deploy/templates/app/conf/config.json.j2:12,35`
- `roles/ingest-rsync/templates/docker-compose.yml`
- `roles/restore/tasks/main.yml:12-17`
- `maintenance/migrations/INFRA-1404/migrate-sizes.yml`, `maintenance/migrations/INFRA-1413/migrate-mime-types.yml`
- `host_vars/vre-prod-01.yml:13-15,31`, `host_vars/vre-stage-01.yml:13,20`, `host_vars/vre-rdu-01.yml:21,28`
- dsp-api `modules/ingest/BUILD.bazel:228`, `modules/webapi/BUILD.bazel:238` (both images run as root)
- dsp-api `modules/ingest/src/main/resources/application.conf` (ingest's current env var names)
- Traefik AddPrefix middleware: <https://doc.traefik.io/traefik/middlewares/http/addprefix/>
