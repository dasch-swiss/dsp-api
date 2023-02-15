<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Metrics Endpoint

The metrics endpoint exposes metrics gathered through the ZIO metrics frontend in the Prometheus
format. Additionally, ZIO runtime, JVM and ZIO-HTTP metrics are also exposed.

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

## ZIO-HTTP metrics

Metrics of all routes served by ZIO-HTTP (default: port `5555`) are exposed through a default metrics middleware. 
However, instead of `http_concurrent_requests_total` etc. they are labeled `zio_http_concurrent_requests_total` etc.
with `zio` prepended, so that they are clearly distinguishable while we still run ZIO-HTTP and Akka-HTTP in parallel. 

To prevent excessive amounts of labels, it is considered good practice, 
to replace dynamic path segments with slugs (e.g. `/projects/shortcode/0000` with `/projects/shortcode/:shortcode`). 
Like this, requesting different projects by identifier will add multiple values to the histogram of a single route,
instead of creating a histogram for each project:

```
zio_http_request_duration_seconds_bucket{method="GET",path="/admin/projects/shortcode/:shortcode",status="200",le="0.005"} 0.0 1676481606015
...
```

Instead of:

```
zio_http_request_duration_seconds_bucket{method="GET",path="/admin/projects/shortcode/0000",status="200",le="0.005"} 0.0 1676481606015
zio_http_request_duration_seconds_bucket{method="GET",path="/admin/projects/shortcode/0001",status="200",le="0.005"} 0.0 1676481606015
...
```

This is achieved by providing the middleware a `pathLabelMapper`;
when adding new routes, it is advisable to assert that this replacement works correctly for the newly added route.
