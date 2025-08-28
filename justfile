openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

alias dog := docs-openapi-generate
alias ssl := stack-start-latest
alias stop := stack-stop
alias ssd := stack-start-dev

# Format code
fmt:
    ./sbtx fmt

# Run unit tests
test:
    ./sbtx "webapi/test"

# Run integration tests
test-it:
    ./sbtx "it/test"

# Run End-2-End tests
test-e2e:
    ./sbtx "e2e/test"

test-ingest-integration:
    export DOCKER_BUILDKIT=1
    ./sbtx Docker/publishLocal
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

## Documentation

# Generate the OpenApi in {{openapiDir}} yml from the tapir endpoints
docs-openapi-generate:
    # The generated files are stored in the docs/03-endpoints/generated-openapi directory
    # You can specify the directory where the files are stored by setting the openapiDir variable
    # e.g. `just openapiDir=/tmp/openapi docs-openapi-generate`
    mkdir -p {{ openapiDir }}
    rm {{ openapiDir }}/*.yml >> /dev/null 2>&1 || true
    ./sbtx "webapi/runMain org.knora.webapi.slice.common.api.DocsGenerator {{ openapiDir }}"

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

# Updates the OpenApi yml files by generating these from the tAPIr specs
docs-ingest-openapi-generate:
    rm -f ./ingest/docs/openapi/openapi-*.yml
    ./sbtx "project ingest" "runMain swiss.dasch.DocsGenerator ./ingest/docs/openapi"

docs-ingest-build: docs-ingest-openapi-generate
    (cd ingest; mkdocs build --clean)

markdownlint:
    docker run \
    --rm \
    -v $PWD:/workdir \
    ghcr.io/igorshubovych/markdownlint-cli:latest \
    --config .markdownlint.yml \
    --disable MD013 MD040 -- \
    "docs/**/*.md"
