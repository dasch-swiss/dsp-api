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

The `dsp-repo` dataset is defined by `dsp-repo.ttl`, which the entrypoint installs at
`/fuseki/configuration/dsp-repo.ttl` on every start. The data volume is persisted at `/fuseki`.
See [Configuration changes](#configuration-changes) for what happens when the file changes.

## Configuration

| Variable | Description | Default |
| --- | --- | --- |
| `ADMIN_PASSWORD` | Fuseki admin password | value from `shiro.ini` |
| `JVM_ARGS` | JVM heap and flags | `-Xmx4G` |
| `REBUILD_INDEX_OF_DATASET` | Dataset name to rebuild Lucene index for | unset |
| `REBUILD_INDEX_MARKER_FILE` | File whose presence (content: dataset name) triggers a one-off rebuild | unset |
| `SKIP_AUTO_INDEX_REBUILD` | `true`: install a changed `dsp-repo.ttl` without rebuilding the index | unset |

## Configuration changes

The image's `dsp-repo.ttl` is the source of truth: a configuration change ships with the image, with no manual
step on the hosts, and an edit made to `/fuseki/configuration/dsp-repo.ttl` on a host is overwritten.
On every start the entrypoint compares the file on the `/fuseki` volume with the image's, ignoring comments
and whitespace:

- **No file yet** (fresh volume): the image's file is copied, nothing else happens.
- **Unchanged**: nothing happens. A comment-only change is not installed, which is harmless.
- **Changed**: the image's file is staged in `/fuseki/configuration.next/`, the Lucene index is deleted and
  rebuilt from it (`jena.textindexer`), and only then is it moved over `/fuseki/configuration/dsp-repo.ttl`.
  Most of the file describes the index, and an index built with a different description is silently wrong
  (for example, an index without `text:graphField` returns nothing for `GRAPH <g> { ?s text:query ... }`).
  If the rebuild fails, the container exits and the host keeps its old file, so the next start retries.
- **Changed, while a rebuild of another dataset is requested** (`REBUILD_INDEX_OF_DATASET` or the marker file):
  a warning is logged and the file is installed on the next start.

So **a release that changes `dsp-repo.ttl` takes the database down for a full index rebuild** on deploy
(about 6.5 minutes on prod, plus container start). Plan the deploy accordingly; it is not a release-notes item.

Setting `SKIP_AUTO_INDEX_REBUILD=true` installs a changed file without the rebuild and logs a warning that the
index is stale. Rebuild it later by starting the container with `REBUILD_INDEX_OF_DATASET=dsp-repo`, or by
writing `dsp-repo` to the file `REBUILD_INDEX_MARKER_FILE` points to before a restart.

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
