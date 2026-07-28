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
  **only** `deployment_environment_name`, not `environment` — filter it with
  `deployment_environment_name=~".*$environment.*"`.
- **Env variable** is a fixed custom list `dev,stage,prod` (value = short name). SIPI OTLP metrics
  filter by `environment=~"$environment"`; `target_info` by `deployment_environment_name`. As of this
  writing **prod is not yet on OTLP** (only `vre-dev-01`, `vre-stage-01`, a `dsp-ls-test-01` box), so
  selecting prod shows no SIPI data until it migrates.
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
  metric has **no `_bucket`** (only `count`/`sum`) → average latency only, no percentiles. Filter by
  `environment=~"$environment"` (it uses `environment`, dev/stage/prod).
- **Container CPU/mem** (cAdvisor, `container_*`, `job=integrations/docker`): use exact
  `service="DSP_iiif_iiif"` — `service=~"DSP_iiif.*"` also matches `DSP_iiif_ingest`. These carry
  `environment` and short `instance` names (`dasch-vre-dev-01`). Panels are scoped to the primary VRE
  host `dasch-vre-$environment-01`.
- **`node_*`** host metrics have **no `environment` label** (keyed by `instance` hostname), so they
  can't be filtered by the env variable directly — join via `and on(instance)` against a
  SIPI-present series, or match the host by name. (No node panels are on the dashboard currently.)

## Conventions

- Do not use "Knora" in human-readable text (titles/descriptions) — repo-wide convention.
- After editing markdown here, run the `/fix-markdownlint` skill.
