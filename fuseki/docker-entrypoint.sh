#!/bin/bash
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

set -e

# Log with the same logging format as Fuseki
log() {
  printf "%s %-5s Entrypoint      :: %s\n" "$(date +%H:%M:%S)" "$1" "$2"
}
info() {
  log "INFO" "$1"
}
warn() {
  log "WARN" "$1" >&2
}
error() {
  log "ERROR" "$1" >&2
}

# copy shiro.ini
cp "$FUSEKI_HOME/shiro.ini" "$FUSEKI_BASE/shiro.ini"

# $ADMIN_PASSWORD can always override
if [ -n "$ADMIN_PASSWORD" ] ; then
  sed -i "s/^admin=[^,]\+/admin=$ADMIN_PASSWORD/" "$FUSEKI_BASE/shiro.ini"
fi

# generate password for healthcheck
openssl rand -hex 32 > "/var/tmp/healthcheck_password"
sed -i "s/^healthcheck=[^,]\+/healthcheck=$(cat "/var/tmp/healthcheck_password")/" "$FUSEKI_BASE/shiro.ini"

# password placeholder sanity check
if grep -Fq "=pw" "$FUSEKI_BASE/shiro.ini"; then
  error "Password placeholder was not replaced!"
  exit 1
fi

# copy dsp-repo.ttl
if [ ! -e "$FUSEKI_BASE/configuration/dsp-repo.ttl" ]; then
  mkdir -p "$FUSEKI_BASE/configuration"
  cp "$FUSEKI_HOME/dsp-repo.ttl" "$FUSEKI_BASE/configuration/dsp-repo.ttl"
fi

# Check if index rebuild marker file exists
if [ ! -n "$REBUILD_INDEX_OF_DATASET" ] && [ -f "$REBUILD_INDEX_MARKER_FILE" ] ; then
  info "Detected index rebuild marker file ${REBUILD_INDEX_MARKER_FILE}"
  REBUILD_INDEX_OF_DATASET="$(cat "$REBUILD_INDEX_MARKER_FILE" | sed "s/[^[:alpha:]_-]/_/g")"
  remove_marker_file=true
fi

# Rebuild lucene index of the dataset specified
# by REBUILD_INDEX_OF_DATASET if set
if [ -n "$REBUILD_INDEX_OF_DATASET" ] ; then
  index_base="${INDEX_BASE:-${FUSEKI_BASE}/lucene}"
  if [ -d "${index_base}/${REBUILD_INDEX_OF_DATASET}" ] ; then
    info "Deleting old index data of dataset ${REBUILD_INDEX_OF_DATASET}"
    if ! rm -r "${index_base}/${REBUILD_INDEX_OF_DATASET}" ; then
      error "Failed deleting old index data"
      exit 1
    fi
  fi
  info "Rebuilding index of dataset ${REBUILD_INDEX_OF_DATASET}..."
  if java -cp "${FUSEKI_HOME}/fuseki-server.jar" jena.textindexer --desc="${FUSEKI_BASE}/configuration/${REBUILD_INDEX_OF_DATASET}.ttl" ; then
    info "Successfully rebuilt index"
    # Remove marker on successful rebuild
    if [ "$remove_marker_file" = true ] && ! rm "$REBUILD_INDEX_MARKER_FILE" ; then
      warn "Failed removing index rebuild marker file ${REBUILD_INDEX_MARKER_FILE}"
    fi
  else
    error "Failed rebuilding index"
    exit 1
  fi
fi

# Change JVM_ARGS to address warnings in Java 21+:
#   WARNING: A restricted method in java.lang.foreign.Linker has been called
#   WARNING: java.lang.foreign.Linker::downcallHandle has been called by the unnamed module
#   WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for this module
#   WARN  VectorizationProvider :: Java vector incubator module is not readable. For optimal vector performance, pass '--add-modules jdk.incubator.vector' to enable Vector API.
# See also:
#   https://github.com/apache/jena/issues/2533
#   https://github.com/apache/jena/pull/2782
#   https://github.com/apache/jena/blob/c14193e528a86e97f0ab86cf3827d6ba9ac60296/jena-text/pom.xml#L32-L39
if [ -z "$JVM_ARGS" ]; then
  # this is the default if not set, see /jena-fuseki/fuseki-server wrapper
  JVM_ARGS="-Xmx4G"
fi
#export JVM_ARGS="${JVM_ARGS} --enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector"
info "JVM_ARGS: $JVM_ARGS"

# Start Fueski server
exec "$@"
