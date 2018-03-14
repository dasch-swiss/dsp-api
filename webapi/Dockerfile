##################################
## first stage does the building
##################################
FROM dhlabbasel/sbt-ivy2-cache as build-stage

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

## copy source code
RUN mkdir /src
COPY . /src

## generate deployment packaging
RUN \
  cd /src && \
  sbt stage && \
  mkdir /webapi && \
  cp -r /src/target/universal/stage/* /webapi

##################################
## starting second stage
##################################
FROM openjdk:8-jre-alpine

## need bash for the webapi start script
RUN apk add --no-cache bash

## get the packaged webapi from the build stage
COPY --from=build-stage /webapi /webapi

# Expose the webapi default port
EXPOSE 3333

CMD ["/webapi/bin/webapi"]