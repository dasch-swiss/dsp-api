##################################
## first stage does the building
##################################
FROM dhlabbasel/scala-sbt:master as build-stage

LABEL maintainer="ivan.subotic@unibas.ch"

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

## copy source code
COPY . /src

## generate deployment packaging
WORKDIR /src
RUN sbt stage

##################################
## starting second stage
##################################
FROM openjdk:10-jre-slim

LABEL maintainer="ivan.subotic@unibas.ch"

## install wget
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget=1.18-5+deb9u2 && \
    rm -rf /var/lib/apt/lists/*

## install yourkit profiler
RUN wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2018.04-docker.zip -P /tmp/ && \
    unzip /tmp/YourKit-JavaProfiler-2018.04-docker.zip -d /usr/local && \
    rm /tmp/YourKit-JavaProfiler-2018.04-docker.zip

## get the packaged webapi from the build stage
COPY --from=build-stage /src/target/universal/stage /webapi

# Expose the webapi default port
EXPOSE 3333
EXPOSE 10001

ENTRYPOINT ["/webapi/bin/webapi", "-J-agentpath:/usr/local/YourKit-JavaProfiler-2018.04/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all"]