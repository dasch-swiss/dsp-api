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
upload-graph ../../test_data/project_data/admin-data.ttl http://www.knora.org/data/admin
upload-graph ../../test_data/project_data/permissions-data.ttl http://www.knora.org/data/permissions
upload-graph ../../test_data/project_data/system-data.ttl http://www.knora.org/data/0000/SystemProject
upload-graph ../../test_data/project_ontologies/incunabula-onto.ttl http://www.knora.org/ontology/0803/incunabula
upload-graph ../../test_data/project_data/incunabula-data.ttl http://www.knora.org/data/0803/incunabula
upload-graph ../../test_data/project_ontologies/dokubib-onto.ttl http://www.knora.org/ontology/0804/dokubib
upload-graph ../../test_data/project_ontologies/images-onto.ttl http://www.knora.org/ontology/00FF/images
upload-graph ../../test_data/project_data/images-demo-data.ttl http://www.knora.org/data/00FF/images
upload-graph ../../test_data/project_ontologies/anything-onto.ttl http://www.knora.org/ontology/0001/anything
upload-graph ../../test_data/project_data/anything-data.ttl http://www.knora.org/data/0001/anything
upload-graph ../../test_data/project_ontologies/minimal-onto.ttl http://www.knora.org/ontology/0001/minimal
upload-graph ../../test_data/project_ontologies/something-onto.ttl http://www.knora.org/ontology/0001/something
upload-graph ../../test_data/project_ontologies/beol-onto.ttl http://www.knora.org/ontology/0801/beol
upload-graph ../../test_data/project_ontologies/biblio-onto.ttl http://www.knora.org/ontology/0801/biblio
upload-graph ../../test_data/project_ontologies/newton-onto.ttl http://www.knora.org/ontology/0801/newton
upload-graph ../../test_data/project_ontologies/leibniz-onto.ttl http://www.knora.org/ontology/0801/leibniz
upload-graph ../../test_data/project_data/biblio-data.ttl http://www.knora.org/data/0801/biblio
upload-graph ../../test_data/project_data/beol-data.ttl http://www.knora.org/data/0801/beol
upload-graph ../../test_data/project_ontologies/webern-onto.ttl http://www.knora.org/ontology/0806/webern
upload-graph ../../test_data/project_data/webern-data.ttl http://www.knora.org/data/0806/webern
upload-graph ../../test_data/project_ontologies/freetest-onto.ttl http://www.knora.org/ontology/0001/freetest
upload-graph ../../test_data/project_data/freetest-data.ttl http://www.knora.org/data/0001/freetest
upload-graph ../../test_data/project_ontologies/sequences-onto.ttl http://www.knora.org/ontology/0001/sequences
upload-graph ../../test_data/project_data/sequences-data.ttl http://www.knora.org/data/0001/sequences
