# context

These files are materials for test cases in `StandoffV1R2RSpec`.

The `mapping*` files describe mapping used to translate the `xml` encoded text into Knora's Standoff format.

# mappings

## Letter

Is the mapping used for lettter transcription

- mapping file: `mappingForLetter.xml`
- example text files: `letter.xml`, `letter2.xml`, `letter3.xml`

## StandardHTML

Is the mapping used to translate html markup using the default mapping `OntologyConstants.KnoraBase.StandardMapping`.

- mapping file: `mappingForStandardHTML.xml`
- example text file: `StandardHTML`

Note: that you can use this mapping to generate the default mapping that is distributed
as `knora-ontologies/standoff-data.ttl`.  
Please read the official documentation about Standoff Standard Mapping.

Step to update standard mapping:

1. Update `mappingForStandardHTML.xml`
2. . create the `update-standard-mapping.json`

```json
{
  "knora-api:mappingHasName": "update-standard-mapping",
  "knora-api:attachedToProject": {
    "@id": "http://rdfh.ch/projects/0001"
  },
  "rdfs:label": "update-standard-mapping",
  "@context": {
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

3. call the endpoint to generate the new mapping in the `anything` project:

```shell
$ curl -u root@example.com:test -X POST -F json=@update-standard-mapping.json -F xml=@mappingForStandardHTML.xml  http://localhost:3333/v2/mapping
```

4. generate the ttl:

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
Construct {
    ?mapping ?p ?o .
    ?mapping knora-base:hasMappingElement ?mEle .
    ?mEle ?pp ?oo .
    ?oo ?ppp ?ooo .
}
FROM <http://www.knora.org/data/0001/anything>
WHERE {
    BIND(<http://rdfh.ch/projects/0001/mappings/update-standard-mapping> as ?mapping)
    ?mapping ?p ?o .

    OPTIONAL {
        ?mapping knora-base:hasMappingElement ?mEle .
    	OPTIONAL {
        	?mEle ?pp ?oo .
    		OPTIONAL {
        		?oo ?ppp ?ooo .
            }
        }
    }
}
```

5. Find differences to update by hand (to avoid massive IRI update) the `knora-ontologies/standoff-data.ttl`, pay attention to namespaces (e.g. replace all `http://rdfh.ch/projects/0001/mappings/update-standard-mapping` by `http://rdfh.ch/standoff/mappings/StandardMapping`)!
6. Prepare an upgrade script of an existing Knora base

## HTML

Custom mapping of HTML for project specific tagging.

- mapping file: `mappingForHTML.xml`
- example file: `HTML.xml`

Note: this covers part of the StandardHTML mapping because knora does not yet allow multiple mappings encoding.
