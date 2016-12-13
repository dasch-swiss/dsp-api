#!/bin/bash
curl -v -X POST --header "application/xml" -u anything-user:test -d @mapping.xml localhost:3333/v1/mapping/http%3A%2F%2Fdata.knora.org%2Fprojects%2Fanything
