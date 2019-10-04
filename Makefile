include vars.mk

all: build-docs build-images ## builds the docs and all Docker images

#################################
# Documentation targets
#################################

.PHONY: publish-docs
publish-docs: ## build and publish docs
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 -v $HOME/.ssh:/root/.ssh sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

.PHONY: build-docs
build-docs: ## build the docs
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

#################################
# Docker targets
#################################

.PHONE: build-docker-images
build-docker-images: build-knora-api-image  ## build all Docker images locally

# knora-api
.PHONY: build-knora-api-image
build-knora-api-image: ## build and publish knora-api docker image locally
	sbt "webapi/universal:stage"
	docker build -t $(KNORA_API_IMAGE) -f docker/knora-api.Dockerfile  webapi/target/universal

.PHONY: publish-knora-api-image
publish-knora-api-image: build-knora-api-image ## publish knora-api image to Dockerhub
	docker push $(KNORA_API_IMAGE)

# knora-graphdb-se
.PHONY: build-knora-graphdb-se-image
build-knora-graphdb-se-image: ## build and publish knora-graphdb-se docker image locally
	sbt "knora-graphdb-se/universal:stage"
	@mkdir -p .docker
	@sed -e "s/@GRAPHDB_SE_VERSION@/$(GRAPHDB_SE_VERSION)/" docker/knora-graphdb-se.template.Dockerfile > .docker/knora-graphdb-se.Dockerfile
	docker build -t $(KNORA_GRAPHDB_SE_IMAGE) -f .docker/knora-graphdb-se.Dockerfile  knora-graphdb-se/target/universal

.PHONY: publish-knora-graphdb-se-image
publish-knora-graphdb-se-image: build-knora-graphdb-se-image ## publish knora-graphdb-se image to Dockerhub
	docker push $(KNORA_GRAPHDB_SE_IMAGE)

# knora-sipi
.PHONY: build-knora-sipi-image
build-knora-sipi-image: ## build and publish knora-sipi docker image locally
	sbt "knora-sipi/universal:stage"
	@mkdir -p .docker
	@sed -e "s/@SIPI_VERSION@/$(SIPI_VERSION)/" docker/knora-sipi.template.Dockerfile > .docker/knora-sipi.Dockerfile
	docker build -t $(KNORA_SIPI_IMAGE) -f .docker/knora-sipi.Dockerfile  knora-sipi/target/universal

.PHONY: publish-knora-sipi-image
publish-knora-sipi-image: build-knora-sipi-image ## publish knora-sipi image to Dockerhub
	docker push $(KNORA_SIPI_IMAGE)

# knora-salsah1
.PHONY: build-knora-salsah1-image
build-knora-salsah1-image: ## build and publish knora-salsah1 docker image locally
	sbt "salsah1/universal:stage"
	docker build -t $(KNORA_SALSAH1_IMAGE) -f docker/knora-salsah1.Dockerfile  salsah1/target/universal

.PHONY: publish-knora-salsah1-image
publish-knora-salsah1-image: build-knora-salsah1-image ## publish knora-salsah1 image to Dockerhub
	docker push $(KNORA_SALSAH_IMAGE)

# knora-upgrade
.PHONY: build-knora-upgrade-image
build-knora-upgrade-image: ## build and publish knora-upgrade docker image locally
	sbt "knora-upgrade/universal:stage"
	docker build -t $(KNORA_UPGRADE_IMAGE) -f docker/knora-upgrade.Dockerfile  knora-upgrade/target/universal

.PHONY: publish-knora-upgrade-image
publish-knora-upgrade-image: build-knora-upgrade-image ## publish knora-upgrade image to Dockerhub
	docker push $(KNORA_UPGRADE_IMAGE)

# knora-assets
.PHONY: build-knora-assets-image
build-knora-assets-image: ## build and publish knora-assets docker image locally
	sbt "knora-assets/universal:stage"
	docker build -t $(KNORA_ASSETS_IMAGE) -f docker/knora-assets.Dockerfile  knora-assets/target/universal

.PHONY: publish-knora-assets-image
publish-knora-assets-image: build-knora-assets-image ## publish knora-assets image to Dockerhub
	docker push $(KNORA_ASSETS_IMAGE)

# all images
.PHONY: build-docker-images
build-docker-images: build-knora-api-image build-knora-graphdb-se-image build-knora-sipi-image build-knora-upgrade-image build-knora-assets-image  ## build all Docker images

.PHONY: publish-docker-images
publish-docker-images: publish-knora-api-image publish-knora-graphdb-se-image publish-knora-sipi-image publish-knora-upgrade-image publish-knora-assets-image ## publish all Docker images

#################################
# Docker-Compose targets
#################################

.PHONY: knora-stack-up
knora-stack-up: export KNORA_GRAPHDB_SE_IMAGE_NAME=$(KNORA_GRAPHDB_SE_IMAGE)
knora-stack-up: export KNORA_SIPI_IMAGE_NAME=$(KNORA_SIPI_IMAGE)
knora-stack-up: export KNORA_API_IMAGE_NAME=$(KNORA_API_IMAGE)
knora-stack-up: export KNORA_SALSAH1_IMAGE_NAME=$(KNORA_SALSAH1_IMAGE)
knora-stack-up: build-docker-images ## starts the complete knora-stack: graphdb, sipi, redis, api, salsah1
ifeq ($(KNORA_GDB_LICENSE), "unknown")
	$(error The path to your GraphDB-SE license is not set.)
endif
ifeq ($(KNORA_GDB_HOME), "unknown")
	$(error The path to your GraphDB-SE home directory is not set.)
endif
	docker-compose -f docker/knora-stack-complete.docker-compose.yml up -d

.PHONY: knora-stack-logs
knora-stack-logs: ## prints out the logs of the running knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f

.PHONY: knora-stack-logs-db
knora-stack-logs-db: ## prints out the logs of the 'db' container running in knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f db

.PHONY: knora-stack-logs-sipi
knora-stack-logs-sipi: ## prints out the logs of the 'sipi' container running in knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f sipi

.PHONY: knora-stack-logs-redis
knora-stack-logs-redis: ## prints out the logs of the 'redis' container running in knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f redis

.PHONY: knora-stack-logs-api
knora-stack-logs-api: ## prints out the logs of the 'api' container running in knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f api

.PHONY: knora-stack-logs-salsah1
knora-stack-logs-salsah1: ## prints out the logs of the 'salsah1' container running in knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml logs -f salsah1

.PHONY: knora-stack-down
knora-stack-down: ## stops the knora-stack
	docker-compose -f docker/knora-stack-complete.docker-compose.yml down

clean: ## clean build artifacts
	rm -rf .docker

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
	@echo "KNORA_UPGRADE_IMAGE: \t\t $(KNORA_UPGRADE_IMAGE)"
	@echo "KNORA_SALSAH1_IMAGE: \t\t $(KNORA_SALSAH1_IMAGE)"
	@echo "KNORA_GDB_LICENSE: \t\t $(KNORA_GDB_LICENSE)"
	@echo "KNORA_GDB_HOME: \t\t $(KNORA_GDB_HOME)"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT: all
