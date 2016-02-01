#!/usr/bin/env bash
cd /builds/salsah-suite/rapier-scala
sbt "project webapi" "tdb:test"
