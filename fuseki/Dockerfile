#   Licensed to the Apache Software Foundation (ASF) under one or more
#   contributor license agreements.  See the NOTICE file distributed with
#   this work for additional information regarding copyright ownership.
#   The ASF licenses this file to You under the Apache License, Version 2.0
#   (the "License"); you may not use this file except in compliance with
#   the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

# Credits: Mainly copied from https://github.com/stain/jena-docker

FROM eclipse-temurin:21-jre-jammy

ENV LANG C.UTF-8
# hadolint ignore=DL3008
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
       tini bash curl ca-certificates findutils coreutils pwgen procps \
    ; \
    rm -rf /var/lib/apt/lists/*


# Update below according to https://jena.apache.org/download/
# and checksum for apache-jena-fuseki-<version>.tar.gz.sha512
ARG IMAGE_VERSION=5.5.0-3
ARG FUSEKI_VERSION=5.5.0
ARG FUSEKI_SHA512=bfbf59eac731b71bcf8e148f2abeda9b4adca215639eef3bba61243b308572b23a9a70a43c1483247f9894bfb23d69b59e0e64e1da47cb3cfc592aa979084d5c
ENV IMAGE_VERSION=$IMAGE_VERSION
ENV FUSEKI_VERSION=$FUSEKI_VERSION
ENV FUSEKI_SHA512=$FUSEKI_SHA512
# No need for https due to sha512 checksums below
ENV ASF_MIRROR http://www.apache.org/dyn/mirrors/mirrors.cgi?action=download&filename=
ENV ASF_ARCHIVE http://archive.apache.org/dist/

LABEL org.opencontainers.image.url https://github.com/dasch-swiss/dsp-api/tree/main/fuseki
LABEL org.opencontainers.image.source https://github.com/dasch-swiss/dsp-api
LABEL org.opencontainers.image.documentation https://jena.apache.org/documentation/fuseki2/
LABEL org.opencontainers.image.title "Apache Jena Fuseki"
LABEL org.opencontainers.image.description "Fuseki is a SPARQL 1.1 server with a web interface, backed by the Apache Jena TDB RDF triple store."
LABEL org.opencontainers.image.version ${IMAGE_VERSION}
LABEL org.opencontainers.image.licenses "(Apache-2.0 AND (GPL-2.0 WITH Classpath-exception-2.0) AND GPL-3.0)"
LABEL org.opencontainers.image.authors "DaSCH — https://dasch.swiss"

# Config and data
VOLUME /fuseki
ENV FUSEKI_BASE /fuseki
ENV INDEX_BASE $FUSEKI_BASE/lucene

# Installation folder
ENV FUSEKI_HOME /jena-fuseki

WORKDIR /tmp
# published sha512 checksum
RUN echo "$FUSEKI_SHA512  fuseki.tar.gz" > fuseki.tar.gz.sha512
# Download/check/unpack/move in one go (to reduce image size)
RUN  (curl --location --silent --show-error --fail --retry-connrefused --retry 3 --output fuseki.tar.gz ${ASF_MIRROR}jena/binaries/apache-jena-fuseki-$FUSEKI_VERSION.tar.gz || \
      curl --fail --silent --show-error --retry-connrefused --retry 3 --output fuseki.tar.gz $ASF_ARCHIVE/jena/binaries/apache-jena-fuseki-$FUSEKI_VERSION.tar.gz) && \
      sha512sum -c fuseki.tar.gz.sha512 && \
      tar zxf fuseki.tar.gz && \
      mv apache-jena-fuseki* $FUSEKI_HOME && \
      rm fuseki.tar.gz* && \
      rm -rf "$FUSEKI_HOME/fuseki.war" && chmod 755 "$FUSEKI_HOME/fuseki-server"

# Verify that the binary works
RUN  bash -c '[ "$($FUSEKI_HOME/fuseki-server --version)" == "Apache Jena Fuseki version $FUSEKI_VERSION" ]'

# shiro.ini contains a default password. To override, start
# container with ADMIN_PASSWORD environment variable set
COPY shiro.ini $FUSEKI_HOME/shiro.ini

# Create built-in database config
COPY dsp-repo.ttl $FUSEKI_HOME/dsp-repo.ttl

# Create entrypoint
COPY docker-entrypoint.sh /
RUN chmod 755 /docker-entrypoint.sh

# Create healthcheck
COPY healthcheck.sh /
RUN chmod 755 /healthcheck.sh

# Periodically check whether Fuseki is running and dsp-repo exists
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=30s \
  CMD /healthcheck.sh || exit 1

# Add otel java agent and pyroscope extension
ARG OTEL_AGENT_VERSION=v2.21.0
ARG OTEL_PYROSCOPE_VERSION=v1.0.4
ADD "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" "/usr/local/lib/opentelemetry-javaagent.jar"
ADD "https://github.com/grafana/otel-profiling-java/releases/download/${OTEL_PYROSCOPE_VERSION}/pyroscope-otel.jar" "/usr/local/lib/pyroscope-otel.jar"

# Where we start our server from
WORKDIR $FUSEKI_HOME
EXPOSE 3030
ENTRYPOINT ["/usr/bin/tini", "--", "/docker-entrypoint.sh"]
CMD ["/jena-fuseki/fuseki-server"]
