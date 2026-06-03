# Configuration

All configuration for Knora is done in `application.conf`. 

For optimal performance it is important to tune the configuration to the hardware used, mainly
to the number of CPUs and cores per CPU.

The relevant sections for tuning are:

- `knora-actor-dispatcher`
- `knora-blocking-dispatcher`

## System Environment Variables

A number of core settings is additionally configurable through system environment variables. These are:

| key in application.conf                | environment variable                            | default value           |
|----------------------------------------|-------------------------------------------------|-------------------------|
| app.bcrypt-password-strength           | KNORA_WEBAPI_BCRYPT_PASSWORD_STRENGTH           | 12                      |
| app.jwt.secret                         | KNORA_WEBAPI_JWT_SECRET_KEY                     | super-secret-key        |
| app.jwt.expiration                     | KNORA_WEBAPI_JWT_LONGEVITY                      | 30 days                 |
| app.jwt.issuer                         | KNORA_WEBAPI_JWT_ISSUER                         | 0.0.0.0:3333            |
| app.dsp-ingest.audience                | KNORA_WEBAPI_DSP_INGEST_AUDIENCE                | <http://localhost:3340> |
| app.dsp-ingest.base-url                | KNORA_WEBAPI_DSP_INGEST_BASE_URL                | <http://localhost:3340> |
| app.allow-reload-over-http             | KNORA_WEBAPI_ALLOW_RELOAD_OVER_HTTP             | false                   |
| app.ark.resolver                       | KNORA_WEBAPI_ARK_RESOLVER_URL                   | <http://0.0.0.0:3336>   |
| app.ark.assigned-number                | KNORA_WEBAPI_ARK_NAAN                           | 72163                   |
| app.knora-api.internal-host            | KNORA_WEBAPI_KNORA_API_INTERNAL_HOST            | 0.0.0.0                 |
| app.knora-api.internal-port            | KNORA_WEBAPI_KNORA_API_INTERNAL_PORT            | 3333                    |
| app.knora-api.external-protocol        | KNORA_WEBAPI_KNORA_API_EXTERNAL_PROTOCOL        | http                    |
| app.knora-api.external-host            | KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST            | 0.0.0.0                 |
| app.knora-api.external-port            | KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT            | 3333                    |
| app.sipi.internal-protocol             | KNORA_WEBAPI_SIPI_INTERNAL_PROTOCOL             | http                    |
| app.sipi.internal-host                 | KNORA_WEBAPI_SIPI_INTERNAL_HOST                 | localhost               |
| app.sipi.internal-port                 | KNORA_WEBAPI_SIPI_INTERNAL_PORT                 | 1024                    |
| app.sipi.external-protocol             | KNORA_WEBAPI_SIPI_EXTERNAL_PROTOCOL             | http                    |
| app.sipi.external-host                 | KNORA_WEBAPI_SIPI_EXTERNAL_HOST                 | localhost               |
| app.sipi.external-port                 | KNORA_WEBAPI_SIPI_EXTERNAL_PORT                 | 443                     |
| app.ark.resolver                       | KNORA_WEBAPI_ARK_RESOLVER_URL                   | <http://0.0.0.0:3336>   |
| app.ark.assigned-number                | KNORA_WEBAPI_ARK_NAAN                           | 72163                   |
| app.triplestore.dbtype                 | KNORA_WEBAPI_TRIPLESTORE_DBTYPE                 | fuseki                  |
| app.triplestore.use-https              | KNORA_WEBAPI_TRIPLESTORE_USE_HTTPS              | false                   |
| app.triplestore.host                   | KNORA_WEBAPI_TRIPLESTORE_HOST                   | localhost               |
| app.triplestore.fuseki.port            | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PORT            | 3030                    |
| app.triplestore.fuseki.repository-name | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_REPOSITORY_NAME | dsp-repo                |
| app.triplestore.fuseki.username        | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_USERNAME        | admin                   |
| app.triplestore.fuseki.password        | KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PASSWORD        | test                    |

## Startup Flags

There is a number of flags that can be set on startup, they will
override any value set in the application configuration file:

- `loadDemoData`, `--loadDemoData`, `-d`: Loads the demo data.
- `allowReloadOverHTTP`, `--allow-reload-over-http`, `-r`: Allows
  reloading of data over HTTP.
- `-c`: Print the configuration at startup.
- `--help`: Shows the help message with all startup flags.

## Feature Flags

Feature flags gate functionality that is not yet stable, is being rolled out,
or is intentionally restricted on certain deployments. Each flag can be set
via its environment variable or in `application.conf` under
`app.features.<flag-name>`.

| key in application.conf                               | environment variable                     | default value |
|-------------------------------------------------------|------------------------------------------|---------------|
| app.features.allow-erase-projects                     | ALLOW_ERASE_PROJECTS                     | false         |
| app.features.trigger-compaction-after-project-erasure | TRIGGER_COMPACTION_AFTER_PROJECT_ERASURE | false         |
| app.features.disable-last-modification-date-check     | DISABLE_LAST_MODIFICATION_DATE_CHECK     | false         |
| app.features.allow-import-migration-bagit             | ALLOW_IMPORT_MIGRATION_BAGIT             | true          |
| app.features.allow-placeholder                        | ALLOW_PLACEHOLDER                        | true          |
| app.features.allow-project-data-import                | ALLOW_PROJECT_DATA_IMPORT                | false         |

### `allow-erase-projects`

Controls whether the project erase endpoint is enabled.
Erasing a project permanently removes all of its data from the triplestore
and is irreversible; it requires SystemAdmin permission.

When the flag is disabled, calls to the erase endpoint are rejected with
`403 Forbidden` and the message
`The feature to erase projects is not enabled.`

### `trigger-compaction-after-project-erasure`

Controls whether the triplestore is compacted after a project is erased.
When enabled, a Fuseki compaction is triggered as the final step of the
erase operation to reclaim disk space.

Has no effect unless [`allow-erase-projects`](#allow-erase-projects) is
also enabled.

### `disable-last-modification-date-check`

Disables the optimistic-concurrency check that compares the client-supplied
`lastModificationDate` against the value stored in the triplestore on
resource and ontology updates.

When the flag is enabled, mismatched modification dates no longer raise an
`EditConflictException`, so concurrent edits can overwrite each other
silently. Intended for data migration or recovery scenarios only; leave it
off for normal operation.

### `allow-import-migration-bagit`

Controls whether the project migration import endpoints are available.
See [Project Migration Export/Import](../03-endpoints/api-v3/project-migration.md)
for the endpoint reference.

When the flag is disabled, import endpoints return `404 Not Found`.
Export endpoints are always available and are not gated by this flag.

### `allow-placeholder`

Controls whether the Placeholder License
(`urn:dasch:placeholder`) may be used on a `FileValue`.
The Placeholder License is intended as a temporary placeholder while the
actual license for a file is still being determined. See
[License](../01-introduction/legal-info.md#license) for the broader context.

When the flag is disabled, any FileValue with
`licenseIri = urn:dasch:placeholder` is rejected by the
legal-info validation with the error:

```
License urn:dasch:placeholder is the placeholder license and is not allowed on this server
```

This is checked at the server level — the placeholder license is rejected
even if a project has explicitly enabled it.

### `allow-project-data-import`

Controls whether the project data-graph import endpoints are available.
See [Project Data Import](../03-endpoints/api-v3/project-data-import.md)
for the endpoint reference.

When the flag is disabled, the data-graph import endpoints return `404 Not Found`.
