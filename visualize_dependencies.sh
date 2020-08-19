#!/bin/bash

# this script visualizes the dependency graph
bazel query --noimplicit_deps "deps(//webapi/src/main/scala/org/knora/webapi/app:app_cli)" --output graph > graph.in
dot -Tpng < graph.in > graph.png
open ./graph.png
