openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

alias dog := docs-openapi-generate
alias ssl := stack-start-latest
alias stop := stack-stop

# Generate the OpenApi in {{openapiDir}} yml from the tapir endpoints
docs-openapi-generate:
    # The generated files are stored in the docs/03-endpoints/generated-openapi directory
    # You can specify the directory where the files are stored by setting the openapiDir variable
    # e.g. `just openapiDir=/tmp/openapi docs-openapi-generate`
    mkdir -p {{ openapiDir }}
    rm {{ openapiDir }}/*.yml >> /dev/null 2>&1 || true
    ./sbtx "webapi/runMain org.knora.webapi.slice.common.api.DocsGenerator {{ openapiDir }}"

# Start stack
stack-start:
    @echo "Starting Stack"
    docker compose up -d
    ./webapi/scripts/wait-for-db.sh
    @echo "Stack started"

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
