openapiDir := "./docs/03-endpoints/generated-openapi"

# List all recipies
default:
    @just --list

# Update the OpenApi yml files by generating these from the tAPIr specs
alias dog := docs-openapi-generate
docs-openapi-generate:
    mkdir -p {{openapiDir}}
    rm {{openapiDir}}/*.yml >> /dev/null 2>&1 || true
    sbt "webapi/runMain org.knora.webapi.slice.common.api.DocsGenerator {{openapiDir}}"
