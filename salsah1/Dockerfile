# first stage does the building
FROM dhlabbasel/scala-sbt as build-stage

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# copy source code
COPY . /src

# generate deployment packaging
RUN \
  cd /src && \
  sbt stage

# starting second stage
FROM openjdk:10-jre-slim

## need bash for the webapi start script
# RUN apk add --no-cache bash

## get the packaged webapi from the build stage
COPY --from=build-stage /src/target/universal/stage /salsah

# Expose the salsah default port
EXPOSE 3335

CMD ["/salsah/bin/salsah"]