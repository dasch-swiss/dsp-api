<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Instrumentation

The instrumentation endpoints are running on a separate port (default `3339`)
defined in `application.conf` under the key: `app.instrumentaion-server-config.port`
and can also be set through the environment variable: `KNORA_INSTRUMENTATION_SERVER_PORT`.

The exposed endpoints are:
 - `/metrics` - a metrics endpoint, backed by the ZIO metrics backend exposing metrics in the prometheus format
 - `/health` - provides information about the health state, see [Health Endpoint](./health.md)
