#!/usr/bin/env bash

curl -X POST -H "Content-type:application/x-www-form-urlencoded" --data-urlencode update='DROP ALL' http://localhost:3030/knora-test/update > /dev/null
curl -F filedata=@../../knora-ontologies/knora-base.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/knora-base > /dev/null
curl -F filedata=@../../knora-ontologies/knora-dc.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/dc > /dev/null
curl -F filedata=@../../knora-ontologies/salsah-gui.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/salsah-gui > /dev/null
curl -F filedata=@../_test_data/ontologies/incunabula-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/incunabula > /dev/null
curl -F filedata=@../_test_data/all_data/incunabula-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/incunabula > /dev/null
