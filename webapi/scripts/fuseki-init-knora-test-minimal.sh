#!/usr/bin/env bash

# Including fuseki-funcions.sh implementing delete, create, and upload.
source fuseki-functions.sh

# Name of the repository
REPOSITORY="knora-test"

# delete-repository // delete dos not work correctly. need to delete database manually.
create-repository
upload-graph ../../knora-ontologies/knora-admin.ttl http://www.knora.org/ontology/knora-admin
upload-graph ../../knora-ontologies/knora-base.ttl http://www.knora.org/ontology/knora-base
upload-graph ../../knora-ontologies/standoff-onto.ttl http://www.knora.org/ontology/standoff
upload-graph ../../knora-ontologies/standoff-data.ttl http://www.knora.org/data/standoff
upload-graph ../../knora-ontologies/salsah-gui.ttl http://www.knora.org/ontology/salsah-gui
upload-graph ../../test_data/project_data/admin-data-minimal.ttl http://www.knora.org/data/admin
upload-graph ../../test_data/project_data/permissions-data-minimal.ttl http://www.knora.org/data/permissions
