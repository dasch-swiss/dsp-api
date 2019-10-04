.PHONY: graphdb-se-docker-init-knora-test
graphdb-se-docker-init-knora-test: ## initializes GraphDB-DE with the knora-test repository
	./graphdb-se-docker-init-knora-test.sh

.PHONY: graphdb-se-docker-init-knora-test-minimal
graphdb-se-docker-init-knora-test-minimal: ## initializes GraphDB-DE with the knora-test repository and minimal data
	./graphdb-se-docker-init-knora-test-minimal.sh

.PHONY: graphdb-se-docker-init-knora-test-unit
graphdb-se-docker-init-knora-test-unit: ## initializes GraphDB-SE with the knora-test-unit repository
	./graphdb-se-docker-init-knora-test-unit.sh

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help