#!/usr/bin/env bash

#set -x

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in

    -h|--host)
      HOST="$2"
      shift # past argument
      shift # past value
      ;;

    -t|--timeout)
      TIMEOUT="$2"
      shift # past argument
      shift # past value
      ;;

    *) # unknown option
      POSITIONAL+=("$1") # save it in an array for later
      shift # past argument
      ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [[ -z "${HOST}" ]]; then
    HOST="localhost:3333"
fi

if [[ -z "${TIMEOUT}" ]]; then
    TIMEOUT=360
fi

check-health() {
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' http://${HOST}/health)

    if [ "${STATUS}" -eq 200 ]; then
        echo "==> DSP-API started"
        return 0
    else
        return 1
    fi
}

attempt_counter=0

until check-health; do
    if [ ${attempt_counter} -eq ${TIMEOUT} ]; then
      echo "==> Timed out waiting for DSP-API to start"
      exit 1
    fi

    attempt_counter=$((attempt_counter+1))
    sleep 1
done
