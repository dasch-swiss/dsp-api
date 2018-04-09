#!/usr/bin/env python3

import requests
import json
import traceback

try:

    beolProjectIri = "http://rdfh.ch/projects/0801"
    base_url = "http://localhost/v2/"

    beolUser = "t.schweizer@unibas.ch"

    # create mapping

    mappingParams = {
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": beolProjectIri,
        "http://www.w3.org/2000/01/rdf-schema#label": "BEBB letter mapping",
        "http://api.knora.org/ontology/knora-api/v2#mappingHasName": "BEBBLetterMapping3"
    }

    mappingRequest = requests.post(base_url + "mapping",
                                   data={"json": json.dumps(mappingParams)},
                                   files={"xml": ("mappingForLetter.xml", open("mappingForLetter.xml"))},
                                   auth=(beolUser, "test"),
                                   proxies={'http': 'http://localhost:3333'})

    mappingRequest.raise_for_status()

    print(mappingRequest.text)

    mappingIri = beolProjectIri + "/mappings/BEBBLetterMapping"

except requests.exceptions.HTTPError as e:
   #print(str(e))
   print(mappingRequest.text)
   #traceback.print_exc()
   pass