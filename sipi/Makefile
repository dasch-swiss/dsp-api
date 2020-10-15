include ../vars.mk

.PHONY: print-env-file
print-env-file: env-file ## prints the env file used by sipi
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by sipi
	@echo SIPI_IMAGE=$(SIPI_IMAGE) > .env
	@echo DOCKERHOST=$(DOCKERHOST) >> .env

.PHONY: up
up: env-file ## start sipi
	docker-compose up -d sipi

.PHONY: logs
logs: env-file ## follow logs
	docker-compose logs -f sipi

.PHONY: down
down: env-file ## stop sipi
	docker-compose down

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
