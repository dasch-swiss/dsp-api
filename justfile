openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

# Update the OpenApi yml files by generating these from the tAPIr specs
docs-openapi-generate:
    mkdir -p {{ openapiDir }}
    rm {{ openapiDir }}/*.yml >> /dev/null 2>&1 || true
    sbt "webapi/runMain org.knora.webapi.slice.common.api.DocsGenerator {{ openapiDir }}"

alias dog := docs-openapi-generate

# Start stack from latest images on dockerhub (no build required)
start-stack-latest:
    docker compose down
    docker compose pull
    docker compose up -d

alias ssl := start-stack-latest

# Stop stack
stop-stack:
    docker compose down

alias stop := stop-stack
