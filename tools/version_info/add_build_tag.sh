#!/bin/bash

tag=$(awk '/BUILD_SCM_TAG/ {print $2}' "$1")
sed "s/{BUILD_TAG}/$tag/" "$2"
