FROM adoptopenjdk/openjdk11:alpine-jre

RUN \
    apk update && \
    apk upgrade && \
    apk add bash

COPY stage /webapi-test

WORKDIR /webapi-test

EXPOSE 3333

ENTRYPOINT ["webapi-test/bin/webapi-test"]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
