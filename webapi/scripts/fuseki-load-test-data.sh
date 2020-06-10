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

FILE="$1"

if [[ -z "${REPOSITORY}" || -z "${USERNAME}" || -z "${PASSWORD}" ]]; then
    echo "Usage: $(basename "$0") -r|--repository REPOSITORY -u|--username USERNAME [-p|--password PASSWORD] [-h|--host HOST] FILE"
    exit 1
fi

if [[ -z "${HOST}" ]]; then
    HOST="localhost:3030"
fi

drop-all() {
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Content-type:application/x-www-form-urlencoded"?update='DROP ALL' http://${HOST}/${REPOSITORY}/update)

    if [ "${STATUS}" -eq 200 ]; then
        echo "drop all done"
        return 0
    else
      echo "drop all failed"
        return 1
    fi
}

upload() {
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST)

    if [ "${STATUS}" -eq 200 ]; then
        echo "Knora started"
        return 0
    else
        return 1
    fi
}

curl -X POST -H "Content-type:application/x-www-form-urlencoded"?update='DROP ALL' http://localhost:3030/knora-test/update > /dev/null
curl -F filedata=@../../knora-ontologies/knora-admin.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/knora-admin > /dev/null
curl -F filedata=@../../knora-ontologies/knora-base.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/knora-base > /dev/null
curl -F filedata=@../../knora-ontologies/standoff-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/standoff > /dev/null
curl -F filedata=@../../knora-ontologies/standoff-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/standoff > /dev/null
curl -F filedata=@../../knora-ontologies/salsah-gui.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/salsah-gui > /dev/null
curl -F filedata=@../_test_data/all_data/admin-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/admin > /dev/null
curl -F filedata=@../_test_data/all_data/permissions-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/permissions > /dev/null
curl -F filedata=@../_test_data/all_data/system-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/0000/SystemProject > /dev/null
curl -F filedata=@../_test_data/ontologies/incunabula-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0803/incunabula > /dev/null
curl -F filedata=@../_test_data/all_data/incunabula-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/0803/incunabula > /dev/null
curl -F filedata=@../_test_data/ontologies/dokubib-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0804/dokubib > /dev/null
curl -F filedata=@../_test_data/ontologies/images-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/00FF/images > /dev/null
curl -F filedata=@../_test_data/demo_data/images-demo-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/00FF/images > /dev/null
curl -F filedata=@../_test_data/ontologies/anything-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0001/anything > /dev/null
curl -F filedata=@../_test_data/all_data/anything-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/0001/anything > /dev/null
curl -F filedata=@../_test_data/ontologies/something-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0001/something > /dev/null
curl -F filedata=@../_test_data/ontologies/beol-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/beol > /dev/null
curl -F filedata=@../_test_data/ontologies/biblio-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/biblio > /dev/null
curl -F filedata=@../_test_data/ontologies/newton-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/newton > /dev/null
curl -F filedata=@../_test_data/ontologies/leibniz-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/leibniz > /dev/null
curl -F filedata=@../_test_data/all_data/biblio-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/0801/biblio > /dev/null
curl -F filedata=@../_test_data/all_data/beol-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/0801/beol > /dev/null
curl -F filedata=@../_test_data/ontologies/webern-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/08AE/webern > /dev/null
curl -F filedata=@../_test_data/all_data/webern-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/08AE/webern > /dev/null
curl -F filedata=@../_test_data/ontologies/newton-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/newton > /dev/null
curl -F filedata=@../_test_data/ontologies/leibniz-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/0801/leibniz > /dev/null
