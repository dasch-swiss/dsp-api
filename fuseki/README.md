# Apache Jena Fuseki — DaSCH image

Custom Docker image for [Apache Jena Fuseki](https://jena.apache.org/documentation/fuseki2/) used as the triplestore for dsp-api.

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
|---|---|---|
| `ADMIN_PASSWORD` | Fuseki admin password | value from `shiro.ini` |
| `JVM_ARGS` | JVM heap and flags | `-Xmx4G` |
| `REBUILD_INDEX_OF_DATASET` | Dataset name to rebuild Lucene index for | unset |

## Updating Jena/Fuseki

1. Find the new version on [jena.apache.org/download](https://jena.apache.org/download/)
2. Download the SHA512 checksum for `apache-jena-fuseki-<version>.tar.gz.sha512`
3. Update `fuseki/Dockerfile`:
   - `ARG IMAGE_VERSION` — new Docker image tag (e.g. `5.6.0-1`, increment the `-N` suffix for DaSCH revisions)
   - `ARG FUSEKI_VERSION` — new Apache Jena Fuseki version (e.g. `5.6.0`)
   - `ARG FUSEKI_SHA512` — new SHA512 checksum
4. Update `docker-compose.yml`: bump the `db` service image tag to match `IMAGE_VERSION`
5. Update `project/Dependencies.scala`: bump `fusekiImage` to match `IMAGE_VERSION`

The CI `check-fuseki-version-consistency` job will fail if these three are out of sync.

## Publishing

The image is published automatically by `docker-publish-fuseki.yml` on every merge to `main` that touches `fuseki/**`. No manual action is needed — open a PR with the version bump and merge it.

To publish manually from a branch (e.g. for testing): trigger `Docker Publish from branch` via GitHub Actions `workflow_dispatch`.
