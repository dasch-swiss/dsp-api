# Determine this makefile's path.
# Be sure to place this BEFORE `include` directives, if any.
# THIS_FILE := $(lastword $(MAKEFILE_LIST))
THIS_FILE := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

include vars.mk

#################################
# Documentation targets
#################################

.PHONY: docs-publish
docs-publish: ## build and publish docs
	docker run --rm -it -v $(CURRENT_DIR):/knora -v $(HOME)/.ivy2:/root/.ivy2 -v $(HOME)/.ssh:/root/.ssh daschswiss/sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

.PHONY: docs-build
docs-build: ## build the docs
	docker run --rm -v $(CURRENT_DIR):/knora -v $(HOME)/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

#################################
# Docker targets
#################################

.PHONY: build-all-scala
build-all-scala: ## build all scala projects
	@sbt webapi/universal:stage knora-graphdb-se/universal:stage knora-graphdb-free/universal:stage knora-sipi/universal:stage salsah1/universal:stage knora-assets/universal:stage webapi_test/universal:stage webapi_it/universal:stage

## knora-api
.PHONY: build-knora-api-image
build-knora-api-image: build-all-scala ## build and publish knora-api docker image locally
	docker build -t $(KNORA_API_IMAGE) -f docker/knora-api.dockerfile  webapi/target/universal

.PHONY: publish-knora-api
publish-knora-api-image: build-knora-api-image ## publish knora-api image to Dockerhub
	docker push $(KNORA_API_IMAGE)

## knora-graphdb-se
.PHONY: build-knora-graphdb-se-image
build-knora-graphdb-se-image: build-all-scala ## build and publish knora-graphdb-se docker image locally
	@mkdir -p .docker
	@sed -e "s/@GRAPHDB_IMAGE@/daschswiss\/graphdb\:$(GRAPHDB_SE_VERSION)-se/" docker/knora-graphdb.template.dockerfile > .docker/knora-graphdb-se.dockerfile
ifeq ($(KNORA_GDB_LICENSE), unknown)
	touch $(CURRENT_DIR)/knora-graphdb-se/target/universal/stage/scripts/graphdb.license
else
	cp $(KNORA_GDB_LICENSE) $(CURRENT_DIR)/knora-graphdb-se/target/universal/stage/scripts/graphdb.license
endif
	docker build -t $(KNORA_GRAPHDB_SE_IMAGE) -t $(REPO_PREFIX)/$(KNORA_GRAPHDB_SE_REPO):latest -f .docker/knora-graphdb-se.dockerfile  knora-graphdb-se/target/universal

.PHONY: publish-knora-graphdb-se-image
publish-knora-graphdb-se-image: build-knora-graphdb-se-image ## publish knora-graphdb-se image to Dockerhub
	docker push $(KNORA_GRAPHDB_SE_IMAGE)

## knora-graphdb-free
.PHONY: build-knora-graphdb-free-image
build-knora-graphdb-free-image: build-all-scala ## build and publish knora-graphdb-free docker image locally
	@mkdir -p .docker
	@sed -e "s/@GRAPHDB_IMAGE@/daschswiss\/graphdb\:$(GRAPHDB_FREE_VERSION)-free/" docker/knora-graphdb.template.dockerfile > .docker/knora-graphdb-free.dockerfile
	touch $(CURRENT_DIR)/knora-graphdb-free/target/universal/stage/scripts/graphdb.license
	docker build -t $(KNORA_GRAPHDB_FREE_IMAGE) -f .docker/knora-graphdb-free.dockerfile  knora-graphdb-free/target/universal

.PHONY: publish-knora-graphdb-free-image
publish-knora-graphdb-free-image: build-knora-graphdb-free-image ## publish knora-graphdb-se image to Dockerhub
	docker push $(KNORA_GRAPHDB_FREE_IMAGE)

## knora-sipi
.PHONY: build-knora-sipi-image
build-knora-sipi-image: build-all-scala ## build and publish knora-sipi docker image locally
	@mkdir -p .docker
	@sed -e "s/@SIPI_VERSION@/$(SIPI_VERSION)/" docker/knora-sipi.template.dockerfile > .docker/knora-sipi.dockerfile
	docker build -t $(KNORA_SIPI_IMAGE) -f .docker/knora-sipi.dockerfile  knora-sipi/target/universal

.PHONY: publish-knora-sipi-image
publish-knora-sipi-image: build-knora-sipi-image ## publish knora-sipi image to Dockerhub
	docker push $(KNORA_SIPI_IMAGE)

## knora-salsah1
.PHONY: build-knora-salsah1-image
build-knora-salsah1-image: build-all-scala ## build and publish knora-salsah1 docker image locally
	docker build -t $(KNORA_SALSAH1_IMAGE) -f docker/knora-salsah1.dockerfile  salsah1/target/universal

.PHONY: publish-knora-salsah1-image
publish-knora-salsah1-image: build-knora-salsah1-image ## publish knora-salsah1 image to Dockerhub
	docker push $(KNORA_SALSAH1_IMAGE)

## knora-assets
.PHONY: build-knora-assets-image
build-knora-assets-image: build-all-scala ## build and publish knora-assets docker image locally
	docker build -t $(KNORA_ASSETS_IMAGE) -f docker/knora-assets.dockerfile  knora-assets/target/universal

.PHONY: publish-knora-assets-image
publish-knora-assets-image: build-knora-assets-image ## publish knora-assets image to Dockerhub
	docker push $(KNORA_ASSETS_IMAGE)

## all images
.PHONY: build-all-images
build-all-images: build-knora-api-image build-knora-graphdb-se-image build-knora-graphdb-free-image build-knora-sipi-image build-knora-salsah1-image build-knora-assets-image  ## build all Docker images

.PHONY: publish-all-images
publish-all-images: publish-knora-api-image publish-knora-graphdb-se-image publish-knora-graphdb-free-image publish-knora-sipi-image publish-knora-salsah1-image publish-knora-assets-image ## publish all Docker images

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: ## prints the env file used by knora-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by knora-stack.
ifeq ($(KNORA_GDB_LICENSE), unknown)
	$(warning No GraphDB-SE license set. Using GraphDB-Free.)
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_FREE_IMAGE) > .env
	@echo KNORA_GDB_TYPE=graphdb-free >> .env
else
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_SE_IMAGE) > .env
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
	@echo KNORA_GRAPHDB_REPOSITORY_NAME=$(KNORA_GRAPHDB_REPOSITORY_NAME) >> .env
	@echo LOCAL_HOME=$(CURRENT_DIR) >> .env

## knora stack
.PHONY: stack-up
stack-up: build-all-images env-file ## starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker/knora.docker-compose.yml up -d db
	-$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh -h $(DOCKERHOST):7200
	@$(MAKE) -f $(THIS_FILE) stack-logs-db-no-follow
	docker-compose -f docker/knora.docker-compose.yml up -d

.PHONY: stack-up-ci
stack-up-ci: KNORA_GRAPHDB_REPOSITORY_NAME := knora-test-unit
stack-up-ci: build-all-images env-file print-env-file ## starts the knora-stack using 'knora-test-unit' repository: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker/knora.docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: stack-up ## re-starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker/knora.docker-compose.yml restart

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into GraphDB.
	docker-compose -f docker/knora.docker-compose.yml restart api
	@$(CURRENT_DIR)/webapi/scripts/wait-for-knora.sh

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f db

.PHONY: stack-logs-db-no-follow
stack-logs-db-no-follow: ## prints out the logs of the 'db' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs sipi

.PHONY: stack-logs-redis
stack-logs-redis: ## prints out and follows the logs of the 'redis' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f redis

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f api

.PHONY: stack-logs-api-no-follow
stack-logs-api-no-follow: ## prints out the logs of the 'api' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs api

.PHONY: stack-logs-salsah1
stack-logs-salsah1: ## prints out and follows the logs of the 'salsah1' container running in knora-stack.
	docker-compose -f docker/knora.docker-compose.yml logs -f salsah1

.PHONY: stack-health
stack-health:
	curl -f 0.0.0.0:3333/health

.PHONY: stack-status
stack-status:
	docker-compose -f docker/knora.docker-compose.yml ps

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	docker-compose -f docker/knora.docker-compose.yml down

.PHONY: stack-config
stack-config:
	docker-compose -f docker/knora.docker-compose.yml config

## stack without api
.PHONY: stack-without-api
stack-without-api: stack-up ## starts the knora-stack without knora-api: graphdb, sipi, redis, salsah1.
	docker-compose -f docker/knora.docker-compose.yml stop api

## stack without api and sipi
.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the knora-stack without knora-api and sipi: graphdb, redis, salsah1.
	docker-compose -f docker/knora.docker-compose.yml stop api
	docker-compose -f docker/knora.docker-compose.yml stop sipi

.PHONY: test-only
test-only: stack-without-api init-db-test-unit ## runs only the supplied tests, e.g., make test-only TARGET="*.CORSSupportE2ESpec".
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp:delegated \
				-v $(CURRENT_DIR):/src:delegated \
				-v $(HOME)/.ivy2:/root/.ivy2:delegated \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt "webapi/testOnly $(TARGET)"

.PHONY: test-unit
test-unit: stack-without-api init-db-test-unit ## runs the unit tests (equivalent to 'sbt webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest').
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest'

.PHONY: test-unit-ci
test-unit-ci: stack-without-api init-db-test-unit ## runs the unit tests (equivalent to 'sbt webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt coverage 'webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest' webapi/coverageReport

.PHONY: test-e2e
test-e2e: stack-without-api init-db-test-unit ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest').
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest'

.PHONY: test-e2e-ci
test-e2e-ci: stack-without-api init-db-test-unit ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt coverage 'webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest' webapi/coverageReport

.PHONY: test-it
test-it: stack-without-api init-db-test-unit ## runs the integration tests (equivalent to 'sbt webapi/it').
	@echo $@
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/it:test'

.PHONY: test-it-ci
test-it-ci: stack-without-api init-db-test-unit ## runs the integration tests (equivalent to 'sbt webapi/it:test') with code-coverage reporting.
	@echo $@  # print target name
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt coverage webapi/it:test webapi/coverageReport

.PHONY: test-all
test-all: stack-without-api init-db-test-unit ## runs the all tests (equivalent to 'sbt webapi/test').
	@echo $@
	# docker build -t webapi-test -f docker/knora-api-test.dockerfile  webapi/build/test/target/universal
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(CURRENT_DIR):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt webapi/test

.PHONY: test-repository-update
test-repository-update: stack-without-api
	@sleep 15
	@$(MAKE) -f $(THIS_FILE) init-db-test-minimal
	@rm -rf /tmp/knora-test-data/v7.0.0/
	@mkdir -p /tmp/knora-test-data/v7.0.0/
	@unzip $(CURRENT_DIR)/test-data/v7.0.0/v7.0.0-knora-test.trig.zip -d /tmp/knora-test-data/v7.0.0/
	$(CURRENT_DIR)/webapi/scripts/graphdb-empty-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200
	$(CURRENT_DIR)/webapi/scripts/graphdb-upload-repository.sh -r knora-test -u gaga -p gaga -h localhost:7200 /tmp/knora-test-data/v7.0.0/v7.0.0-knora-test.trig
	@$(MAKE) -f $(THIS_FILE) stack-restart-api
	@$(MAKE) -f $(THIS_FILE) stack-logs-api-no-follow

.PHONY: init-db-test
init-db-test: ## initializes the knora-test repository
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test

.PHONY: init-db-test-minimal
init-db-test-minimal: ## initializes the knora-test repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-minimal

.PHONY: init-db-test-unit
init-db-test-unit: ## initializes the knora-test-unit repository
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-unit

.PHONY: init-db-test-unit-minimal
init-db-test-unit-minimal: ## initializes the knora-test-unit repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-unit-minimal

.PHONY: init-db-test-free
init-db-test-free: ## initializes the knora-test repository (for GraphDB-Free)
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-free

.PHONY: init-db-test-minimal-free
init-db-test-minimal-free: ## initializes the knora-test repository with minimal data (for GraphDB-Free)
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-minimal

.PHONY: init-db-test-unit-free
init-db-test-unit-free: ## initializes the knora-test-unit repository (for GraphDB-Free)
	@echo $@
	@$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-unit

#################################
# Other
#################################

.PHONY: clean-local-tmp
clean-local-tmp:
	@rm -rf $(CURRENT_DIR)/.tmp
	@mkdir $(CURRENT_DIR)/.tmp

clean: ## clean build artifacts
	@rm -rf .docker
	@rm -rf .env
	@sbt clean
	@rm -rf .tmp

clean-docker: ## cleans the docker installation
	docker system prune -af

.PHONY: info
info: ## print out all variables
	@echo "BUILD_TAG: \t\t\t $(BUILD_TAG)"
	@echo "GIT_EMAIL: \t\t\t $(GIT_EMAIL)"
	@echo "SIPI_VERSION: \t\t\t $(SIPI_VERSION)"
	@echo "GRAPHDB_SE_VERSION: \t\t $(GRAPHDB_SE_VERSION)"
	@echo "KNORA_API_IMAGE: \t\t $(KNORA_API_IMAGE)"
	@echo "KNORA_GRAPHDB_SE_IMAGE: \t $(KNORA_GRAPHDB_SE_IMAGE)"
	@echo "KNORA_GRAPHDB_FREE_IMAGE: \t $(KNORA_GRAPHDB_FREE_IMAGE)"
	@echo "KNORA_SIPI_IMAGE: \t\t $(KNORA_SIPI_IMAGE)"
	@echo "KNORA_ASSETS_IMAGE: \t\t $(KNORA_ASSETS_IMAGE)"
	@echo "KNORA_SALSAH1_IMAGE: \t\t $(KNORA_SALSAH1_IMAGE)"
	@echo "KNORA_GDB_LICENSE: \t\t $(KNORA_GDB_LICENSE)"
	@echo "KNORA_GDB_IMPORT: \t\t $(KNORA_GDB_IMPORT)"
	@echo "KNORA_GDB_HOME: \t\t $(KNORA_GDB_HOME)"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
