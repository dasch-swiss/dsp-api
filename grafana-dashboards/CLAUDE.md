# CLAUDE.md — grafana-dashboards

Guidance for working in this folder. Read `README.md` first for the Git Sync wiring; this file
covers how to author/edit dashboards and the non-obvious gotchas.

## What this folder is

Grafana dashboards for DSP, version-controlled here and pulled into Grafana Cloud via **Git Sync**
(native provisioning). This repo is the source of truth; synced dashboards are **read-only** in the
Grafana UI. Each subdirectory (`sipi/`, …) becomes a top-level Grafana folder; `_folder.json` sets
its display title. Files are Kubernetes-style resources (`apiVersion`, `kind`, `metadata.name`,
`spec`); `metadata.name` is the **UID** and must stay stable.

## Editing workflow

- Dashboards use the **v2 schema** (`dashboard.grafana.app/v2`): panels under `spec.elements`
  (keyed `panel-<id>`), arranged by `spec.layout`, variables under `spec.variables`.
- To seed from an existing Grafana dashboard: `get_dashboard_by_uid` (Grafana MCP), then wrap its
  body in the envelope (`apiVersion`/`kind`/`metadata.name`/`spec`) and strip `apiVersion`/`isV2`/
  `meta`. See `sipi/sipi-iiif-media-server.json` for the shape.
- Datasources are referenced by **UID** in `datasource.name` (e.g. `grafanacloud-prom`).
- **Always validate before committing:** `jq . <file>`, and check every `layout` `ElementReference`
  name matches an `elements` key and each panel `id`. Verify queries live with the `query_prometheus`
  MCP tool against `grafanacloud-prom`.
- Use the **`grafana-cloud`** MCP variant, not `grafana-local` (local Grafana at :3000 is usually not
  running). `list_provisioning_repositories` returns 403 on cloud (namespace), so pre-merge preview
  isn't available — rely on `query_prometheus` + a merge to `main`.
- **Git Sync only pulls `main`.** Nothing reaches Grafana from a feature branch; it must be merged.

## SIPI metrics gotchas (hard-won)

SIPI emits telemetry through **two pipelines** with colliding metric names:

| | legacy Prometheus scrape | new OTLP pipeline |
| --- | --- | --- |
| `service_name` | `DSP_iiif_iiif` | `sipi` (`service_namespace=dsp`) |
| `job` | `service/metrics` | `dsp/sipi` |
| env label | `environment` (dev/stage/prod) | `environment` **and** `deployment_environment_name` (`vre-dev-01`) |

- **Always scope SIPI queries with `service_name="sipi"`** to select the OTLP data and avoid
  double-counting against the legacy scrape. The dashboard is built entirely on the OTLP set.
- **Request latency/throughput = `http_server_request_duration_seconds_*`** (labels `http_route`,
  `http_response_status_code`, `http_request_method`), NOT `sipi_request_duration_seconds`. That
  generic name is shared with Fuseki etc., so `service_name="sipi"` is mandatory to isolate SIPI.
- **Version is a resource attribute, not a metric.** There is no `sipi_build_info` on OTLP. Use
  `target_info{service_name="sipi"}` → `service_version` (e.g. `topk(1, timestamp(target_info{…}))`
  with `legendFormat={{service_version}}`, `textMode=name` for "last seen"). `target_info` carries
  **only** `deployment_environment_name`, not `environment`.
- **SIPI runs on every `dsp`-group host, all on OTLP**: the primary VRE boxes (`vre-dev-01`,
  `vre-stage-01`, `vre-prod-01`) plus LS/demo/test and ~30 RDU boxes — and the RDU boxes inherit
  `environment="prod"` through the Ansible group hierarchy. Filtering only by
  `environment=~"$environment"` silently sums many hosts, and a fuzzy
  `deployment_environment_name=~".*prod.*"` matches both `vre-prod-01` and `dsp-ls-prod-01`.
- **Scope every SIPI OTLP query to the primary VRE host** with
  `deployment_environment_name=~"vre-$environment-01"` — the resource attribute is the Ansible
  inventory hostname (injected via `SIPI_SENTRY_ENVIRONMENT` in ops-deploy). The env variable is a
  fixed custom list `dev,stage,prod` (value = short name) and only feeds that host pattern.
- **Not bridged to OTLP** (deliberately, per the Rust source — permanently empty on the OTLP path):
  `sipi_rejected_connections_total`, `sipi_waiting_connections`, `sipi_rate_limit_decisions_total`
  (the `{action}` family; OTLP splits it into `allowed`/`rejected`/`near_limit`/`shadow_rejected`),
  `sipi_essentials_hash_mismatch_total`, `sipi_read_shape_fast_path_total`. These live in the
  **"Empty on OTLP (investigate)"** row so gaps stay visible. If one starts flowing, move it into the
  relevant section (as was done for the decode-memory estimate histogram).
- **Authoritative metric/span inventory** is the SIPI Rust shell:
  `~/_github.com/dasch-swiss/sipi/src/server-rs/src/metrics.rs` (instrument names + a `NOT_BRIDGED`
  list) and `telemetry.rs` (resource attributes / version chain). OTLP instrument names are dotted
  (`sipi.cache.hits`) and normalize to `sipi_cache_hits_total` in Prometheus.

## Non-SIPI metrics on this dashboard

- **dsp-api auth route** (`tapir_request_duration_seconds_*`, service DSP_svc_api): SIPI calls
  `/admin/files/{projectShortcode}/{filename}` for a permission check on every IIIF request. This
  metric has **no `_bucket`** (only `count`/`sum`) → average latency only, no percentiles. It has no
  `deployment_environment_name`; scope it by `instance=~"dasch-vre-$environment-01"` (dsp-api also
  answers on the LS box, so `environment` alone over-counts).
- **Container CPU/mem** (cAdvisor, `container_*`, `job=integrations/docker`): use exact
  `service="DSP_iiif_iiif"` — `service=~"DSP_iiif.*"` also matches `DSP_iiif_ingest`. These carry
  `environment` and short `instance` names (`dasch-vre-dev-01`). Panels are scoped to the primary VRE
  host `dasch-vre-$environment-01`.
- **`node_*`** host metrics have **no `environment` label** (keyed by `instance` hostname), so they
  can't be filtered by the env variable directly — join via `and on(instance)` against a
  SIPI-present series, or match the host by name. (No node panels are on the dashboard currently.)

## Fuseki metrics gotchas

The Fuseki triplestore emits OTLP through its JVM's OTel agent under **`service_name="DSP_db_db"`**
(`fuseki/` dashboard). Unlike sipi:

- **No `deployment_environment_name`** on `target_info` (and no `environment` either) — instances are
  distinguished only by **`host_name`** (`dasch-vre-{dev,stage,prod}-01`, plus dev-02/03 and an
  `its-bs-dasch-ls-test-01` box). Key/legend version panels by `host_name`, not an env variable.
- **Version** is the OTLP `service.version` resource attribute, baked into the image from
  `FUSEKI_DIST_VERSION` (`MODULE.bazel`). Read it the sipi way:
  `topk(1, timestamp(target_info{service_name="DSP_db_db"}))` / `timestamp(target_info{…})` with
  `legendFormat={{host_name}} — {{service_version}}`, `textMode=name`. It is **empty until an image
  carrying the attribute is deployed** (this feature's first deploy).
- **Dual-pipeline collision (same as sipi):** `jvm_*` / `http_server_request_duration_seconds_*` for
  this service exist on both the OTLP path (`job="DSP_db_db"`) and a legacy scrape
  (`job="service/metrics"`), and carry **no `host_name`** (only `instance`/`job`/`environment`). Scope
  by `job="DSP_db_db"` and key by `instance`/`environment` if you add JVM/HTTP panels — the current
  dashboard is version-only to avoid this.

## Conventions

- Do not use "Knora" in human-readable text (titles/descriptions) — repo-wide convention.
- After editing markdown here, run the `/fix-markdownlint` skill.
