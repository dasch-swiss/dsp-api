#!/usr/bin/env bash

cd webapi/scripts

# Delete repository
curl -s -S -X DELETE "http://localhost:8080/openrdf-sesame/repositories/knora-test-unit"

# Create repository
curl  -s -S -X POST -H "Content-Type:text/turtle" --data-binary @./sesame-knora-test-unit-repository-config.ttl http://localhost:8080/openrdf-sesame/repositories/SYSTEM/rdf-graphs/service?graph=http://www.knora.org/config-test-unit

curl -s -S -X POST -H "Content-Type:text/turtle" -d "<http://www.knora.org/config-test-unit> a <http://www.openrdf.org/config/repository#RepositoryContext> ." http://localhost:8080/openrdf-sesame/repositories/SYSTEM/statements

# Configure / create lucene index
# curl -X POST -d "update=PREFIX luc: <http://www.ontotext.com/owlim/lucene#> INSERT DATA { luc:fullTextSearchIndex luc:createIndex \"true\" . }" http://localhost:8080/openrdf-sesame/repositories/knora-test-unit/statements
curl -s -S -X POST -H "Content-Type:text/turtle" --data-binary @./graphdb-knora-test-unit-index-config.ttl http://localhost:8080/openrdf-sesame/repositories/knora-test-unit/statements

#curl -X POST -H "Content-Type:text/turtle" --data-binary @./graphdb-knora-test-unit-index-config.ttl http://localhost:8080/openrdf-sesame/repositories/knora-test-unit/rdf-graphs/service?default

# Update lucene index
# curl -s -S -X POST -H "Content-Type:text/turtle" -d "<http://www.ontotext.com/owlim/lucene#fullTextSearchIndex> <http://www.ontotext.com/owlim/lucene#updateIndex> _:b1 ." http://localhost:8080/openrdf-sesame/repositories/knora-test-unit/statements
# curl -s -S -X POST -H "Content-Type:text/turtle" -d "<http://www.ontotext.com/owlim/lucene#fullTextSearchIndex> <http://www.ontotext.com/owlim/lucene#updateIndex> _:b1 ." http://localhost:8080/openrdf-sesame/repositories/knora-test-unit/rdf-graphs/service?default