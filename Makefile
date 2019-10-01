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

.PHONE: build-images
build-images: build-knora-api-image  ## build all Docker images locally

# knora-api
webapi/target/universal/stage/bin/webapi: ## build and write out packaged knora-api
	sbt "webapi/universal:stage"

.PHONY: build-knora-api-image
build-knora-api-image: webapi/target/universal/stage/bin/webapi  ## build and publish knora-api docker image locally
	docker build -t $(KNORA_API_IMAGE) -f docker/knora-api.Dockerfile  .

.PHONY: publish-knora-api-image
publish-knora-api-image: build-knora-api-image ## publish knora-api image to Dockerhub
    docker push $(KNORA_API_IMAGE)

# knora-graphdb-se
graphdb-se/target/universal/stage/bin/webapi: ## build and write out packaged knora-api
	sbt "graphdb-se/universal:stage"

.PHONY: build-knora-api-image
build-knora-graphdb-se-image: graphdb-se/target/universal/stage/bin/webapi  ## build and publish knora-api docker image locally
	docker build -t $(KNORA_API_IMAGE) -f docker/knora-api.Dockerfile  .

.PHONY: publish-knora-api-image
publish-knora-api-image: build-knora-api-image ## publish knora-api image to Dockerhub
    docker push $(KNORA_API_IMAGE)


# knora-graphdb-free

# knora-sipi



.PHONY: publish-all-docker-images
publish-all-docker-images: publish-knora-api-image ## publish all Docker images

#################################
# Docker-Compose targets
#################################

.PHONY: knora-stack-complete
knora-stack-complete: .docker/knora-stack-complete.docker-compose.yml ## starts the complete knora-stack: graphdb, sipi, redis, api, salsah1
    docker-compose -f .docker/knora-stack-complete.docker-compose.yml

.docker/knora-stack-complete.docker-compose.yml:
    envsubst < docker/knora-stack-complete.docker-compoae.yml.template > .docker/knora-stack-complete.docker-compose.yml

clean: ## clean build artifacts
    rm -rf .docker

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT: all
