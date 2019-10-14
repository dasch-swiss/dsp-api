FROM adoptopenjdk/openjdk11:alpine-jre

ADD stage /webapi-test

WORKDIR /webapi-test

EXPOSE 3333

ENTRYPOINT ["bin/webapi-test"]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
