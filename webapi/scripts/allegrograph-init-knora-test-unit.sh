#!/usr/bin/env bash

curl -X DELETE -u test:xyzzy http://localhost:10035/repositories/knora-test-unit
curl -X PUT -u test:xyzzy http://localhost:10035/repositories/knora-test-unit


# indexing the literals of the following properties:
# <http://www.w3.org/2000/01/rdf-schema#label>
# <http://www.knora.org/ontology/knora-base#valueHasString>
# <http://www.knora.org/ontology/knora-base#valueHasComment>
INDEX='{"predicates":["<http://www.w3.org/2000/01/rdf-schema#label>","<http://www.knora.org/ontology/knora-base#valueHasString>","<http://www.knora.org/ontology/knora-base#valueHasComment>"],"indexLiterals":true,"indexResources":false,"indexFields":["object"],"minimumWordSize":3,"stopWords":["and","are","but","for","into","not","such","that","the","their","then","there","these","they","this","was","will","with"],"wordFilters":[],"innerChars":[],"borderChars":[],"tokenizer":"default"}'

curl -X PUT -u test:xyzzy -d $INDEX http://localhost:10035/repositories/knora-test-unit/freetext/indices/alles