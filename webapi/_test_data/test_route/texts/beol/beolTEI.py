#!/usr/bin/env python3

import requests, json, traceback, urllib
import xml.etree.ElementTree as ET


try:

    # create XSL transformation for body (standoff)

    resourceParams = {
        'restype_id': 'http://www.knora.org/ontology/knora-base#XSLTransformation',
        'label': "XSLT",
        'properties': {},
        'project_id': 'http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF'
    }

    # the name of the file to be submitted
    filename = "../../../../src/main/resources/beol/BEOLstandoffToTEI.xsl"

    # a tuple containing the file's name, its binaries and its mimetype
    file = {'file': (filename, open(filename, 'r'), "text/xml; charset=utf-8")} # use name "file"

    # do a POST request providing both the JSON and the binaries
    r = requests.post("http://localhost/v1/resources",
                      data={'json': json.dumps(resourceParams)}, # use name "json"
                      files=file,
                      auth=('t.schweizer@unibas.ch', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()

    result = r.json()

    XSLT_id = result['res_id']

    # update res_id in mapping

    tree = ET.parse("../../../../src/main/resources/beol/BEOLTEIMapping.xml")
    root = tree.getroot()

    defaultXSLT = root.findall("./defaultXSLTransformation")

    if len(defaultXSLT) != 1:
        raise Exception("could not get element for default XSLT in mapping")


    defaultXSLT[0].text = XSLT_id

    # update id of default XSLT
    tree.write("../../../../src/main/resources/beol/BEOLTEIMapping.xml")

    # create mapping referring to the XSL transformation

    mappingParams = {
        "http://api.knora.org/ontology/knora-api/v2#mappingHasName": "BEOLTEIMapping",
        "http://api.knora.org/ontology/knora-api/v2#attachedToProject": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF",
        "http://www.w3.org/2000/01/rdf-schema#label": "TEI mapping"
    }

    mappingRequest = requests.post("http://localhost:3333/v2/mapping",
                                   data={"json": json.dumps(mappingParams)},
                                   files={"xml": ("BEOLTEImapping.xml", open("../../../../src/main/resources/beol/BEOLTEImapping.xml"))},
                                   auth=("t.schweizer@unibas.ch", "test"),
                                   proxies={'http': 'http://localhost:3333'})

    #print(mappingRequest.text)

    mappingRequest.raise_for_status()

    # Gravsearch template

    resourceParams = {
        'restype_id': 'http://www.knora.org/ontology/knora-base#TextRepresentation',
        'label': "gravsearch",
        'properties': {},
        'project_id': 'http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF'
    }

    # the name of the file to be submitted
    filename = "../../../../src/main/resources/beol/gravsearch.txt"

    # a tuple containing the file's name, its binaries and its mimetype
    file = {'file': (filename, open(filename, 'r'), "text/plain; charset=utf-8")} # use name "file"

    # do a POST request providing both the JSON and the binaries
    g = requests.post("http://localhost/v1/resources",
                      data={'json': json.dumps(resourceParams)}, # use name "json"
                      files=file,
                      auth=('t.schweizer@unibas.ch', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    g.raise_for_status()

    resourceParams = {
        'restype_id': 'http://www.knora.org/ontology/knora-base#XSLTransformation',
        'label': "XSLT",
        'properties': {},
        'project_id': 'http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF'
    }

    # the name of the file to be submitted
    filename = "../../../../src/main/resources/beol/BEOLHeaderXSLT.xsl"

    # a tuple containing the file's name, its binaries and its mimetype
    file = {'file': (filename, open(filename, 'r'), "text/xml; charset=utf-8")} # use name "file"

    # do a POST request providing both the JSON and the binaries
    h = requests.post("http://localhost/v1/resources",
                      data={'json': json.dumps(resourceParams)}, # use name "json"
                      files=file,
                      auth=('t.schweizer@unibas.ch', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    h.raise_for_status()

    print("&mappingIri=" + urllib.parse.quote_plus("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF/mappings/BEOLTEIMapping") + "&gravsearchTemplateIri=" + urllib.parse.quote_plus(g.json()['res_id']) + "&teiHeaderXSLTIri=" + urllib.parse.quote_plus(h.json()['res_id']))




except:

    traceback.print_exc()