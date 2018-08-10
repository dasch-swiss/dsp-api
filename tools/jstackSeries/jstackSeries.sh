#!/usr/bin/env bash

if [ $# -eq 0 ]; then
    echo >&2 "Usage: jstackSeries <pid> [ <count> [ <delay> ] ]"
    echo >&2 "    Defaults: count = 720, delay = 10 (seconds)"
    exit 1
fi

pid=$1          # required
count=${2:-720}  # defaults to 720 times (runs for 2 hours)
delay=${3:-10} # defaults to 10 second

while [ $count -gt 0 ]
do
    jstack $pid >jstack.$pid.$(date +%H%M%S.%N)
    sleep $delay
    let count--
    echo -n "."
done
