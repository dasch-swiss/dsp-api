#!/usr/bin/env bash

#set -x

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in
  -u | --username)
    USERNAME="$2"
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

REPOSITORY="knora-test-unit"

if [[ -z "${HOST}" ]]; then
  HOST="localhost:3030"
fi

if [[ -z "${USERNAME}" ]]; then
  USERNAME="admin"
fi

if [[ -z "${PASSWORD}" ]]; then
  PASSWORD="test"
fi

delete-repository() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u ${USERNAME}:${PASSWORD} -X DELETE http://${HOST}/\$/datasets/${REPOSITORY})

  if [ "${STATUS}" -eq 200 ]; then
    echo "==> delete repository done"
    return 0
  else
    echo "==> delete repository failed"
    return 1
  fi
}

create-repository() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u ${USERNAME}:${PASSWORD} -F data=@./fuseki-knora-test-unit-repository-config.ttl http://${HOST}/\$/datasets)

  if [ "${STATUS}" -eq 200 ]; then
    echo "==> create repository done"
    return 0
  else
    echo "==> create repository failed"
    return 1
  fi
}

upload-graph() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -u ${USERNAME}:${PASSWORD} -H "Content-Type:text/turtle" -d @$1 -X PUT http://${HOST}/${REPOSITORY}\?graph\="$2")

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

# delete-repository // delete dos not work correctly. need to delete database manually.
create-repository
upload-graph ../../knora-ontologies/knora-admin.ttl http://www.knora.org/ontology/knora-admin
upload-graph ../../knora-ontologies/knora-base.ttl http://www.knora.org/ontology/knora-base
upload-graph ../../knora-ontologies/standoff-onto.ttl http://www.knora.org/ontology/standoff
upload-graph ../../knora-ontologies/standoff-data.ttl http://www.knora.org/data/standoff
upload-graph ../../knora-ontologies/salsah-gui.ttl http://www.knora.org/ontology/salsah-gui
upload-graph ../_test_data/all_data/admin-data.ttl http://www.knora.org/data/admin
upload-graph ../_test_data/all_data/permissions-data.ttl http://www.knora.org/data/permissions
upload-graph ../_test_data/all_data/system-data.ttl http://www.knora.org/data/0000/SystemProject
