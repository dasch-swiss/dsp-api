---
title: "Docker Compose dev stack startup ordering and port conflicts"
date: 2026-03-08
category: test-setup
component: infrastructure
module: dsp-api/docker-compose and dsp-api/justfile
problem_type: setup_failure
severity: medium
symptoms: "Port already in use when starting local dev servers; API boots with empty triplestore cache; Docker services torn down unexpectedly during data initialization"
root_cause: "just/make recipes chain destructive operations (volume deletion, full stack restart) behind constructive-sounding names; dev stack recipes only stop the replaced service, not all conflicting services"
tags: ["docker-compose", "local-development", "startup-ordering", "port-conflicts", "just-recipes", "fuseki", "dsp-api", "dsp-das", "test-infrastructure"]
related:
  - configuration-errors/fuseki-remote-dev-db-config-recipe.md
  - build-errors/fork-long-lived-processes-from-build-tools.md
---

# Docker Compose Dev Stack Startup Ordering and Port Conflicts

## Problem

Setting up a local dsp-api development environment requires starting Docker services, initializing the triplestore, and running the API and frontend locally. The available `just`/`make` recipes have hidden behaviors that cause failures if used in the wrong order or without understanding their dependency chains.

Three things commonly go wrong:

1. **Port 4200 conflict**: `just stack-start-dev` starts ALL Docker services (including the `app` container on port 4200), then only stops the `api` container. The `app` container keeps running, blocking any local Angular dev server from binding to port 4200.

2. **Destructive data initialization**: `just stack-init-test` sounds like it loads test data, but it actually calls `make init-db-test` which first runs `stack-down-delete-volumes` (tears down everything and deletes volumes), then rebuilds Fuseki, loads data, and finally restarts the full Docker stack — including the API container on port 3333 — competing with any locally-run API.

3. **Stale API cache**: If the API is started before Fuseki has test data loaded, its in-memory caches are populated from an empty triplestore. Subsequent data loading into Fuseki does not invalidate the API's caches.

## Key Recipe Behaviors

Understanding what each recipe actually does is essential:

- **`just stack-start-dev`** = `docker compose up -d` (ALL services) → `docker compose down api`. Leaves `app` (port 4200), `db` (3030), `sipi` (1024), `ingest` (3340), and `alloy` (3001) running.

- **`just stack-init-test`** = `make init-db-test` → `just stack-start`. Where `init-db-test` = `stack-down-delete-volumes` → `stack-db-only` → run Fuseki init script. The full chain: destroy everything → start Fuseki → load data → start full stack (including API on 3333).

- **`make init-db-test`** = `stack-down-delete-volumes` → `stack-db-only` → load data. Destructive (deletes volumes), but does NOT restart the full stack afterwards.

## Correct Local Development Setup

```bash
# 1. Initialize Fuseki with test data
#    Use `make` directly — avoids the full stack teardown/rebuild of `just stack-init-test`
make init-db-test

# 2. Start supporting Docker services, stop API container
just stack-start-dev

# 3. Also stop the app container to free port 4200
docker compose down app

# 4. Start API locally (Fuseki already has test data → cache is correct)
./sbtx "webapi/run"

# 5. Start Angular frontend locally (port 4200 is now free)
cd ../dsp-das && npm run start-local
```

Key points:
- Data initialization happens FIRST, before the API starts, so caches are populated correctly
- Use `make init-db-test` instead of `just stack-init-test` to avoid the destructive teardown/rebuild cycle
- Explicitly stop the `app` container after `stack-start-dev` to free port 4200

## Gotchas

- **`just stack-init-test` is NOT safe to run mid-session.** It will tear down your entire running stack (including volumes) and restart everything from scratch. Only use it for a clean-slate setup when nothing else is running.
- **Adding services to docker-compose.yml silently affects `stack-start-dev`.** Since it starts ALL services then only stops `api`, any new service with a conflicting port will break local development.
- **`make init-db-test` is also destructive** — it deletes volumes. But unlike `just stack-init-test`, it does not restart the full stack afterwards, so it's safer for initializing data before starting local services.

## Prevention

- **Always trace recipe dependency chains** before using build tool commands in test setups. Recipe names can be misleading — `stack-init-test` sounds safe but triggers `stack-down-delete-volumes`.
- **Check docker-compose.yml for ALL port bindings** when writing local dev setup instructions. Any service binding a port that a local dev tool also needs must be explicitly stopped.
- **Prefer additive over subtractive stack setup**: Rather than "start everything, then stop what you don't need", define Compose profiles (`--profile infra-only`) that start only what's needed.

## References

- `dsp-api/justfile` — `stack-start-dev` and `stack-init-test` recipes
- `dsp-api/Makefile` — `init-db-test` target with `stack-down-delete-volumes` dependency
- `dsp-api/docker-compose.yml` — service definitions with port bindings
