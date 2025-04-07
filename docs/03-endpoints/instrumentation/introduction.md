# Instrumentation

The instrumentation endpoints are running on a separate port (default `3339`)
defined in `application.conf` under the key: `app.instrumentaion-server-config.port`
and can also be set through the environment variable: `KNORA_INSTRUMENTATION_SERVER_PORT`.

The exposed endpoints are:

- `/metrics` - a metrics endpoint, backed by the ZIO metrics backend exposing metrics in the prometheus format
- `/health` - provides information about the health state, see [Health Endpoint](./health.md)
