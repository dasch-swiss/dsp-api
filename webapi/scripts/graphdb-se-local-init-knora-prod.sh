#!/usr/bin/env bash

set -e

GRAPHDB="http://localhost:7200"
CONSOLE="openrdf-console/bin/console.sh --force --echo --serverURL $GRAPHDB"

GREEN='\033[0;32m'
RED='\033[0;31m'
NO_COLOUR='\033[0m'
DELIMITER="****************************************************************************************************\n* "

printf "${GREEN}${DELIMITER}Deleting repository${NO_COLOUR}\n\n"

cat graphdb-drop-knora-prod-repository.ttl | $CONSOLE

printf "\n${GREEN}${DELIMITER}Creating repository${NO_COLOUR}\n\n"

sed -e 's@PIE_FILE@'"$PWD/KnoraRules.pie"'@' graphdb-se-knora-prod-repository-config.ttl.tmpl > graphdb-se-knora-prod-repository-config.ttl

curl -X POST -H "Content-Type:text/turtle" -T graphdb-se-knora-prod-repository-config.ttl "$GRAPHDB/repositories/SYSTEM/rdf-graphs/service?graph=http://www.knora.org/config-prod"

curl -X POST -H "Content-Type:text/turtle" -d "<http://www.knora.org/config-prod> a <http://www.openrdf.org/config/repository#RepositoryContext>." $GRAPHDB/repositories/SYSTEM/statements

printf "${GREEN}Repository created.\n\n${DELIMITER}Creating Lucene Index${NO_COLOUR}\n\n"

STATUS=$(curl -s -w '%{http_code}' -S -X POST --data-urlencode 'update@./graphdb-knora-index-create.rq' $GRAPHDB/repositories/knora-prod/statements)

if [ "$STATUS" == "204" ]
then
    printf "${GREEN}Lucene index built.${NO_COLOUR}\n\n"
else
    printf "${RED}Building of Lucene index failed: ${STATUS}${NO_COLOUR}\n\n"
fi

printf "${GREEN}${DELIMITER}Loading Data${NO_COLOUR}\n\n"

./graphdb-knora-prod-data.expect $GRAPHDB

printf "\n${GREEN}Data Loaded.\n\n${DELIMITER}Updating Lucene Index${NO_COLOUR}\n\n"

curl -X POST --data-urlencode 'update@./graphdb-knora-index-update.rq' $GRAPHDB/repositories/knora-prod/statements

printf "${GREEN}Lucene Index Updated.${NO_COLOUR}\n"
