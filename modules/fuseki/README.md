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

## Lucene index configuration changes

The `dsp-repo.ttl` copy on the `/fuseki` volume is authoritative once it exists: the entrypoint copies the bundled file
only when the target is missing, so a new image does **not** change the configuration of an existing host. A change
to the text index description (`:indexLucene` / `:entMap`) must be applied by editing
`/fuseki/configuration/dsp-repo.ttl` on each host by hand.

Changes that alter what is stored per Lucene document — such as `text:graphField "graph"`, which lets
`GRAPH <g> { ?s text:query ... }` filter inside Lucene — also need a full index rebuild, because existing
documents lack the new field. Trigger it with `make db-rebuild-index` on the host (sets the marker file and
restarts the DB) or with `REBUILD_INDEX_OF_DATASET=dsp-repo`; the entrypoint deletes the old index directory and
runs `jena.textindexer`. The database is down for the duration of the rebuild.

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
