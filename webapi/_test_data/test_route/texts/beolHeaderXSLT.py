#!/usr/bin/env python3

import requests, json, traceback


try:

    resourceParams = {
        'restype_id': 'http://www.knora.org/ontology/knora-base#XSLTransformation',
        'label': "XSLT",
        'properties': {},
        'project_id': 'http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF'
    }

    # the name of the file to be submitted
    filename = "../../../src/main/resources/BEOLHeaderXSLT.xsl"

    # a tuple containing the file's name, its binaries and its mimetype
    file = {'file': (filename, open(filename, 'r'), "text/xml; charset=utf-8")} # use name "file"

    # do a POST request providing both the JSON and the binaries
    r = requests.post("http://localhost/v1/resources",
                      data={'json': json.dumps(resourceParams)}, # use name "json"
                      files=file,
                      auth=('t.schweizer@unibas.ch', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()

    print(r.json())

except:

    print(r.text)
    traceback.print_exc()