#!/bin/bash

# this script visualizes the dependency graph
# bazel query --noimplicit_deps "deps(//webapi/src/main/scala/org/knora/webapi/app:app_cli)" --output graph | tred > graph.in
# bazel query "kind(scala_library, deps(//webapi/src/main/scala/org/knora/webapi/app:app_cli))" --output=graph | tred > graph.in
bazel query --noimplicit_deps "rdeps(..., //webapi/src/main/scala/org/knora/webapi/responders)" --output graph | tred > graph.in
# bazel aquery --noimplicit_deps "deps(//webapi/src/main/scala/org/knora/webapi/responders)" --output graph | tred > graph.in
dot -Tsvg < graph.in > graph.svg
open ./graph.svg
