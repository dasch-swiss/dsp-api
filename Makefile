# Makefile

publish-all: publish-all-docker-images

publish-all-docker-images: ## publish all Docker images
	docker login -u $DOCKER_USER -p $DOCKER_PASS
	sbt "webapi/docker:publish"

publish-docs: build-docs ## build and publish docs
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 -v $HOME/.ssh:/root/.ssh sbt-paradox /bin/sh -c "cd /knora && git config --global user.email '400790+subotic@users.noreply.github.com' && sbt docs/ghpagesPushSite"

build-docs:
	docker run --rm -it -v $PWD:/knora -v $HOME/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

webapi/target/docker/stage/opt/Dockerfile: ## write out the webapi Dockerfile
	sbt "webapi/docker:stage"

build-webapi-image: webapi/target/docker/stage/opt/Dockerfile ## build and publish webapi docker image locally
	sbt "webapi/docker:publishLocal"

help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort
