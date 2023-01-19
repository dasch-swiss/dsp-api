<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Metrics Endpoint

The metrics endpoint exposes metrics gathered through the ZIO metrics frontend in the Prometheus
format. Additionally, ZIO runtime and JVM metrics are also exposed.

## Configuration

The refresh interval is configured in `application.conf` under the key: `app.instrumentaion-server-config.interval`
which es per default set to `5 seconds`.


## Example request

`GET /metrics`


## Example response

```text
# TYPE jvm_memory_pool_allocated_bytes_total counter
# HELP jvm_memory_pool_allocated_bytes_total Some help
jvm_memory_pool_allocated_bytes_total{pool="G1 Survivor Space"}  4828024.0 1671021037947
# TYPE jvm_memory_pool_allocated_bytes_total counter
# HELP jvm_memory_pool_allocated_bytes_total Some help
jvm_memory_pool_allocated_bytes_total{pool="G1 Eden Space"}  3.3554432E7 1671021037947
# TYPE zio_fiber_successes counter
# HELP zio_fiber_successes Some help
zio_fiber_successes 17.0 1671021037947
# TYPE zio_fiber_lifetimes histogram
# HELP zio_fiber_lifetimes Some help
zio_fiber_lifetimes_bucket{le="1.0"}  17.0 1671021037947
zio_fiber_lifetimes_bucket{le="2.0"}  17.0 1671021037947
...
```
