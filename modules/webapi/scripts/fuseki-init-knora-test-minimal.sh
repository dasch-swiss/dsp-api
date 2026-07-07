#!/usr/bin/env bash
source fuseki-functions.sh

# Load data
upload-graph ../src/main/resources/knora-ontologies/knora-admin.ttl http://www.knora.org/ontology/knora-admin
upload-graph ../src/main/resources/knora-ontologies/knora-base.ttl http://www.knora.org/ontology/knora-base
upload-graph ../src/main/resources/knora-ontologies/standoff-onto.ttl http://www.knora.org/ontology/standoff
upload-graph ../src/main/resources/knora-ontologies/standoff-data.ttl http://www.knora.org/data/standoff
upload-graph ../src/main/resources/knora-ontologies/salsah-gui.ttl http://www.knora.org/ontology/salsah-gui
upload-graph ../../../test_data/project_data/admin-data-minimal.ttl http://www.knora.org/data/admin
