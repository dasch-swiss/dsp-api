#!/usr/bin/env bash

curl -X DELETE -u test:xyzzy http://localhost:10035/repositories/knora-prod
curl -X PUT -u test:xyzzy http://localhost:10035/repositories/knora-prod

curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/knora-base.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/knora-base.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/knora-admin.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/standoff-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fstandoff%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/standoff-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fstandoff%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/knora-dc.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fdc%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../../knora-ontologies/salsah-gui.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fsalsah-gui%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/admin-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fadmin%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/permissions-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fpermissions%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/ontologies/incunabula-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/incunabula-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fincunabula%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/ontologies/images-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/demo_data/images-demo-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fimages%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/ontologies/anything-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fanything%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/anything-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fanything%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/ontologies/beol-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fbeol%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/beol-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fbeol%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/ontologies/biblio-onto.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fontology%2Fbiblio%3E
curl -H "Content-Type: text/turtle" -u test:xyzzy --data-binary '@../_test_data/all_data/biblio-data.ttl' http://localhost:10035/repositories/knora-prod/statements?context=%3Chttp%3A%2F%2Fwww.knora.org%2Fdata%2Fbiblio%3E

# indexing the literals of the following properties:
# <http://www.w3.org/2000/01/rdf-schema#label>
# <http://www.knora.org/ontology/knora-base#valueHasString>
# <http://www.knora.org/ontology/knora-base#valueHasComment>
INDEX='{"predicates":["<http://www.w3.org/2000/01/rdf-schema#label>","<http://www.knora.org/ontology/knora-base#valueHasString>","<http://www.knora.org/ontology/knora-base#valueHasComment>"],"indexLiterals":true,"indexResources":false,"indexFields":["object"],"minimumWordSize":3,"stopWords":["and","are","but","for","into","not","such","that","the","their","then","there","these","they","this","was","will","with"],"wordFilters":[],"innerChars":[],"borderChars":[],"tokenizer":"default"}'

curl -X PUT -u test:xyzzy -d $INDEX http://localhost:10035/repositories/knora-prod/freetext/indices/alles