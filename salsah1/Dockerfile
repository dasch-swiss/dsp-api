# first stage does the building
FROM dhlabbasel/scala-sbt:master as build-stage

LABEL maintainer="ivan.subotic@unibas.ch"

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# copy source code
COPY . /src

# generate deployment packaging
WORKDIR /src
RUN sbt stage

# starting second stage
FROM openjdk:10-jre-slim-sid

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"
ENV KNORA_SALSAH1_DEPLOYED=true
ENV KNORA_SALSAH1_WORKDIR=/salsah

## get the packaged webapi from the build stage
COPY --from=build-stage /src/target/universal/stage /salsah

# Expose the salsah default port
EXPOSE 3335

ENTRYPOINT ["/salsah/bin/salsah"]