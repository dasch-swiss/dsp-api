FROM adoptopenjdk/openjdk11:alpine-jre

RUN \
    apk update && \
    apk upgrade && \
    apk add bash

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"
ENV KNORA_SALSAH1_DEPLOYED=true
ENV KNORA_SALSAH1_WORKDIR=/salsah1

COPY stage /salsah1

WORKDIR /salsah1
EXPOSE 3335
ENTRYPOINT ["/salsah1/bin/salsah1"]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
