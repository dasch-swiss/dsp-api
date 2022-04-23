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
docs-publish: ## build and publish docs to Github Pages
	@$(MAKE) -C docs graphvizfigures
	mkdocs gh-deploy

.PHONY: docs-build
docs-build: ## build docs into the local 'site' folder
	@$(MAKE) -C docs graphvizfigures
	mkdocs build

.PHONY: docs-serve
docs-serve: ## serve docs for local viewing
	@$(MAKE) -C docs graphvizfigures
	mkdocs serve

.PHONY: docs-install-requirements
docs-install-requirements: ## install requirements
	pip3 install -r docs/requirements.txt

.PHONY: docs-clean
docs-clean: ## cleans the project directory
	@rm -rf site/

.PHONY: structurizer
structurizer: ## starts the structurizer and serves c4 architecture docs
	@docker pull structurizr/lite
	@docker run -it --rm -p 8080:8080 -v $(CURRENT_DIR)/docs/architecture:/usr/local/structurizr structurizr/lite

#################################
# Docker targets
#################################

.PHONY: build
build: docker-build ## build all targets (excluding docs)

.PHONY: docker-build-dsp-api-image
docker-build-dsp-api-image: # build and publish dsp-api docker image locally
	@sbt "webapi / Docker / publishLocal"

.PHONY: docker-publish-dsp-api-image
docker-publish-dsp-api-image: # publish dsp-api image to Dockerhub
	@sbt "webapi / Docker / publish"

.PHONY: docker-build-sipi-image
docker-build-sipi-image: # build and publish sipi docker image locally
	@sbt "sipi / Docker / publishLocal"

.PHONY: docker-publish-sipi-image
docker-publish-sipi-image: # publish sipi image to Dockerhub
	@sbt "sipi / Docker / publish"

.PHONY: docker-build
docker-build: docker-build-dsp-api-image docker-build-sipi-image ## build and publish all Docker images locally

.PHONY: docker-publish
docker-publish: docker-publish-dsp-api-image docker-publish-sipi-image ## publish all Docker images to Dockerhub

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: ## prints the env file used by knora-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by knora-stack.
	@echo DOCKERHOST=$(DOCKERHOST) > .env
	@echo KNORA_DB_REPOSITORY_NAME=$(KNORA_DB_REPOSITORY_NAME) >> .env
	@echo LOCAL_HOME=$(CURRENT_DIR) >> .env

#################################
## Knora Stack Targets
#################################

.PHONY: stack-up
stack-up: docker-build env-file ## starts the knora-stack: fuseki, sipi, redis, api.
	@docker compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh
	@docker compose -f docker-compose.yml up -d
	$(CURRENT_DIR)/webapi/scripts/wait-for-knora.sh

.PHONY: stack-up-fast
stack-up-fast: docker-build-knora-api-image env-file ## starts the knora-stack by skipping rebuilding most of the images (only api image is rebuilt).
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-up-ci
stack-up-ci: KNORA_DB_REPOSITORY_NAME := knora-test-unit
stack-up-ci: docker-build env-file print-env-file ## starts the knora-stack using 'knora-test-unit' repository: fuseki, sipi, redis, api.
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: stack-up ## re-starts the knora-stack: fuseki, sipi, redis, api.
	@docker compose -f docker-compose.yml restart

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into fuseki.
	docker-compose -f docker-compose.yml restart api
	@$(CURRENT_DIR)/webapi/scripts/wait-for-knora.sh

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running knora-stack.
	@docker compose -f docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in knora-stack.
	@docker compose -f docker-compose.yml logs -f db

.PHONY: stack-logs-db-no-follow
stack-logs-db-no-follow: ## prints out the logs of the 'db' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in knora-stack.
	@docker compose -f docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in knora-stack.
	@docker compose -f docker-compose.yml logs sipi

.PHONY: stack-logs-redis
stack-logs-redis: ## prints out and follows the logs of the 'redis' container running in knora-stack.
	@docker compose -f docker-compose.yml logs -f redis

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in knora-stack.
	@docker compose -f docker-compose.yml logs -f api

.PHONY: stack-logs-api-no-follow
stack-logs-api-no-follow: ## prints out the logs of the 'api' container running in knora-stack.
	@docker compose -f docker-compose.yml logs api

.PHONY: stack-health
stack-health:
	curl -f 0.0.0.0:3333/health

.PHONY: stack-status
stack-status:
	@docker compose -f docker-compose.yml ps

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	@docker compose -f docker-compose.yml down

.PHONY: stack-down-delete-volumes
stack-down-delete-volumes: ## stops the knora-stack and deletes any created volumes (deletes the database!).
	@docker compose -f docker-compose.yml down --volumes

.PHONY: stack-config
stack-config: env-file
	@docker compose -f docker-compose.yml config

## stack without api
.PHONY: stack-without-api
stack-without-api: stack-up ## starts the knora-stack without knora-api: fuseki, sipi, redis.
	@docker compose -f docker-compose.yml stop api

.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the knora-stack without knora-api and sipi: fuseki, redis.
	@docker compose -f docker-compose.yml stop api
	@docker compose -f docker-compose.yml stop sipi

.PHONY: stack-db-only
stack-db-only: env-file  ## starts only fuseki.
	@docker compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh

#################################
## Test Targets
#################################

.PHONY: client-test-data
client-test-data: export KNORA_WEBAPI_COLLECT_CLIENT_TEST_DATA := true
client-test-data: build ## runs the dsp-api e2e tests and generates client test data.
	$(CURRENT_DIR)/webapi/scripts/zap-client-test-data.sh
	sbt -v "webapi/testOnly *E2ESpec *R2RSpec"
	$(CURRENT_DIR)/webapi/scripts/zip-client-test-data.sh

.PHONY: test-repository-upgrade
test-repository-upgrade: build init-db-test-minimal ## runs DB upgrade integration test
	@rm -rf $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	@mkdir -p $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	@unzip $(CURRENT_DIR)/test_data/v7.0.0/v7.0.0-knora-test.trig.zip -d $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	# empty repository
	$(CURRENT_DIR)/webapi/scripts/fuseki-empty-repository.sh -r knora-test -u admin -p test -h localhost:3030
	# load v7.0.0 data
	$(CURRENT_DIR)/webapi/scripts/fuseki-upload-repository.sh -r knora-test -u admin -p test -h localhost:3030 $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/v7.0.0-knora-test.trig
	# call target which restarts the API and emits error if API does not start
	# after a certain time. at startup, data should be upgraded.
	@$(MAKE) -f $(THIS_FILE) stack-up

.PHONY: test
test: build ## runs all tests
	sbt -v +test

#################################
## Database Management
#################################

.PHONY: init-db-test
init-db-test: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test

.PHONY: init-db-test-minimal
init-db-test-minimal: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-minimal

.PHONY: init-db-test-empty
init-db-test-empty: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-empty

.PHONY: init-db-test-unit
init-db-test-unit: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test-unit repository
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-unit

.PHONY: init-db-test-unit-minimal
init-db-test-unit-minimal: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test-unit repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-unit-minimal

## Dump test data
db_test_dump.trig:
	@echo $@
	@curl -X GET -H "Accept: application/trig" -u "admin:${DB_TEST_PASSWORD}" "https://db.test.dasch.swiss/dsp-repo" > "db_test_dump.trig"

## Dump staging data
db_staging_dump.trig:
	@echo $@
	@curl -X GET -H "Accept: application/trig" -u "admin:${DB_STAGING_PASSWORD}" "https://db.staging.dasch.swiss/dsp-repo" > "db_staging_dump.trig"

## Dump production data
db_prod_dump.trig:
	@echo $@
	@curl -X GET -H "Accept: application/trig" -u "admin:${DB_PROD_PASSWORD}" "https://db.dasch.swiss/dsp-repo" > "db_prod_dump.trig"

.PHONY: init-db-test-from-test
init-db-test-from-test: db_test_dump.trig init-db-test-empty ## init local database with data from test
	@echo $@
	@curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "admin:test" "http://localhost:3030/knora-test"
	@curl -X POST -H "Content-Type: application/trig" --data-binary "@${CURRENT_DIR}/db_test_dump.trig" -u "admin:test" "http://localhost:3030/knora-test"

.PHONY: init-db-test-from-staging
init-db-test-from-staging: db_staging_dump.trig init-db-test-empty ## init local database with data from staging
	@echo $@
	@curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "admin:test" "http://localhost:3030/knora-test"
	@curl -X POST -H "Content-Type: application/trig" --data-binary "@${CURRENT_DIR}/db_staging_dump.trig" -u "admin:test" "http://localhost:3030/knora-test"

.PHONY: init-db-test-from-prod
init-db-test-from-prod: db_prod_dump.trig init-db-test-empty ## init local database with data from production
	@echo $@
	@curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "admin:test" "http://localhost:3030/knora-test"
	@curl -X POST -H "Content-Type: application/trig" --data-binary "@${CURRENT_DIR}/db_prod_dump.trig" -u "admin:test" "http://localhost:3030/knora-test"

#################################
## Other
#################################

clean-docker: ## cleans the docker installation
	@docker system prune -af
	@docker volume prune -f

.PHONY: clean-local-tmp
clean-local-tmp:
	@rm -rf .tmp
	@mkdir .tmp

clean: docs-clean clean-local-tmp clean-docker ## clean build artifacts
	@rm -rf .env

.PHONY: clean-sipi-tmp
clean-sipi-tmp: ## deletes all files in Sipi's tmp folder
	@rm -rf sipi/images/tmp/*

.PHONY: clean-sipi-projects
clean-sipi-projects: ## deletes all files uploaded within a project
	@rm -rf sipi/images/[0-9A-F][0-9A-F][0-9A-F][0-9A-F]
	@rm -rf sipi/images/originals/[0-9A-F][0-9A-F][0-9A-F][0-9A-F]

.PHONY: info
info: ## print out all variables
	@echo "BUILD_TAG: \t\t $(BUILD_TAG)"
	@echo "GIT_EMAIL: \t\t $(GIT_EMAIL)"

.PHONY: check
check: # Run code formating check 
	@sbt "check"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
