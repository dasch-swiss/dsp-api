#!/usr/bin/env bash

stardog-admin db drop knora-test-unit

set -e

# Prints the absolute path of a file, to work around a bug in the 'stardog' command.
realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

stardog-admin db create -c ./stardog-knora-test-unit.properties
