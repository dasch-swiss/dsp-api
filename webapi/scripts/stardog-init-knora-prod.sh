#!/usr/bin/env bash

stardog-admin db drop knora-prod

set -e

# Prints the absolute path of a file, to work around a bug in the 'stardog' command.
realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

stardog-admin db create -c ./stardog-knora-prod.properties
stardog data add --named-graph http://www.knora.org/ontology/knora-base knora-test $(realpath ../../knora-ontologies/knora-base.ttl)
stardog data add --named-graph http://www.knora.org/ontology/knora-base knora-test $(realpath ../../knora-ontologies/knora-admin.ttl)
stardog data add --named-graph http://www.knora.org/ontology/standoff knora-test $(realpath ../../knora-ontologies/standoff-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/standoff knora-test $(realpath ../../knora-ontologies/standoff-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/dc knora-test $(realpath ../../knora-ontologies/knora-dc.ttl)
stardog data add --named-graph http://www.knora.org/ontology/salsah-gui knora-test $(realpath ../../knora-ontologies/salsah-gui.ttl)
stardog data add --named-graph http://www.knora.org/data/admin knora-test $(realpath ../_test_data/all_data/admin-data.ttl)
stardog data add --named-graph http://www.knora.org/data/permissions knora-test $(realpath ../_test_data/all_data/permissions-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/incunabula knora-test $(realpath ../_test_data/ontologies/incunabula-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/incunabula knora-test $(realpath ../_test_data/all_data/incunabula-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/dokubib knora-test $(realpath ../_test_data/ontologies/dokubib-onto.ttl)
stardog data add --named-graph http://www.knora.org/ontology/images knora-test $(realpath ../_test_data/ontologies/images-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/images knora-test $(realpath ../_test_data/demo_data/images-demo-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/anything knora-test $(realpath ../_test_data/ontologies/anything-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/anything knora-test $(realpath ../_test_data/all_data/anything-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/beol knora-test $(realpath ../_test_data/ontologies/beol-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/beol knora-test $(realpath ../_test_data/all_data/beol-data.ttl)
stardog data add --named-graph http://www.knora.org/ontology/biblio knora-test $(realpath ../_test_data/ontologies/biblio-onto.ttl)
stardog data add --named-graph http://www.knora.org/data/biblio knora-test $(realpath ../_test_data/all_data/biblio-data.ttl)
