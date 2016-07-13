.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _adding-resources:

Adding Resources
================

.. _adding_resources_without_representation:

*************************************************
Adding Resources without a digital Representation
*************************************************

In order to create a resource, the HTTP method ``POST`` has to be used.
The request has to be sent to the Knora server using the ``resources`` path segment.

Unlike in the case of GET requests, the request body consists of JSON describing the resource to be created.
The format of the JSON used to create a resource without a digital representation (e.g. an image file) is described
in the TypeScript interface ``createResourceWithoutRepresentationRequest`` in module ``createResourceFormats``.
It requires the IRI of the resource class the new resource belongs to, a label describing the new resource,
the IRI of the project the new resource belongs to, and the properties to be assigned to the new resource.

The request header's content type has to set to ``application/json``.

Creating resources requires authentication since only known users may add resources.


**********************************************
Adding Resources with a digital Representation
**********************************************

Certain resource classes allow for digital representations (e.g. an image). There are two ways to attach a file to a resource:
Either by submitting directly the binaries of the file in a HTTP Multipart request or by indicating the location of the file.
The two cases are referred to as Non GUI-case and GUI-case (see :ref:`sipi_and_knora`).

-------------------------------------
Including the binaries (Non GUI-case)
-------------------------------------

In order to include the binaries, a HTTP Multipart request has to be sent. One part contains the JSON (same format as described for :ref:`adding_resources_without_representation`) and has to be named ``json``.
The other part contains the file's name, its binaries, and its mime type. The following example illustrates how to make this type of request using Python3:

::

    #!/usr/bin/env python3

    # a Python dictionary that will be turned into a JSON object
    resourceParams = {
       'restype_id': 'http://www.knora.org/ontology/test#testType',
       'properties': {
           'http://www.knora.org/ontology/test#testtext': [
               {'richtext_value': {'utf8str': "test", 'textattr': json.dumps({}), 'resource_reference': []}}
           ],
           'http://www.knora.org/ontology/test#testnumber': [
               {'int_value': 1}
           ]
       },
       'label': "test resource",
       'project_id': 'http://data.knora.org/projects/testproject'
    }

    # the name of the file to be submitted
    filename = "myimage.jpg"

    # a tuple containing the file's name, its binaries and its mimetype
    file = {'file': (filename, open(filename, 'rb'), "image/jpeg")}

    # do a POST request providing both the JSON and the binaries
    r = requests.post("http://www.knora.org/v1/resources",
                      data={'json': json.dumps(resourceParams)},
                      files=file,
                      auth=('user', 'password'))


Please note that the file has to be read in binary mode (by default it would be read in text mode).

--------------------------------------------
Indicating the location of a file (GUI-case)
--------------------------------------------

This request works similarly to :ref:`adding_resources_without_representation`. The JSON format is described in
the TypeScript interface ``createResourceWithRepresentationRequest`` in module ``createResourceFormats``.

In addition to :ref:`adding_resources_without_representation`, the (temporary) name of the file, its original name, and mime type have to be provided (see :ref:`gui_case`).