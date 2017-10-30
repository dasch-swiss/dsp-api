FROM dhlabbasel/webapi-base:latest

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# copy source code
RUN mkdir /src
COPY . /src

# generate deployment jars and clean up.
RUN \
  cd /src && \
  sbt stage && \
  mkdir /webapi && \
  cp -r /src/target/universal/stage/* /webapi && \
  rm -rf /src

# Expose the webapi default port
EXPOSE 3333

CMD ["/webapi/bin/webapi"]