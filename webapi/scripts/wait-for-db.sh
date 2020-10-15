#!/usr/bin/env bash

#set -x

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in

  -h | --host)
    HOST="$2"
    shift # past argument
    shift # past value
    ;;

  -t | --timeout)
    TIMEOUT="$2"
    shift # past argument
    shift # past value
    ;;

   -u | --username)
    USER_NAME="$2"
    shift # past argument
    shift # past value
    ;;

  -p | --password)
    PASSWORD="$2"
    shift # past argument
    shift # past value
    ;;

  *) # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift              # past argument
    ;;
  esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [[ -z "${HOST}" ]]; then
  HOST="localhost:3030"
fi

if [[ -z "${TIMEOUT}" ]]; then
  TIMEOUT=360
fi

if [[ -z "${USER_NAME}" ]]; then
  USER_NAME="admin"
fi

if [[ -z "${PASSWORD}" ]]; then
  PASSWORD="test"
fi

poll-db() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u ${USER_NAME}:${PASSWORD} http://${HOST}/\$/server)

  if [ "${STATUS}" -eq 200 ]; then
    echo "==> DB started"
    return 0
  else
    return 1
  fi
}

attempt_counter=0

until poll-db; do
  if [ ${attempt_counter} -eq ${TIMEOUT} ]; then
    echo "Timed out waiting for DB to start"
    exit 1
  fi

  attempt_counter=$((attempt_counter + 1))
  sleep 1
done
