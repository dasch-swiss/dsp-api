#!/bin/bash
curl -v -X POST --header "Content-Type: multipart/form-data" -u anything-user:test --form "json=@mappingCreationParams.json" --form "xml=@mapping.xml" localhost:3333/v1/mapping
