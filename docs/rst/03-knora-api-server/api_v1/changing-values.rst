.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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

.. _changing-values:

Changing a Value
================

.. contents:: :local:

In order to add values to an existing resource, the HTTP method ``PUT`` has to be used.
Changing values requires authentication since only known users may change values.

**************************
Modifying a Property Value
**************************

The request has to be sent to the Knora server using the ``values`` path segment followed by the value's IRI:

::

     HTTP PUT to http://host/values/valueIRI

Please note that the value IRI has to be URL encoded.

In order to change an existing value (creating a new version of it), the value's current IRI and its new value have to be submitted as JSON in the HTTP body.

Depending on the type of the new value, one of the following formats (all TypeScript interfaces defined in module ``changeValueFormats``) has to be used in order to create a new value:
  - ``changeRichtextValueRequest``
  - ``changeLinkValueRequest``
  - ``changeIntegerValueRequest``
  - ``changeDecimalValueRequest``
  - ``changeBooleanValueRequest``
  - ``changeUriValueRequest``
  - ``changeDateValueRequest``
  - ``changeColorValueRequest``
  - ``changeGeometryValueRequest``
  - ``changeHierarchicalListValueRequest``
  - ``changeIntervalValueRequest``
  - ``changeGeonameValueRequest``


**********************
Modifying a File Value
**********************

In order to exchange a file value (digital representation of a resource), the path segment ``filevalue`` has to be used.
The IRI of the resource whose file value is to be exchanged has to be appended:

::

     HTTP PUT to http://host/filevalue/resourceIRI

Please note that the resource IRI has to be URL encoded.

There are two ways to change a file of a resource:
Either by submitting directly the binaries of the file in a HTTP Multipart request or by indicating the location of the file.
The two cases are referred to as Non GUI-case and GUI-case (see :ref:`sipi_and_knora`).

-------------------------------------
Including the binaries (Non GUI-case)
-------------------------------------

Here, a HTTP MULTIPART request has to be made simply providing the binaries (without JSON):

::

    #!/usr/bin/env python3

    import requests, json, urllib

    # the name of the file to be submitted
    filename = 'myimage.tif'

    # a tuple containing the file's name, its binaries and its mimetype
    files = {'file': (filename, open(filename, 'rb'), "image/tiff")}

    resIri = urllib.parse.quote_plus('http://data.knora.org/xy')

    r = requests.put("http://host/filevalue/" + resIri,
                     files=files)


Please note that the file has to be read in binary mode (by default it would be read in text mode).


--------------------------------------------
Indicating the location of a file (GUI-case)
--------------------------------------------

Here, simply the location of the new file has to be submitted as JSON.
The JSON format is described in the TypeScript interface ``changeFileValueRequest`` in module ``changeValueFormats``.
The request header's content type has to set to ``application/json``.

************************
Response on Value Change
************************

When a value has been successfully changed, Knora sends back a JSON with the new value's IRI.
The value IRI identifies the value and can be used to perform future Knora API V1 operations.

The JSON format of the response is described in the TypeScript interface ``changeValueResponse`` in module ``changeValueFormats``.
