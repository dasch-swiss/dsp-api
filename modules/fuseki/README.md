# Apache Jena Fuseki — DaSCH image

Custom container image for [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/) used as the triplestore for dsp-api. Built with Bazel (`//modules/fuseki`, rules_oci).

Published to Docker Hub as `daschswiss/apache-jena-fuseki:<dsp-release-version>` (plus `:latest`). The
image is versioned by release-please, like `knora-api`/`dsp-ingest` — the tag is the DSP release/git
version, not the Jena version.

## What this image is

Fuseki is a SPARQL 1.1 server backed by Apache Jena TDB. This image extends the upstream Fuseki distribution with:

- A pre-configured `dsp-repo` dataset (see `dsp-repo.ttl`)
- A `shiro.ini` with password-based access control
- A healthcheck script that verifies Fuseki is running and the `dsp-repo` dataset exists
- OpenTelemetry Java agent and Pyroscope extension for observability

## Pre-configured dataset

The `dsp-repo` dataset is created automatically on first start from `dsp-repo.ttl`. It is mounted at `/fuseki/configuration/dsp-repo.ttl` inside the container. The data volume is persisted at `/fuseki`.

## Configuration

| Variable | Description | Default |
| --- | --- | --- |
| `ADMIN_PASSWORD` | Fuseki admin password | value from `shiro.ini` |
| `JVM_ARGS` | JVM heap and flags | `-Xmx4G` |
| `REBUILD_INDEX_OF_DATASET` | Dataset name to rebuild Lucene index for | unset |

## Updating Jena/Fuseki

The Jena dist (jar) version is single-sourced as `FUSEKI_DIST_VERSION` in `MODULE.bazel`. To bump:

1. Find the new version on [jena.apache.org/download](https://jena.apache.org/download/)
2. In `MODULE.bazel`, update `FUSEKI_DIST_VERSION` (e.g. `5.6.0`) and the `@fuseki_dist` `http_archive`'s
   `sha256` (the checksum of the new tarball). The tarball `urls` and `strip_prefix` are derived from
   `FUSEKI_DIST_VERSION`, so they update automatically.

That is the only change. `FUSEKI_DIST_VERSION` flows to `modules/fuseki/BUILD.bazel` (the image's
`FUSEKI_VERSION` env + the OTLP `service.version` resource attribute) and to the `/version` report, all
via `@dsp_image_versions`. The image **tag** is not touched — it is the DSP release version
(release-please), and `docker-compose.yml` / the test containers reference `:latest`, so there is no
tag to keep in sync and no consistency gate.

The deployed engine version is visible in Grafana (dashboard **Fuseki → Fuseki Triplestore**), read from
the OTLP `service.version` resource attribute → `target_info{service_name="DSP_db_db"}` `service_version`.

## Publishing

The Fuseki image is published together with the other images via `just docker-publish` — on every merge
to `main` (`docker-publish.yml`) and at release (`publish-release.yml`), tagged `:latest` + the
release/git version. There is no separate Fuseki workflow.

To build + load it locally: `just docker-build-fuseki-image` (loads `:latest`). To publish manually:
`just docker-publish-fuseki-image` (needs Docker Hub credentials).
