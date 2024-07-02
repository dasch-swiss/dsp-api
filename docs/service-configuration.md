# Configuration

Configuring the service is done through environment variables.

There are a couple of groups of variables:

- [Service](#service) - configuration of the service itself, e.g. ports and logging
- [Storage](#storage) - configuration of the storage, e.g. file paths to asset folder
- [Jwt](#jwt) - configuration of the JWT authentication, e.g. secret key
- [Features](#features) - configuration options for enabling or disabling certain features

## Service

| Variable            | Default   | Description                                                                   |
|---------------------|-----------|-------------------------------------------------------------------------------|
| `SERVICE_PORT`      | `3340`    | Port on which the service will listen                                         |
| `SERVICE_HOST`      | `0.0.0.0` | Host on which the service will listen                                         |
| `SERVICE_LOG_LEVEL` | `test`    | Log output format: `json` for json logging, `text` for human readable logging |

## Storage

| Variable            | Default                   | Description                                              |
|---------------------|---------------------------|----------------------------------------------------------|
| `STORAGE_ASSET_DIR` | `localdev/storage/images` | Path to the folder where the assets will be stored       |
| `STORAGE_TEMP_DIR`  | `localdev/storage/temp`   | Path to the folder where temporary files will be created |

The layout in these folders is explained in detail in the chapter ["Filesystem Setup"](service-filesystem-setup.md).

## Jwt

| Variable           | Default                            | Description                                                                                                                                               |
|--------------------|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JWT_SECRET`       | `UP 4888, nice 4-8-4 steam engine` | Secret key used to sign the JWT tokens, must be a valid H256 secret                                                                                       |
| `JWT_ISSUER`       | `http://0.0.0.0:3333`              | Expected issuer claim in the JWT                                                                                                                          |
| `JWT_AUDIENCE`     | `http://localhost:3340`            | Expected audience claim in the JWT                                                                                                                        |
| `JWT_DISABLE_AUTH` | `false`                            | Disable JWT authentication, useful for local development. <br/>When set to `true` protected routes still expect a `Bearer` token but it can be any value. |

## Database

| Variable      | Default                                         | Description                                                                                                         |
|---------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `DB_JDBC_URL` | `jdbc:sqlite:localdev/storage/db/ingest.sqlite` | Jdbc Url for the sqlite database. Everything after `jdbc:sqlite:` is the path to the file where the data is stored. |

## Features

| Variable               | Default | Description                                                                                                                                             |
|------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ALLOW_ERASE_PROJECTS` | `false` | If set to `true`, the endpoint `DELETE /projects/:shortcode/erase` will be enabled. This endpoint can remove all data related to a project permanently. |
