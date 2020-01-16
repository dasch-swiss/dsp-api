# Determine this makefile's path.
# Be sure to place this BEFORE `include` directives, if any.
THIS_FILE := $(lastword $(MAKEFILE_LIST))

include vars.mk

#################################
# Documentation targets
#################################

.PHONY: docs-publish
docs-publish: ## build and publish docs
	@docker run --rm -it -v $(PWD):/knora -v $(HOME)/.ivy2:/root/.ivy2 -v $(HOME)/.ssh:/root/.ssh daschswiss/sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

.PHONY: docs-build
docs-build: # build the docs
	@docker run --rm -v $(PWD):/knora -v $(HOME)/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

#################################
# Bazel targets
#################################

.PHONY: bazel-build
bazel-build: ## build everything
	@bazel build //...

.PHONY: bazel-test
bazel-test: ## run all tests
	@bazel test //...

#################################
# Docker targets
#################################

.PHONY: docker-build-knora-api-image
docker-build-knora-api-image: # build and publish knora-api docker image locally
	@bazel run //docker/knora-api

.PHONY: docker-publish-knora-api
docker-publish-knora-api-image: # publish knora-api image to Dockerhub
	@docker run //docker/knora-api:push

.PHONY: docker-build-knora-graphdb-se-image
docker-build-knora-graphdb-se-image: # build and publish knora-graphdb-se docker image locally
	@bazel run //docker/knora-graphdb-se

.PHONY: docker-publish-knora-graphdb-se-image
docker-publish-knora-graphdb-se-image: # publish knora-graphdb-se image to Dockerhub
	@bazel run //docker/knora-graphdb-se:push

.PHONY: docker-build-knora-graphdb-free-image
docker-build-knora-graphdb-free-image: # build and publish knora-graphdb-free docker image locally
	@bazel run //docker/knora-graphdb-free

.PHONY: docker-publish-knora-graphdb-free-image
docker-publish-knora-graphdb-free-image: # publish knora-graphdb-se image to Dockerhub
	@bazel run //docker/knora-graphdb-free:push

.PHONY: docker-build-knora-sipi-image
docker-build-knora-sipi-image: # build and publish knora-sipi docker image locally
	@bazel run //docker/knora-sipi

.PHONY: docker-publish-knora-sipi-image
docker-publish-knora-sipi-image: # publish knora-sipi image to Dockerhub
	@bazel run //docker/knora-sipi:push

.PHONY: docker-build-knora-salsah1-image
docker-build-knora-salsah1-image: # build and publish knora-salsah1 docker image locally
	@bazel run //docker/knora-salsah1

.PHONY: docker-publish-knora-salsah1-image
docker-publish-knora-salsah1-image: # publish knora-salsah1 image to Dockerhub
	@bazel run //docker/knora-salsah1:push

.PHONY: docker-build-knora-upgrade-image
docker-build-knora-upgrade-image: # build and publish knora-upgrade docker image locally
	@bazel run //docker/knora-upgrade

.PHONY: docker-publish-knora-upgrade-image
docker-publish-knora-upgrade-image: # publish knora-upgrade image to Dockerhub
	@bazel run //docker/knora-upgrade:push

.PHONY: docker-build
docker-build: docker-build-knora-api-image docker-build-knora-graphdb-se-image docker-build-knora-graphdb-free-image docker-build-knora-sipi-image docker-build-knora-salsah1-image docker-build-knora-upgrade-image  ## build and publish all Docker images locally

.PHONY: docker-publish
docker-publish: docker-publish-knora-api-image docker-publish-knora-graphdb-se-image docker-publish-knora-graphdb-free-image docker-publish-knora-sipi-image docker-publish-knora-salsah1-image docker-publish-knora-upgrade-image ## publish all Docker images to Dockerhub

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: env-file ## print the env file used by knora-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by knora-stack.
# FIXME: Generate .env file though bazel
ifeq ($(KNORA_GDB_LICENSE), unknown)
	$(warning No GraphDB-SE license set. Using GraphDB-Free.)
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_FREE_IMAGE) > .env
	@echo KNORA_GDB_LICENSE_FILE=no-license >> .env
	@echo KNORA_GDB_TYPE=graphdb-free >> .env
else
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_SE_IMAGE) > .env
	@echo KNORA_GDB_LICENSE_FILE=$(KNORA_GDB_LICENSE) >> .env
	@echo KNORA_GDB_TYPE=graphdb-se >> .env
endif
ifeq ($(KNORA_GDB_IMPORT), unknown)
	$(warning The path to the GraphDB import directory is not set. Using docker volume: db-import.)
	@echo KNORA_GDB_IMPORT_DIR=db-import >> .env
else
	@echo KNORA_GDB_IMPORT_DIR=$(KNORA_GDB_IMPORT) >> .env
endif
ifeq ($(KNORA_GDB_HOME), unknown)
	$(warning The path to the GraphDB home directory is not set. Using docker volume: db-home.)
	@echo KNORA_GDB_HOME_DIR=db-home >> .env
else
	@echo KNORA_GDB_HOME_DIR=$(KNORA_GDB_HOME) >> .env
endif
	@echo KNORA_GDB_HEAP_SIZE=$(KNORA_GDB_HEAP_SIZE) >> .env
	@echo KNORA_SIPI_IMAGE=$(KNORA_SIPI_IMAGE) >> .env
	@echo KNORA_API_IMAGE=$(KNORA_API_IMAGE) >> .env
	@echo KNORA_SALSAH1_IMAGE=$(KNORA_SALSAH1_IMAGE) >> .env
	@echo DOCKERHOST=$(DOCKERHOST) >> .env
	@echo KNORA_WEBAPI_DB_CONNECTIONS=$(KNORA_WEBAPI_DB_CONNECTIONS) >> .env

#################################
## Knora Stack Targets
#################################

.PHONY: stack-up
stack-up: docker-build env-file ## starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	@docker-compose -f docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: stack-up ## re-starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	@docker-compose -f docker-compose.yml restart

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into GraphDB.
	@docker-compose -f docker-compose.yml restart api

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running knora-stack.
	@docker-compose -f docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs sipi

.PHONY: stack-logs-redis
stack-logs-redis: ## prints out and follows the logs of the 'redis' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f redis

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f api

.PHONY: stack-logs-salsah1
stack-logs-salsah1: ## prints out and follows the logs of the 'salsah1' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f salsah1

.PHONY: stack-without-api
stack-without-api: stack-up ## starts the knora-stack without knora-api: graphdb, sipi, redis, salsah1.
	@docker-compose -f docker-compose.yml stop api

.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the knora-stack without knora-api and sipi: graphdb, redis, salsah1.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop sipi

.PHONY: stack-without-api-and-salsah1
stack-without-api-and-salsah1: stack-up ## starts the knora-stack without knora-api and salsah1: graphdb, redis, sipi.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop salsah1

.PHONY: stack-db-only
stack-db-only: stack-up ## starts only GraphDB.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop salsah1
	@docker-compose -f docker-compose.yml stop sipi
	@docker-compose -f docker-compose.yml stop redis

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	@docker-compose -f docker-compose.yml down

#################################
## Test Targets
#################################

.PHONY: test-webapi-all
test-webapi-all: stack-without-api ## runs all webapi tests.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	@bazel test //webapi/...

.PHONY: test-webapi-unit
test-webapi-unit: stack-without-api ## runs the webapi unit tests.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	@bazel test //webapi/...
	@docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest'

.PHONY: unit-tests-with-coverage
unit-tests-with-coverage: stack-without-api ## runs the unit tests (equivalent to 'sbt webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	@docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src/workspace \
				-w /src/workspace \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				l.gcr.io/google/bazel:1.2.1 \
				test //webapi:unit_tests

.PHONY: e2e-tests
e2e-tests: stack-without-api init-db-test-unit ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest').
	@docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest'

.PHONY: e2e-tests-with-coverage
e2e-tests-with-coverage: stack-without-api ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	@docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src/workspace \
				-w /src/workspace \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				l.gcr.io/google/bazel:1.2.1 \
				test //webapi:e2e_tests

.PHONY: it-tests
it-tests: stack-without-api init-db-test-unit ## runs the integration tests (equivalent to 'sbt webapi/it').
	@docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/it:test'

.PHONY: test-salsah1
test-salsah1: stack-up ## runs salsah1 headless browser tests
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-minimal
	@bazel test //salsah1/...

.PHONY: test-upgrade-unit
test-upgrade-unit: stack-down ## runs upgrade unit tests
	@echo $@  # print target name
	@bazel test //upgrade/...

.PHONY: test-upgrade-integration
test-upgrade-integration: stack-db-only clean-local-tmp ## runs upgrade integration test
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-minimal
	# unzip test data
	@mkdir -p $(PWD)/.tmp/test_data/v7.0.0/
	@unzip $(PWD)/test_data/v7.0.0/v7.0.0-knora-test.trig.zip -d $(PWD)/.tmp/test_data/v7.0.0/
	# empty repository
	@$(PWD)/upgrade/graphdb-se/empty-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200
	# load v7.0.0 data
	@$(PWD)/upgrade/graphdb-se/upload-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200 $(PWD)/test_data/v7.0.0/v7.0.0-knora-test.trig
	# dump repository data
	@mkdir -p $(PWD)/.tmp/upgrade/out
	@$(PWD)/upgrade/graphdb-se/dump-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200 $(PWD)/.tmp/upgrade/out/dump.trig
	# run upgrade from inside docker
	@docker run --rm --network=knora-net -v $(PWD)/.tmp/upgrade/out:/upgrade/out bazel/docker/knora-upgrade:knora-upgrade /upgrade/out/dump.trig /upgrade/out/dump-upgraded.trig
	# empty repository
	@$(PWD)/upgrade/graphdb-se/empty-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200
	# load upgraded data
	@$(PWD)/upgrade/graphdb-se/upload-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200 ./.tmp/upgrade/out/dump-upgraded.trig

.PHONY: test
test:  ## runs all test targets.
	@$(MAKE) -f $(THIS_FILE) test-webapi
	@$(MAKE) -f $(THIS_FILE) test-upgrade
	@$(MAKE) -f $(THIS_FILE) test-salsah1

#################################
## Database Management
#################################

.PHONY: init-db-test
init-db-test: ## initializes the knora-test repository
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test

.PHONY: init-db-test-minimal
init-db-test-minimal: ## initializes the knora-test repository with minimal data
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-minimal

.PHONY: init-db-test-unit
init-db-test-unit: ## initializes the knora-test-unit repository
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-unit

.PHONY: init-db-test-free
init-db-test-free: ## initializes the knora-test repository (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-free

.PHONY: init-db-test-minimal-free
init-db-test-minimal-free: ## initializes the knora-test repository with minimal data (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-minimal

.PHONY: init-db-test-unit-free
init-db-test-unit-free: ## initializes the knora-test-unit repository (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-unit

.PHONY: init-db-test-local
init-db-test-local: ## initializes the knora-test-unit repository (for a local GraphDB-SE)
	$(MAKE) -C webapi/scripts graphdb-se-local-init-knora-test

.PHONY: init-db-test-unit-local
init-db-test-unit-local: ## initializes the knora-test-unit repository (for a local GraphDB-SE)
	$(MAKE) -C webapi/scripts graphdb-se-local-init-knora-test-unit

#################################
## Other
#################################

.PHONY: clean-local-tmp
clean-local-tmp:
	@rm -rf .tmp
	@mkdir .tmp

clean: ## clean build artifacts
	@rm -rf .env
	@bazel clean
	@sbt clean

clean-docker: ## cleans the docker installation
	@docker system prune -af

.PHONY: info
info: ## print out all variables
	@echo "GIT_EMAIL: \t\t\t $(GIT_EMAIL)"
	@echo "KNORA_API_IMAGE: \t\t $(KNORA_API_IMAGE)"
	@echo "KNORA_GRAPHDB_SE_IMAGE: \t $(KNORA_GRAPHDB_SE_IMAGE)"
	@echo "KNORA_GRAPHDB_FREE_IMAGE: \t $(KNORA_GRAPHDB_FREE_IMAGE)"
	@echo "KNORA_SIPI_IMAGE: \t\t $(KNORA_SIPI_IMAGE)"
	@echo "KNORA_UPGRADE_IMAGE: \t\t $(KNORA_UPGRADE_IMAGE)"
	@echo "KNORA_SALSAH1_IMAGE: \t\t $(KNORA_SALSAH1_IMAGE)"
	@echo "KNORA_GDB_LICENSE: \t\t $(KNORA_GDB_LICENSE)"
	@echo "KNORA_GDB_IMPORT: \t\t $(KNORA_GDB_IMPORT)"
	@echo "KNORA_GDB_HOME: \t\t $(KNORA_GDB_HOME)"
	@echo "DOCKERHOST: \t\t\t $(DOCKERHOST)"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
