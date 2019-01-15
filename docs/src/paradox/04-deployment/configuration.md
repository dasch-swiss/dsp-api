<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Configuration

All configuration for Knora is done in `application.conf`. Besides the Knora application
specific configuration, there we can also find configuration for the underlying Akka library.

For optimal performance it is important to tune the configuration to the hardware used, mainly
to the number of CPUs and cores per CPU.

The relevant sections for tuning are:

 - `akka.actor.deployment`
 - `knora-ask-dispatcher`
 - `knora-store-dispatcher`
 - `knora-sipi-dispatcher`
 - `knora-v1-dispatcher`
 - `knora-v2-dispatcher`
 - `knora-admin-dispatcher`
 
 ## System Environment Variables
 
 A number of core settings is additionally configurable through system environment variables. These are:
 
| key in application.conf                  | environment variable                              | default value        |
|------------------------------------------|---------------------------------------------------|----------------------|
| akka.log-config-on-start                 | KNORA_AKKA_LOG_CONFIG_ON_START                    | off                  |
| akka.loglevel                            | KNORA_AKKA_LOGLEVEL                               | INFO                 |
| akka.stdout-loglevel                     | KNORA_AKKA_STDOUT_LOGLEVEL                        | INFO                 |
| app.print-short-config                   | KNORA_WEBAPI_PRINT_SHORT_CONFIG                   | true                 |
| app.print-extended-config                | KNORA_WEBAPI_PRINT_EXTENDED_CONFIG                | false                |
| app.jwt-secret-key                       | KNORA_WEBAPI_JWT_SECRET_KEY                       | super-secret-key     |
| app.jwt-longevity                        | KNORA_WEBAPI_JWT_LONGEVITY                        | 30 days              |
| app.knora-api.internal-host              | KNORA_WEBAPI_KNORA_API_INTERNAL_HOST              | 0.0.0.0              |
| app.knora-api.internal-port              | KNORA_WEBAPI_KNORA_API_INTERNAL_PORT              | 3333                 |
| app.knora-api.external-protocol          | KNORA_WEBAPI_KNORA_API_EXTERNAL_PROTOCOL          | http                 |
| app.knora-api.external-host              | KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST              | 0.0.0.0              |
| app.knora-api.external-port              | KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT              | 3333                 |
| app.sipi.internal-protocol               | KNORA_WEBAPI_SIPI_INTERNAL_PROTOCOL               | http                 |
| app.sipi.internal-host                   | KNORA_WEBAPI_SIPI_INTERNAL_HOST                   | localhost            |
| app.sipi.internal-port                   | KNORA_WEBAPI_SIPI_INTERNAL_PORT                   | 1024                 |
| app.sipi.external-protocol               | KNORA_WEBAPI_SIPI_EXTERNAL_PROTOCOL               | http                 |
| app.sipi.external-host                   | KNORA_WEBAPI_SIPI_EXTERNAL_HOST                   | localhost            |
| app.sipi.external-port                   | KNORA_WEBAPI_SIPI_EXTERNAL_PORT                   | 443                  |
| app.salsah1.base-url                     | KNORA_WEBAPI_SALSAH1_BASE_URL                     | http://localhost:3335|
| app.triplestore.dbtype                   | KNORA_WEBAPI_TRIPLESTORE_DBTYPE                   | graphdb-se           |
| app.triplestore.use-https                | KNORA_WEBAPI_TRIPLESTORE_USE_HTTPS                | false                |
| app.triplestore.host                     | KNORA_WEBAPI_TRIPLESTORE_HOST                     | localhost            |
| app.triplestore.graphdb.port             | KNORA_WEBAPI_TRIPLESTORE_GRAPHDB_PORT             | 7200                 |
| app.triplestore.graphdb.repository-name  | KNORA_WEBAPI_TRIPLESTORE_GRAPHDB_REPOSITORY_NAME  | knora-test           |
| app.triplestore.graphdb.username         | KNORA_WEBAPI_TRIPLESTORE_GRAPHDB_USERNAME         | admin                |
| app.triplestore.graphdb.password         | KNORA_WEBAPI_TRIPLESTORE_GRAPHDB_PASSWORD         | root                 |
| app.triplestore.fuseki.port              | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PORT              | 3030                 |
| app.triplestore.fuseki.repository-name   | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_REPOSITORY_NAME   | knora-test           |