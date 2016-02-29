#!/usr/bin/env python3

import requests, json

#
# This scripts tests the creation of a page and submits binary image data to the resources route, testing the running Sipi server.
#

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
        'label': 'test page',
        'project_id': 'http://data.knora.org/projects/77275339'
    }

    filename = 'Chlaus.jpg'
    path = 'images/'
    mimetype = 'image/jpeg'

    files = {'file': (filename, open(path + filename, 'rb'), mimetype)}

    props = json.dumps(params)

    r = requests.post(base_url + 'resources',
                      data={'json': props},
                      files=files,
                      headers=None,
                      auth=('root', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()
    print(r.text)
except Exception as e:
    print('Knora API answered with an error:\n')
    print(e)
    print(r.text)
