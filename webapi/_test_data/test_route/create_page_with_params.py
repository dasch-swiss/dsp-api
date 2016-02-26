#!/usr/bin/env python3

import requests, json

try:
    base_url = "http://localhost/v1/"

    params = {
        'restype_id': 'http://www.knora.org/ontology/incunabula#page',
        'properties': {
            'http://www.knora.org/ontology/incunabula#pagenum': [
                {'richtext_value': {'utf8str': 'test page', 'textattr': json.dumps({}), 'resource_reference': []}}
            ],
            'http://www.knora.org/ontology/incunabula#origname': [
                {'richtext_value': {'utf8str': 'Chlaus', 'textattr': json.dumps({}), 'resource_reference': []}}
            ],
            'http://www.knora.org/ontology/incunabula#partOf': [
                {'link_value': 'http://data.knora.org/5e77e98d2603'}
            ],
            'http://www.knora.org/ontology/incunabula#seqnum': [{'int_value': 99999999}]
        },
        'file': {
            'originalFilename' : "Chlaus.jpg",
            'originalMimeType' : "image/jpeg",
            'filename' : "./test_server/images/Chlaus.jpg"
        },
        'label': 'test page',
        'project_id': 'http://data.knora.org/projects/77275339'
    }


    r = requests.post(base_url + 'resources',
                      data=json.dumps(params),
                      headers={'content-type': 'application/json; charset=utf8'},
                      auth=('root', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()
    print(r.text)
except Exception as e:
    print('Knora API answered with an error:\n')
    print(e)
    print(r.text)
