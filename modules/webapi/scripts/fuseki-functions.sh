#!/usr/bin/env bash

#set -x

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in
  -r | --repository)
    REPOSITORY="$2"
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
  -h | --host)
    HOST="$2"
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

FILE="$1"

if [[ -z "${REPOSITORY}" ]]; then
  REPOSITORY="dsp-repo"
fi

if [[ -z "${HOST}" ]]; then
  HOST="localhost:3030"
fi

if [[ -z "${USER_NAME}" ]]; then
  USER_NAME="admin"
fi

if [[ -z "${PASSWORD}" ]]; then
  PASSWORD="test"
fi

upload-graph() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u ${USER_NAME}:${PASSWORD} -H "Content-Type:text/turtle; charset=utf-8" --data-binary @$1 -X PUT http://${HOST}/${REPOSITORY}/data\?graph\="$2")

  if [ "${STATUS}" -eq 201 ]; then
    echo "==> 201 Created: $1 -> $2"
    return 0
  elif [ "${STATUS}" -eq 200 ]; then
    echo "==> 200 OK: $1 -> $2"
    return 0
  else
    echo "==> failed with status code ${STATUS}: $1 -> $2"
    return 1
  fi
}
