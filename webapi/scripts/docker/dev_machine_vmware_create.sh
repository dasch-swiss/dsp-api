#!/usr/bin/env bash

docker-machine create -d vmwarefusion \
    --vmwarefusion-cpu-count "2" \
    --vmwarefusion-memory-size "8192" \
    dev