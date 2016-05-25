#!/usr/bin/env bash

curl -X POST -H "Content-type:application/x-www-form-urlencoded" --data-urlencode update='DROP ALL' http://localhost:3030/knora-test/update > /dev/null
curl -F filedata=@../../knora-ontologies/knora-base.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/knora-base > /dev/null
curl -F filedata=@../../knora-ontologies/knora-dc.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/dc > /dev/null
curl -F filedata=@../../knora-ontologies/salsah-gui.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/salsah-gui > /dev/null
curl -F filedata=@../_test_data/ontologies/incunabula-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/incunabula > /dev/null
curl -F filedata=@../_test_data/all_data/incunabula-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/incunabula > /dev/null
curl -F filedata=@../_test_data/ontologies/images-demo-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/images > /dev/null
curl -F filedata=@../_test_data/demo_data/images-demo-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/images > /dev/null
curl -F filedata=@../_test_data/ontologies/anything-onto.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/ontology/anything > /dev/null
curl -F filedata=@../_test_data/all_data/anything-data.ttl http://localhost:3030/knora-test/data?graph=http://www.knora.org/data/anything > /dev/null

