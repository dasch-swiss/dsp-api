# Configuration

Ingest runs inside DSP-API and is configured through `INGEST_*` environment variables of the API container, read from
its own `ingest.conf`. The listening port, logging and token validation are DSP-API's: ingest accepts the tokens
DSP-API issues, checked against DSP-API's secret, issuer and ingest audience (`KNORA_WEBAPI_DSP_INGEST_AUDIENCE`).
The full DSP-API configuration is described in the
[DSP-API documentation](https://docs.dasch.swiss/DSP-API/04-publishing-deployment/configuration/).

There are a couple of groups of variables:

- [Storage](#storage) - configuration of the storage, e.g. file paths to asset folder
- [Processing](#processing) - how ingest runs Sipi, `ffmpeg` and `ffprobe`, and how much of the machine it uses
- [Features](#features) - configuration options for enabling or disabling certain features

## Storage

| Variable                   | Default       | Description                                              |
|----------------------------|---------------|----------------------------------------------------------|
| `INGEST_STORAGE_ASSET_DIR` | `/opt/images` | Path to the folder where the assets will be stored       |
| `INGEST_STORAGE_TEMP_DIR`  | `/opt/temp`   | Path to the folder where temporary files will be created |

Both folders must be mounted read-write into the API container.
The layout in these folders is explained in detail in the chapter ["Filesystem Setup"](service-filesystem-setup.md).

## Processing

| Variable                      | Default                        | Description                                                                                                                                           |
|-------------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INGEST_SIPI_USE_LOCAL_DEV`   | `false`                        | `false` runs the Sipi CLI of the API image; `true` runs it through `docker run`, for local development                                                |
| `INGEST_SIPI_LOCAL_DEV_IMAGE` | `daschswiss/knora-sipi:latest` | The Sipi image `docker run` starts, read only when `INGEST_SIPI_USE_LOCAL_DEV` is `true`                                                              |
| `INGEST_BULK_MAX_PARALLEL`    | `8`                            | Number of files a _Bulk Ingest_ processes in parallel                                                                                                 |
| `INGEST_EXECUTOR_CPU_SHARE`   | `0.5`                          | Share of the available processors, in `(0, 1]`, that sizes the ingest thread pool and the number of concurrent Sipi, `ffmpeg` and `ffprobe` processes |

## Features

| Variable                      | Default | Description                                                                                                                                                    |
|-------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INGEST_ALLOW_ERASE_PROJECTS` | `false` | If set to `true`, the endpoint `DELETE /ingest/projects/:shortcode/erase` will be enabled. This endpoint can remove all data related to a project permanently. |
