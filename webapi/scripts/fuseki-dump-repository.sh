#!/usr/bin/env bash

#set -x

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -r|--repository)
    REPOSITORY="$2"
    shift # past argument
    shift # past value
    ;;
    -u|--username)
    USER_NAME="$2"
    shift # past argument
    shift # past value
    ;;
    -p|--password)
    PASSWORD="$2"
    shift # past argument
    shift # past value
    ;;
    -h|--host)
    HOST="$2"
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

FILE="$1"

if [[ -z "${REPOSITORY}" || -z "${USER_NAME}" || -z "${FILE}" ]]; then
    echo "Usage: $(basename "$0") -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE"
    exit 1
fi

if [[ -z "${PASSWORD}" ]]; then
    echo -n "Password: "
    IFS="" read -rs PASSWORD
    echo
fi

if [[ -z "${HOST}" ]]; then
    HOST="localhost:3030"
fi

curl -sS -X GET -H "Accept: application/trig" -u "${USER_NAME}:${PASSWORD}" "http://${HOST}/${REPOSITORY}" > "${FILE}"
