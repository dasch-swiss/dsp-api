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

# Sync MODULE.bazel's maven.install versions to Dependencies.scala (sbt) + re-pin the lock file.
# scala-steward updates only the sbt side, so run this after a dependency update to clear the drift that
# //tools/deps:maven_versions_match_sbt reports.
sync-bazel-maven-versions:
    python3 tools/deps/check_maven_versions.py --fix MODULE.bazel project/Dependencies.scala
    bazel run @unpinned_maven//:pin

## Docker image build / publish

# Print the docker image tag (git describe via workspace_status.sh; no sbt)
docker-image-tag:
    @tools/workspace_status.sh | awk '/^STABLE_GIT_VERSION /{print $2}'

# Assert workspace_status.sh's STABLE_GIT_VERSION byte-matches sbt's dockerImageTag (temporary gate)
check-docker-image-tag:
    #!/usr/bin/env bash
    set -euo pipefail
    ws=$(tools/workspace_status.sh | awk '/^STABLE_GIT_VERSION /{print $2}')
    # `print dockerImageTag` emits the value on the last stdout line; take only that
    # line so a cold sbt-launcher bootstrap (download messages to stdout) can't pollute it.
    sbt=$(./sbtx -Dsbt.log.noformat=true -Dsbt.supershell=false -Dsbt.ci=true -error "print dockerImageTag" | awk 'NF{last=$0} END{print last}' | tr -d '[:space:]')
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
    just init-db-test

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

## Architecture docs

# starts the structurizer and serves c4 architecture docs
structurizer:
    docker pull structurizr/lite
    docker run -it --rm -p 8080:8080 -v {{justfile_directory()}}/docs/architecture:/usr/local/structurizr structurizr/lite

## DSP stack (local dev)

# starts the dsp-stack: fuseki, sipi, api and app
stack-up: docker-build
    docker compose -f docker-compose.yml up -d db
    ./modules/webapi/scripts/wait-for-db.sh
    docker compose -f docker-compose.yml up -d
    ./modules/webapi/scripts/wait-for-api.sh

# starts the dsp-stack, rebuilding only the api image
stack-up-fast: docker-build-dsp-api-image
    docker compose -f docker-compose.yml up -d

# starts the dsp-stack using the 'dsp-repo' repository: fuseki, sipi, api
stack-up-ci: docker-build
    docker compose -f docker-compose.yml up -d

# re-starts the api (usually after loading data into fuseki)
stack-restart-api:
    docker compose -f docker-compose.yml restart api
    ./modules/webapi/scripts/wait-for-api.sh

# prints out and follows the logs of the running dsp-stack
stack-logs:
    docker compose -f docker-compose.yml logs -f

# prints out and follows the logs of the 'db' container
stack-logs-db:
    docker compose -f docker-compose.yml logs -f db

# prints out the logs of the 'db' container (no follow)
stack-logs-db-no-follow:
    docker compose -f docker-compose.yml logs db

# prints out and follows the logs of the 'sipi' container
stack-logs-sipi:
    docker compose -f docker-compose.yml logs -f sipi

# prints out the logs of the 'sipi' container (no follow)
stack-logs-sipi-no-follow:
    docker compose -f docker-compose.yml logs sipi

# prints out and follows the logs of the 'api' container
stack-logs-api:
    docker compose -f docker-compose.yml logs -f api

# prints out the logs of the 'api' container (no follow)
stack-logs-api-no-follow:
    docker compose -f docker-compose.yml logs api

# checks the api health endpoint
stack-health:
    curl -f 0.0.0.0:3333/health

# shows the status of the stack containers
stack-status:
    docker compose -f docker-compose.yml ps

# stops the dsp-stack (removes containers)
stack-down:
    docker compose -f docker-compose.yml down

# stops the dsp-stack and deletes any created volumes (deletes the database!)
stack-down-delete-volumes: clean-local-tmp clean-sipi-tmp
    docker compose -f docker-compose.yml down --volumes

# prints the resolved docker-compose config
stack-config:
    docker compose -f docker-compose.yml config

# starts the dsp-stack without dsp-api: fuseki and sipi only
stack-without-api: stack-up
    docker compose -f docker-compose.yml stop api

# starts the dsp-stack without dsp-app
stack-without-app: stack-up
    docker compose -f docker-compose.yml stop app

# starts the dsp-stack without dsp-api and sipi: fuseki only
stack-without-api-and-sipi: stack-up
    docker compose -f docker-compose.yml stop api
    docker compose -f docker-compose.yml stop sipi

# starts only fuseki
stack-db-only:
    docker compose -f docker-compose.yml up -d db
    ./modules/webapi/scripts/wait-for-db.sh

## Database management

# initializes the dsp-repo repository
init-db-test: stack-down-delete-volumes stack-db-only
    cd modules/webapi/scripts && ./fuseki-init-knora-test.sh

# initializes the dsp-repo repository with minimal data
init-db-test-minimal: stack-down-delete-volumes stack-db-only
    cd modules/webapi/scripts && ./fuseki-init-knora-test-minimal.sh

# initializes an empty dsp-repo repository
init-db-test-empty: stack-down-delete-volumes stack-db-only
    @echo init-db-test-empty

# init local database with data from test server. Use as `just init-db-from-test <password>`
init-db-from-test PW: (init-db-from-env PW "db.test.dasch.swiss")

# init local database with data from a local dump file of test server
init-db-from-test-dump: (init-db-from-dump-file "db.test.dasch.swiss.trig")

# init local database with data from stage server. Use as `just init-db-from-stage <password>`
init-db-from-stage PW: (init-db-from-env PW "db.stage.dasch.swiss")

# init local database with data from a local dump file of stage server
init-db-from-stage-dump: (init-db-from-dump-file "db.stage.dasch.swiss.trig")

# init local database with data from prod server. Use as `just init-db-from-prod <password>`
init-db-from-prod PW: (init-db-from-env PW "db.dasch.swiss")

# init local database with data from a local dump file of prod server
init-db-from-prod-dump: (init-db-from-dump-file "db.dasch.swiss.trig")

# init local database with data from dev server. Use as `just init-db-from-dev <password>`
init-db-from-dev PW: (init-db-from-env PW "db.dev.dasch.swiss")

# init local database with data from a local dump file of dev server
init-db-from-dev-dump: (init-db-from-dump-file "db.dev.dasch.swiss.trig")

# init local database with data from ls-test-server. Use as `just init-db-from-ls-test-server <password>`
init-db-from-ls-test-server PW: (init-db-from-env PW "db.ls-test-server.dasch.swiss")

# init local database with data from a local dump file of ls-test-server
init-db-from-ls-test-server-dump: (init-db-from-dump-file "db.ls-test-server.dasch.swiss.trig")

# dump data from an env. Use as `just db-dump <password> <env>` e.g. db.0000-test-server.dasch.swiss
db-dump PW ENV:
    @echo "dumping environment {{ENV}}"
    curl -f -X GET -H "Accept: application/trig" -u "admin:{{PW}}" "https://{{ENV}}/dsp-repo" > "{{ENV}}.trig"

# init local database from a specified dump file. Use as `just init-db-from-dump-file <dump-file.trig>`
init-db-from-dump-file DUMP: init-db-test-empty
    @echo "dump file: {{DUMP}}"
    curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "admin:test" "http://localhost:3030/dsp-repo"
    curl -X POST -H "Content-Type: application/trig" -T "{{justfile_directory()}}/{{DUMP}}" -u "admin:test" "http://localhost:3030/dsp-repo"

# dump data from an env and upload it to the local DB. Use as `just init-db-from-env <password> <env>`
init-db-from-env PW ENV: (db-dump PW ENV) (init-db-from-dump-file (ENV + ".trig"))

## Clean

# cleans the docker installation
clean-docker:
    docker system prune -af
    docker volume prune -f

clean-local-tmp:
    rm -rf .tmp
    mkdir .tmp

# clean SBT and Metals related stuff
clean-metals:
    rm -rf .bloop
    rm -rf .bsp
    rm -rf .metals
    rm -rf target
    ./sbtx clean

# clean build artifacts
clean: docs-clean clean-local-tmp clean-docker clean-sipi-tmp
    rm -rf .env

# deletes all files in Sipi's tmp folder
clean-sipi-tmp:
    mkdir empty_folder_for_clean_sipi_tmp
    cp modules/sipi/images/tmp/.gitignore empty_folder_for_clean_sipi_tmp/.gitignore
    rsync -a --delete empty_folder_for_clean_sipi_tmp/ modules/sipi/images/tmp/
    rm -r empty_folder_for_clean_sipi_tmp

# deletes all files uploaded within a project
clean-sipi-projects:
    rm -rf modules/sipi/images/[0-9A-F][0-9A-F][0-9A-F][0-9A-F]
