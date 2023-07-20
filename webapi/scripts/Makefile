.PHONY: fuseki-init-knora-test
fuseki-init-knora-test: ## initializes Fuseki with the knora-test repository and data
	./fuseki-init-knora-test.sh

.PHONY: fuseki-init-knora-test-minimal
fuseki-init-knora-test-minimal: ## initializes Fuseki with the knora-test repository and minimal data
	./fuseki-init-knora-test-minimal.sh

.PHONY: fuseki-init-knora-test-empty
fuseki-init-knora-test-empty: ## initializes Fuseki with the knora-test repository and no data
	./fuseki-init-knora-test-empty.sh

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
