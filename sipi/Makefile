.PHONY: up
up: ## start sipi
	docker-compose up -d sipi

.PHONY: logs
logs: ## follow logs
	docker-compose logs -f sipi

.PHONY: down
down: ## stop sipi
	docker-compose down

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help