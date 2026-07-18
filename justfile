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

# CI threads the RBE flag string emitted by the bazel-rbe composite action into these recipes as a
# trailing `*FLAGS` argument (`just <recipe> "$FLAGS"`); each bazel command appends `{{FLAGS}}`.
# Locally FLAGS is empty (dev never contacts the remote backend) and the recipes run as before.

# Run unit tests for dsp-api (webapi only; use `test-unit` for the full pure-JVM suite)
test *FLAGS='':
    bazel test //modules/webapi:test {{FLAGS}}

# Run all pure-JVM unit tests (matches the CI `unit-tests` job; RBE-safe: runs remotely + result-caches)
test-unit *FLAGS='':
    bazel test //modules/webapi:test //modules/ingest:test //modules/bagit:test //modules/jwt:test //modules/shacl-validator:test {{FLAGS}}

# Load the :latest/pinned sipi, ingest and fuseki images into the local Docker daemon (needed by test-it/test-e2e/test-ingest-integration)
docker-load-test-images *FLAGS='':
    bazel run {{FLAGS}} //modules/sipi:load
    bazel run {{FLAGS}} //modules/ingest:load
    bazel run {{FLAGS}} //modules/fuseki:load

# Run integration tests for dsp-api
test-it *FLAGS='': (docker-load-test-images FLAGS)
    bazel test //modules/test-it:test //modules/test-it:test_gravsearch_span {{FLAGS}}

# Run End-2-End tests for dsp-api
test-e2e *FLAGS='': (docker-load-test-images FLAGS)
    bazel test //modules/test-e2e:test {{FLAGS}}

# Run unit tests for ingest
test-ingest *FLAGS='':
    bazel test //modules/ingest:test {{FLAGS}}

# Run integration tests for ingest
test-ingest-integration *FLAGS='': (docker-load-test-images FLAGS)
    bazel test //modules/test-ingest-integration:test {{FLAGS}}

# Run code formatting + lint check (sbt)
check:
    ./sbtx check

## Docker image build / publish (canonical home — the Makefile is deprecated)

# Print the docker image tag (git describe via workspace_status.sh; no sbt)
docker-image-tag:
    @tools/workspace_status.sh | awk '/^STABLE_GIT_VERSION /{print $2}'

# Assert workspace_status.sh's STABLE_GIT_VERSION byte-matches sbt's dockerImageTag (temporary gate)
check-docker-image-tag:
    #!/usr/bin/env bash
    set -euo pipefail
    ws=$(tools/workspace_status.sh | awk '/^STABLE_GIT_VERSION /{print $2}')
    sbt=$(./sbtx -Dsbt.log.noformat=true -Dsbt.supershell=false -Dsbt.ci=true -error "print dockerImageTag" | tr -d '[:space:]')
    if [ "$ws" != "$sbt" ]; then
      echo "DRIFT: workspace_status=$ws sbt=$sbt"
      exit 1
    fi
    echo "OK: $ws"

# Build the knora-api image with Bazel + load it into the local Docker daemon (:latest and :<version>)
docker-build-dsp-api-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/webapi:load
    docker tag daschswiss/knora-api:latest "daschswiss/knora-api:$TAG"
    echo "Loaded daschswiss/knora-api: latest + $TAG"

# Build the knora-sipi image with Bazel + load it into the local Docker daemon (:latest and :<version>)
docker-build-sipi-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/sipi:load
    docker tag daschswiss/knora-sipi:latest "daschswiss/knora-sipi:$TAG"
    echo "Loaded daschswiss/knora-sipi: latest + $TAG"

# Build the dsp-ingest image with Bazel + load it into the local Docker daemon (:latest and :<version>)
docker-build-ingest-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/ingest:load
    docker tag daschswiss/dsp-ingest:latest "daschswiss/dsp-ingest:$TAG"
    echo "Loaded daschswiss/dsp-ingest: latest + $TAG"

# Build dsp-api/sipi/ingest images locally (Fuseki excluded: use docker-build-fuseki-image)
docker-build *FLAGS='': (docker-build-dsp-api-image FLAGS) (docker-build-sipi-image FLAGS) (docker-build-ingest-image FLAGS)

# Build + publish the multi-arch knora-api image with Bazel (tags: latest + <version>)
docker-publish-dsp-api-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/webapi:push -- -t latest -t "$TAG"

# Build + publish the multi-arch knora-sipi image with Bazel (tags: latest + <version>)
docker-publish-sipi-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/sipi:push -- -t latest -t "$TAG"

# Build + publish the multi-arch dsp-ingest image with Bazel (tags: latest + <version>)
docker-publish-ingest-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    TAG=$(just docker-image-tag)
    bazel run {{FLAGS}} //modules/ingest:push -- -t latest -t "$TAG"

# Publish dsp-api/sipi/ingest images to Docker Hub (Fuseki excluded: published separately)
docker-publish *FLAGS='': (docker-publish-dsp-api-image FLAGS) (docker-publish-sipi-image FLAGS) (docker-publish-ingest-image FLAGS)

# Build the Fuseki image with Bazel + load it into the local Docker daemon (at its version tag)
docker-build-fuseki-image *FLAGS='':
    bazel run {{FLAGS}} //modules/fuseki:load

# Build + publish the multi-arch Fuseki image to Docker Hub with Bazel
docker-publish-fuseki-image *FLAGS='':
    #!/usr/bin/env bash
    set -euo pipefail
    VER=$(grep -oE 'apache-jena-fuseki:[^"]+' modules/fuseki/BUILD.bazel | head -1 | cut -d: -f2)
    bazel run {{FLAGS}} //modules/fuseki:push -- -t "$VER"

# Start stack
stack-start:
    @echo "Starting Stack"
    docker compose up -d
    ./modules/webapi/scripts/wait-for-db.sh
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

# Run API locally against the dev Fuseki (requires VPN)
run-with-dev-db:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ ! -f .env ]; then
        main=$(git worktree list --porcelain | awk 'NR==1{sub(/^worktree /, ""); print}')
        ln -s "$main/.env" .env
    fi
    source .env
    export KNORA_WEBAPI_TRIPLESTORE_HOST=db.dev.dasch.swiss
    export KNORA_WEBAPI_TRIPLESTORE_USE_HTTPS=true
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PORT=443
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_USERNAME=admin
    export KNORA_WEBAPI_TRIPLESTORE_FUSEKI_PASSWORD=$DEV_DB_PASSWORD
    export KNORA_WEBAPI_SIPI_INTERNAL_HOST=iiif.dev.dasch.swiss                                                             
    export KNORA_WEBAPI_SIPI_INTERNAL_PROTOCOL=https                                                                        
    export KNORA_WEBAPI_SIPI_INTERNAL_PORT=443
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
    (cd modules/ingest; mkdocs build --clean)

markdownlint:
    docker run \
    --rm \
    -v $PWD:/workdir \
    ghcr.io/igorshubovych/markdownlint-cli:latest \
    --config .markdownlint.yml \
    --disable MD013 MD040 -- \
    "docs/**/*.md"
