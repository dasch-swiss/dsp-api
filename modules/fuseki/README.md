# Apache Jena Fuseki — DaSCH image

Custom container image for [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/) used as the triplestore for dsp-api. Built with Bazel (`//modules/fuseki`, rules_oci).

Published to Docker Hub as `daschswiss/apache-jena-fuseki:<IMAGE_VERSION>`.

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

1. Find the new version on [jena.apache.org/download](https://jena.apache.org/download/)
2. Update `MODULE.bazel`'s `@fuseki_dist` `http_archive`:
   - the tarball `urls` — new `apache-jena-fuseki-<version>.tar.gz`
   - `sha256` — the checksum of that tarball
3. Update `modules/fuseki/BUILD.bazel`:
   - `FUSEKI_VERSION` (in the image `env`) — new Apache Jena Fuseki version (e.g. `5.6.0`)
   - `IMAGE_VERSION` (image `env` + `.version` label) and the `:load` `repo_tags` — new image tag (e.g. `5.6.0-1`, increment the `-N` suffix for DaSCH revisions)
4. Update `docker-compose.yml`: bump the `db` service image tag to match `IMAGE_VERSION`
5. Update `project/Dependencies.scala`: bump `fusekiImage` to match `IMAGE_VERSION`

CI guards this: `//tools/oci:image_versions_match_sbt` fails if `FUSEKI_VERSION` doesn't match the `@fuseki_dist` tarball URL, and `check-fuseki-version-consistency` fails if the image tag is out of sync across `BUILD.bazel`, `docker-compose.yml`, and `Dependencies.scala`.

## Publishing

The image is published automatically by `docker-publish-fuseki.yml` on every merge to `main` that touches `modules/fuseki/**` (via `bazel run //modules/fuseki:push`). No manual action is needed — open a PR with the version bump and merge it.

To build + load it locally: `just docker-build-fuseki-image`. To publish manually: `just docker-publish-fuseki-image` (needs Docker Hub credentials).
