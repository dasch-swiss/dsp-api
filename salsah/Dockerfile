# first stage does the building
FROM dhlabbasel/sbt-ivy2-cache as build-stage

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

ENV LANG="en_US.UTF-8"
ENV JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"

# copy source code
RUN mkdir /src
COPY . /src

# generate deployment packaging
RUN \
  cd /src && \
  sbt stage && \
  mkdir /salsah && \
  cp -r /src/target/universal/stage/* /salsah

# starting second stage
FROM openjdk:8-jre-alpine

COPY --from=build-stage /salsah /salsah

# Expose the salsah default port
EXPOSE 3335

CMD ["/salsah/bin/salsah"]