#!/usr/bin/env bash

# Including fuseki-funcions.sh implementing delete, create, and upload.
source fuseki-functions.sh

# Name of the repository
REPOSITORY="knora-test"

# delete-repository // delete dos not work correctly. need to delete database manually.
create-repository
