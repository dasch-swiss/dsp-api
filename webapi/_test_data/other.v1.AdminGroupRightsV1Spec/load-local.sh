#!/usr/bin/env bash

set -e

GRAPHDB="http://localhost:7200"
CONSOLE="console --force --echo --serverURL $GRAPHDB"
cat load.ttl | $CONSOLE
