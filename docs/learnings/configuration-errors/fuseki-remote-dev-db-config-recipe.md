---
title: "Testing against the dev database was not documented or automated"
date: 2026-02-17
category: configuration-errors
component: infrastructure
module: build-system/justfile
problem_type: dev-workflow
severity: medium
symptoms: |
  Testing certain changes locally was unnecessarily difficult because the fully local
  setup requires realistic test data. In some cases testing against the dev database
  with real data is much easier, but no documented or automated way to do this existed.
root_cause: |
  The local development setup assumed developers always use a local Fuseki with test data.
  No workflow was provided for the common case of needing to test against the dev database.
tags:
  - fuseki
  - testing-workflow
  - local-development
  - justfile
  - developer-experience
---

## Problem

The local development setup assumes a fully local Fuseki triplestore seeded with test data. For many changes this works fine, but some changes are hard to test meaningfully without realistic data. In those cases, running the API against the dev database (db.dev.dasch.swiss) is the practical option — but how to do this was neither documented nor automated. Developers had to figure out the correct environment variables, HTTPS settings, and credential management on their own.

## Root Cause

The development tooling only covered the local Docker setup (`just stack-start-dev`). The alternative workflow of connecting to the remote dev Fuseki was undocumented tribal knowledge, requiring five environment variable overrides with non-obvious values (HTTPS on port 443 instead of HTTP on 3030).

## Solution

Added a `just run-with-dev-db` recipe to the justfile that encapsulates all the configuration:

```just
# Run API locally against the dev Fuseki
run-with-dev-db:
    #!/usr/bin/env bash
    set -euo pipefail
    source .env
    export KNORA_WEBAPI_TRIPLESTORE_HOST=db.dev.dasch.swiss
    export KNORA_WEBAPI_TRIPLESTORE_USE_HTTPS=true
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PORT=443
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_USERNAME=admin
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PASSWORD=$DEV_DB_PASSWORD
    ./sbtx "webapi/run"
```

Prerequisites: A `.env` file in the repo root (git-ignored) containing `DEV_DB_PASSWORD=<password>`. Passwords can be found in [ops-deploy/host_vars](https://github.com/dasch-swiss/ops-deploy/tree/main/host_vars).

## Prevention

- **Automate alternative workflows too**: When the primary dev setup doesn't cover all testing scenarios, provide recipes for the alternatives.
- **Avoid tribal knowledge**: If a workflow needs explaining, it should be a `just` recipe instead.

## References

- `webapi/src/main/resources/application.conf:133-165` — triplestore config block with env var overrides
- `docs/04-publishing-deployment/configuration.md:41-47` — environment variable documentation
