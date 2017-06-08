FROM dhlabbasel/webapi-base:latest

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Get and build salsah release package, cleanup
COPY . /src

RUN \
  cd /src && \
  sbt stage && \
  mkdir /salsah && \
  cp -r /src/target/universal/stage/* /salsah && \
  rm -rf /src

VOLUME["/localdata"]

# Expose the webapi default port
EXPOSE 3335

CMD ["/salsah/bin/salsah"]