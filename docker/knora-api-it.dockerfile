FROM adoptopenjdk/openjdk11:alpine-jre

ADD stage /webapi-it

WORKDIR /webapi-it

EXPOSE 3333

ENTRYPOINT ["bin/webapi-it]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
