#!/usr/bin/env python3

import requests, json, urllib

#
# This scripts tests the changing of the file value of a page and submits image data params (no binaries) to the values route, testing the running Sipi server.
#

try:
    base_url = "http://localhost/v1/"

    params = {
        'file': {
            'originalFilename' : "Chlaus.jpg",
            'originalMimeType' : "image/jpeg",
            'filename' : "./test_server/images/Chlaus.jpg"
        }
    }

    resIri = urllib.parse.quote_plus('http://data.knora.org/8a0b1e75')

    r = requests.put(base_url + 'filevalue/' + resIri,
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
