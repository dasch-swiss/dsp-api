#!/usr/bin/env python3

import requests, json, urllib

#
# This scripts tests the changing of the file value of a page and submits binary image data to the values route, testing the running Sipi server.
#

try:
    base_url = "http://localhost/v1/"


    filename = 'marbles.tif'
    path = 'images/'
    mimetype = 'image/tiff'

    files = {'file': (filename, open(path + filename, 'rb'), mimetype)}

    resIri = urllib.parse.quote_plus('http://data.knora.org/8a0b1e75')

    r = requests.put(base_url + 'filevalue/' + resIri,
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
