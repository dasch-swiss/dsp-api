#!/bin/bash

# Log with the same logging format as Fuseki
log() {
  printf "%s %-5s Healthcheck     :: %s\n" "$(date +%H:%M:%S)" "$1" "$2"
}
warn() {
  log "WARN" "$1" >&2
}

if ! curl -s --fail 'http://localhost:3030/$/ping' > /dev/null; then
  warn "Not responding to ping"
  exit 1
fi

healthcheck_user='healthcheck'
healthcheck_password="$(cat "/var/tmp/healthcheck_password")"
if ! curl -s --fail 'http://localhost:3030/$/datasets/dsp-repo' --config - \
     <<< '--user "'"${healthcheck_user}:${healthcheck_password}"'"' > /dev/null
then
  warn "Dataset 'dsp-repo' is missing or unavailable"
  exit 1
fi
