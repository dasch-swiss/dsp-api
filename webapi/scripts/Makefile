.PHONY: graphdb-se-docker-init-knora-test
graphdb-se-docker-init-knora-test: ## initializes docker GraphDB-SE with the knora-test repository
	./graphdb-se-docker-init-knora-test.sh

.PHONY: graphdb-se-docker-init-knora-test-minimal
graphdb-se-docker-init-knora-test-minimal: ## initializes docker GraphDB-SE with the knora-test repository and minimal data
	./graphdb-se-docker-init-knora-test-minimal.sh

.PHONY: graphdb-se-docker-init-knora-test-unit
graphdb-se-docker-init-knora-test-unit: ## initializes docker GraphDB-SE with the knora-test-unit repository
	./graphdb-se-docker-init-knora-test-unit.sh

.PHONY: graphdb-free-docker-init-knora-test
graphdb-free-docker-init-knora-test: ## initializes docker GraphDB-Free with the knora-test repository
	./graphdb-free-docker-init-knora-test.sh

.PHONY: graphdb-free-docker-init-knora-test-minimal
graphdb-free-docker-init-knora-test-minimal: ## initializes docker GraphDB-Free with the knora-test repository and minimal data
	./graphdb-free-docker-init-knora-test-minimal.sh

.PHONY: graphdb-free-docker-init-knora-test-unit
graphdb-free-docker-init-knora-test-unit: ## initializes docker GraphDB-Free with the knora-test-unit repository
	./graphdb-free-docker-init-knora-test-unit.sh

.PHONY: graphdb-se-local-init-knora-test
graphdb-se-local-init-knora-test: ## initializes local GraphDB-SE with the knora-test repository
	./graphdb-se-local-init-knora-test.sh

.PHONY: graphdb-se-local-init-knora-test-minimal
graphdb-se-local-init-knora-test-minimal: ## initializes local GraphDB-SE with the knora-test repository and minimal data
	./graphdb-se-local-init-knora-test-minimal.sh

.PHONY: graphdb-se-local-init-knora-test-unit
graphdb-se-local-init-knora-test-unit: ## initializes local GraphDB-SE with the knora-test-unit repository
	./graphdb-se-local-init-knora-test-unit.sh


.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help