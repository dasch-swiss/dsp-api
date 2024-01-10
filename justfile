openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

alias dog := docs-openapi-generate
alias ssl := stack-start-latest
alias stop := stack-stop

# Update the OpenApi yml files by generating these from the tAPIr specs
docs-openapi-generate:
    mkdir -p {{ openapiDir }}
    rm {{ openapiDir }}/*.yml >> /dev/null 2>&1 || true
    sbt "webapi/runMain org.knora.webapi.slice.common.api.DocsGenerator {{ openapiDir }}"

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

# Stops and removes all stack related container volumes and network
[confirm]
stack-destroy:
    @echo "Destroying Stack"
    @echo "\nStopping and removing containers"
    docker stop $(docker ps -aq)
    docker rm $(docker ps -aq)
    @echo "\nPruning Network"
    docker network rm knora-net
    @echo "\nPruning Volume"
    docker volume rm dsp-api_db-home -f
    docker volume rm dsp-api_db-import -f
    @echo "Stack destroyed"

# Initialize the db with a set of test data
[confirm]
stack-init-test: && stack-start
    make init-db-test
