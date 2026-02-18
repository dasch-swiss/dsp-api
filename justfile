openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

alias ssl := stack-start-latest
alias stop := stack-stop
alias ssd := stack-start-dev

# Format code
fmt:
    ./sbtx fmt

# Run unit tests for dsp-api
test:
    ./sbtx "webapi/test"

# Run integration tests for dsp-api
test-it:
    ./sbtx "test-it/test"

# Run End-2-End tests for dsp-api
test-e2e:
    ./sbtx "test-e2e/test"

# Run unit tests for ingest
test-ingest:
    ./sbtx ingest/test

# Run integration tests for ingest
test-ingest-integration:
    ./sbtx ingestIntegration/test

# Start stack
stack-start:
    @echo "Starting Stack"
    docker compose up -d
    ./webapi/scripts/wait-for-db.sh
    @echo "Stack started"

# Start Stack without API for development
stack-start-dev: stack-start
    @echo "Stopping API"
    docker compose down api
    @echo "Stack started without API"

# Start stack and pull latest images before starting
stack-start-latest: && stack-start
    @echo "Pulling latest"
    docker compose pull

# Stop stack
stack-stop:
    @echo "Stopping Stack"
    docker compose stop
    @echo "Stack stopped"

# Restart stack
stack-restart: stack-stop && stack-start

# Stops and removes all containers and stack related volumes and network
[confirm]
stack-destroy:
    @echo "Destroying Stack"
    @echo "\nStopping and removing containers"
    docker stop $(docker ps -aq)
    docker rm $(docker ps -aq)
    @echo "\nPruning Network"
    docker network rm knora-net
    @echo "\nPruning Volumes"
    docker volume rm dsp-api_db-home -f
    docker volume rm dsp-api_db-import -f
    @echo "Stack destroyed"

# Initialize the db with a set of test data
[confirm]
stack-init-test: && stack-start
    make init-db-test

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

## Documentation

docs-install-requirements:
    python -m pip install --upgrade pip
    pip3 install -r docs/requirements.txt

docs-clean:
    rm -rf site/

docs-build-dependent:
    make -C docs graphvizfigures

docs-serve: docs-build-dependent
    mkdocs serve

docs-build: docs-build-dependent docs-ingest-build
    mkdocs build --strict

docs-ingest-build:
    (cd ingest; mkdocs build --clean)

markdownlint:
    docker run \
    --rm \
    -v $PWD:/workdir \
    ghcr.io/igorshubovych/markdownlint-cli:latest \
    --config .markdownlint.yml \
    --disable MD013 MD040 -- \
    "docs/**/*.md"
