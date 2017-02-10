#!/usr/bin/env bash

set -e

GRAPHDB="http://localhost:7200"
CONSOLE="openrdf-console/bin/console.sh --force --echo --serverURL $GRAPHDB"

GREEN='\033[0;32m'
RED='\033[0;31m'
NO_COLOUR='\033[0m'
DELIMITER="****************************************************************************************************\n* "

printf "${GREEN}${DELIMITER}Deleting repository${NO_COLOUR}\n\n"

cat graphdb-se-drop-knora-prod-repository.ttl | $CONSOLE

printf "\n${GREEN}${DELIMITER}Creating repository${NO_COLOUR}\n\n"

# in this docker version of the script, the path to the KnoraRules.pie is fixed
sed -e 's@PIE_FILE@'"/localdata/graphdb/KnoraRules.pie"'@' graphdb-se-knora-prod-repository-config.ttl.tmpl > graphdb-se-knora-prod-repository-config.ttl

curl -X POST -H "Content-Type:application/x-turtle" -T graphdb-se-knora-prod-repository-config.ttl "$GRAPHDB/repositories/SYSTEM/rdf-graphs/service?graph=http://www.knora.org/config"

curl -X POST -H "Content-Type:application/x-turtle" -d "<http://www.knora.org/config> a <http://www.openrdf.org/config/repository#RepositoryContext>." $GRAPHDB/repositories/SYSTEM/statements

printf "${GREEN}Repository created.\n\n${DELIMITER}Loading test data${NO_COLOUR}\n\n"

cat graphdb-se-knora-prod-data.ttl | $CONSOLE

printf "\n${GREEN}${DELIMITER}Creating Lucene index${NO_COLOUR}\n\n"

STATUS=$(curl -s -w '%{http_code}' -S -X POST -H "Content-Type:text/turtle" --data-binary @./graphdb-se-knora-prod-index-config.ttl $GRAPHDB/repositories/knora-prod/statements)

if [ "$STATUS" == "204" ]
then
    printf "${GREEN}Lucene index built.${NO_COLOUR}\n\n"
else
    printf "${RED}Building of Lucene index failed: ${STATUS}${NO_COLOUR}\n\n"
fi

