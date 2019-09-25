include vars.mk

publish-all: publish-all-docker-images

publish-all-docker-images: ## publish all Docker images
	docker login -u $DOCKER_USER -p $DOCKER_PASS
	sbt "webapi/docker:publish"

publish-docs: build-docs ## build and publish docs
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 -v $HOME/.ssh:/root/.ssh sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

build-docs:
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

webapi/target/universal/stage/lib/Dockerfile: ## write out the knora-api Dockerfile
	sbt "webapi/universal:stage"

build-knora-api-image: docker/knora-api.Dockerfile  ## build and publish knora-api docker image locally
	sbt "webapi/docker:publishLocal"

help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort
