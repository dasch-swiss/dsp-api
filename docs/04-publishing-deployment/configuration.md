<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

<!-- TODO: adjust/update -->

# Configuration

All configuration for Knora is done in `application.conf`. Besides the Knora application
specific configuration, there we can also find configuration for the underlying Akka library.

For optimal performance it is important to tune the configuration to the hardware used, mainly
to the number of CPUs and cores per CPU.

The relevant sections for tuning are:

 - `akka.actor.deployment`
 - `knora-actor-dispatcher`
 - `knora-blocking-dispatcher`

## System Environment Variables
 
A number of core settings is additionally configurable through system environment variables. These are:
 
| key in application.conf                | environment variable                            | default value         |
| -------------------------------------- | ----------------------------------------------- | --------------------- |
| akka.log-config-on-start               | KNORA_AKKA_LOG_CONFIG_ON_START                  | off                   |
| akka.loglevel                          | KNORA_AKKA_LOGLEVEL                             | INFO                  |
| akka.stdout-loglevel                   | KNORA_AKKA_STDOUT_LOGLEVEL                      | INFO                  |
| app.print-extended-config              | KNORA_WEBAPI_PRINT_EXTENDED_CONFIG              | false                 |
| app.bcrypt-password-strength           | KNORA_WEBAPI_BCRYPT_PASSWORD_STRENGTH           | 12                    |
| app.jwt-secret-key                     | KNORA_WEBAPI_JWT_SECRET_KEY                     | super-secret-key      |
| app.jwt-longevity                      | KNORA_WEBAPI_JWT_LONGEVITY                      | 30 days               |
| app.cookie-domain                      | KNORA_WEBAPI_COOKIE_DOMAIN                      | localhost             |
| app.allow-reload-over-http             | KNORA_WEBAPI_ALLOW_RELOAD_OVER_HTTP             | false                 |
| app.ark.resolver                       | KNORA_WEBAPI_ARK_RESOLVER_URL                   | http://0.0.0.0:3336   |
| app.ark.assigned-number                | KNORA_WEBAPI_ARK_NAAN                           | 72163                 |
| app.knora-api.internal-host            | KNORA_WEBAPI_KNORA_API_INTERNAL_HOST            | 0.0.0.0               |
| app.knora-api.internal-port            | KNORA_WEBAPI_KNORA_API_INTERNAL_PORT            | 3333                  |
| app.knora-api.external-protocol        | KNORA_WEBAPI_KNORA_API_EXTERNAL_PROTOCOL        | http                  |
| app.knora-api.external-host            | KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST            | 0.0.0.0               |
| app.knora-api.external-port            | KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT            | 3333                  |
| app.sipi.internal-protocol             | KNORA_WEBAPI_SIPI_INTERNAL_PROTOCOL             | http                  |
| app.sipi.internal-host                 | KNORA_WEBAPI_SIPI_INTERNAL_HOST                 | localhost             |
| app.sipi.internal-port                 | KNORA_WEBAPI_SIPI_INTERNAL_PORT                 | 1024                  |
| app.sipi.external-protocol             | KNORA_WEBAPI_SIPI_EXTERNAL_PROTOCOL             | http                  |
| app.sipi.external-host                 | KNORA_WEBAPI_SIPI_EXTERNAL_HOST                 | localhost             |
| app.sipi.external-port                 | KNORA_WEBAPI_SIPI_EXTERNAL_PORT                 | 443                   |
| app.ark.resolver                       | KNORA_WEBAPI_ARK_RESOLVER_URL                   | http://0.0.0.0:3336   |
| app.ark.assigned-number                | KNORA_WEBAPI_ARK_NAAN                           | 72163                 |
| app.salsah1.base-url                   | KNORA_WEBAPI_SALSAH1_BASE_URL                   | http://localhost:3335 |
| app.triplestore.dbtype                 | KNORA_WEBAPI_TRIPLESTORE_DBTYPE                 | fuseki                |
| app.triplestore.use-https              | KNORA_WEBAPI_TRIPLESTORE_USE_HTTPS              | false                 |
| app.triplestore.host                   | KNORA_WEBAPI_TRIPLESTORE_HOST                   | localhost             |
| app.triplestore.auto-init              | KNORA_WEBAPI_TRIPLESTORE_AUTOINIT               | false                 |
| app.triplestore.fuseki.port            | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PORT            | 3030                  |
| app.triplestore.fuseki.repository-name | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_REPOSITORY_NAME | knora-test            |
| app.triplestore.fuseki.username        | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_USERNAME        | admin                 |
| app.triplestore.fuseki.password        | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PASSWORD        | test                  |
| app.cache-service.enabled              | KNORA_WEBAPI_CACHE_SERVICE_ENABLED              | true                  |

## Selectively Disabling Routes

In `application.conf` the setting `app.routes-to-reject` contains a list
of strings, representing routes which should be rejected.

For Example, the string `"v1/users"` would lead to rejection of any
route which contains this string.

## Startup Flags

There is a number of flags that can be set on startup, they will
override any value set in the application configuration file:

  - `loadDemoData`, `--loadDemoData`, `-d`: Loads the demo data.
  - `allowReloadOverHTTP`, `--allow-reload-over-http`, `-r`: Allows
    reloading of data over HTTP.
  - `-c`: Print the configuration at startup.
  - `--help`: Shows the help message with all startup flags.
