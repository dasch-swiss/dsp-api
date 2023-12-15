# List all recipies
default:
    just --list --unsorted

# Build a docker image locally and run it with docker-compose up
build-and-run-docker:
    export DOCKER_BUILDKIT=1; sbt Docker/publishLocal
    docker-compose up

# Updates the OpenApi yml files by generating these from the tAPIr specs
openapi-update:
    rm ./docs/openapi/openapi-*.yml
    sbt "runMain swiss.dasch.DocsGenerator ./docs/openapi"

# Installs the necessary Python dependencies for building the documentation
docs-install:
    pip install -r docs/requirements.txt

# Build the documentation clean
docs-build: docs-install
    mkdocs build --clean

# Serve the documentation
docs-serve: docs-install
    mkdocs serve

# Clean build, regenerate OpenApi, and serve the documentation
docs-serve-latest: openapi-update docs-build docs-serve
