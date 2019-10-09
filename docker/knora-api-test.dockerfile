FROM openjdk:10-jre-slim-sid

ADD stage /webapi-test

WORKDIR /webapi-test

EXPOSE 3333
EXPOSE 10001

ENTRYPOINT ["bin/webapi-test"]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
