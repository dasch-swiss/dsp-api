FROM dhlabbasel/webapi-base:latest

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Get and build webapi release package, cleanup
COPY . /src

RUN \
  cd /src && \
  sbt stage && \
  mkdir /webapi && \
  cp -r /src/target/universal/stage/* /webapi && \
  rm -rf /src

VOLUME ["/localdata"]

# Expose the webapi default port
EXPOSE 3333

CMD ["/webapi/bin/webapi"]