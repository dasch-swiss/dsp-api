#!/usr/bin/env bash

#set -x
set -e

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
    USERNAME="$2"
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

if [[ -z "${REPOSITORY}" || -z "${USERNAME}" ]]; then
    echo "Usage: $(basename "$0") -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST]"
    exit 1
fi

if [[ -z "${PASSWORD}" ]]; then
    echo -n "Password: "
    IFS="" read -rs PASSWORD
    echo
fi

if [[ -z "${HOST}" ]]; then
    HOST="localhost:7200"
fi

TEMP_DIR=$(mktemp -d)

INPUT_FILE=${TEMP_DIR}/dump.trig
OUTPUT_FILE=${TEMP_DIR}/transformed.trig

# Download the repository.
echo "Downloading repository..."
curl -X GET -H "Accept: application/trig" -u "${USERNAME}:${PASSWORD}" "http://${HOST}/repositories/${REPOSITORY}/statements?infer=false" > "${INPUT_FILE}"

# Transform the downloaded file.
echo "Checking for needed transformations..."
(cd ../.. && sbt "upgrade/run ${INPUT_FILE} ${OUTPUT_FILE}")

# If a transformed file was produced, empty the repository and upload the transformed file.
if [[ -f "${OUTPUT_FILE}" ]]; then
    echo "Emptying repository..."
    curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "${USERNAME}:${PASSWORD}" "http://${HOST}/repositories/${REPOSITORY}/statements"

    echo "Uploading transformed data to repository..."
    curl -X POST -H "Content-Type: application/trig" -d "@${OUTPUT_FILE}" -u "${USERNAME}:${PASSWORD}" "http://${HOST}/repositories/${REPOSITORY}/statements" | tee /dev/null
fi

rm -r "${TEMP_DIR}"
echo "Done."
