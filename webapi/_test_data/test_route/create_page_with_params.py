#!/usr/bin/env python3

import requests, json, sys

#
# This scripts tests the creation of a page and submits image data params (no binaries) to the resources route after the binaries have been uploaded to Sipi and a preview thumbnail has been created,
#  testing the running Sipi server.
#

try:

    # do a make_thumbnail request here to Sipi to obtain a valid filename for later conversion request
    base_url = "http://localhost/v1/"

    filename = 'Chlaus.jpg'
    path = 'images/'
    mimetype = 'image/jpeg'

    files = {'file': (filename, open(path + filename, 'rb'), mimetype)}

    r = requests.post("http://localhost:1024/make_thumbnail", files = files)

    #print(r.text)

    thumb_response = r.json()

    print(thumb_response)

    # Get the thumbnail
    r = requests.get(thumb_response['preview_path'])
    r.raise_for_status()

    print("Got preview with status code " + str(r.status_code))

    conversion_params = {
        'restype_id': 'http://www.knora.org/ontology/incunabula#page',
        'properties': {
            'http://www.knora.org/ontology/incunabula#pagenum': [
                {'richtext_value': {'utf8str': 'test page'}}
            ],
            'http://www.knora.org/ontology/incunabula#origname': [
                {'richtext_value': {'utf8str': 'Chlaus'}}
            ],
            'http://www.knora.org/ontology/incunabula#partOf': [
                {'link_value': 'http://data.knora.org/5e77e98d2603'}
            ],
            'http://www.knora.org/ontology/incunabula#seqnum': [{'int_value': 99999999}]
        },
        'file': {
            'originalFilename' : thumb_response["original_filename"],
            'originalMimeType' : thumb_response["original_mimetype"],
            'filename' : thumb_response['filename']
        },
        'label': 'test page',
        'project_id': 'http://data.knora.org/projects/77275339'
    }


    r = requests.post(base_url + 'resources',
                      data=json.dumps(conversion_params),
                      headers={'content-type': 'application/json; charset=utf8'},
                      auth=('root@example.com', 'test'),
                      proxies={'http': 'http://localhost:3333'})

    r.raise_for_status()
    print(r.text)
except Exception as e:
    print('Knora API answered with an error:\n')
    print(e)
    #print(r.text)
