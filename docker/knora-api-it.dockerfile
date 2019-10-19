FROM adoptopenjdk/openjdk11:alpine-jre

RUN \
    apk update && \
    apk upgrade && \
    apk add bash

COPY stage /webapi-it

WORKDIR /webapi-it

EXPOSE 3333

ENTRYPOINT ["/webapi-it/bin/webapi-it]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
