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

.. _sipi_and_knora:

***********************************
Interaction Between Sipi and Knora
***********************************

General Remarks
===============
Knora and Sipi (Simple Image Presentation Interface) are two **complementary** software projects.
Whereas Knora deals with data that is written to and read from a triplestore (metadata and annotations), Sipi takes care of storing,
converting and serving image files as well as other types of files such as audio, video, or documents (binary files it just stores and serves).

Knora and Sipi stick to a clear division of responsibility regarding files:
Knora knows about the names of files that are attached to resources as well as some metadata and is capable of creating the URLs for the client to request them from Sipi, but the whole handling of files
(storing, naming, organization of the internal directory structure, format conversions, and serving) is taken care of by Sipi.

Adding Files to Knora: Using the GUI or directly the API
========================================================
To create a resource with a digital representation attached to, either the browser-based GUI (SALSAH) can be used
or this can be done by *directly* [#]_ addressing the API. The same applies for changing an existing digital representation for a resource. Subsequently, the first case will be called the *GUI-case* and the second the *non GUI-case*.

.. _gui_case:

GUI-Case
--------
In this case, the user may choose a file to upload using his web-browser. The file is directly sent to Sipi (route: ``create_thumbnail``) to calculate a thumbnail hosted by Sipi
which then gets displayed to the user in the browser. Sipi copies the original file into a temporary directory and keeps it there (for later processing in another request). In its answer (JSON), Sipi returns:

 - ``preview_path``: the path to the thumbnail (accessible to a web-browser)
 - ``filename``: the name of the temporarily stored original file (managed by Sipi)
 - ``original_mimetype``: mime type of the original file
 - ``original_filename``: the original name of the file submitted by the client

Once the user finally wants to attach the file to a resource, the request is sent to Knora's API
providing all the required parameters to create the resource along with additional information about the file to be attached.
**However, the file itself is not submitted to the Knora Api,
but its filename returned by Sipi.**

Create a new Resource with a Digital Representation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The POST request is handled in ``ResourcesRouteV1.scala`` and parsed to a ``CreateResourceApiRequestV1``. Information about the file is sent separately
from the other resource parameters (properties) under the name ``file``:

 - ``originalFilename``: original name of the file (returned by Sipi when creating the thumbnail)
 - ``originalMimeType``: original mime type of the file (returned by Sipi when creating the thumbnail)
 - ``filename``: name of the temporarily stored original file (returned by Sipi when creating the thumbnail)

In the route, a ``SipiResponderConversionFileRequestV1`` is created representing the information about the file to be attached to the new resource. Along with the other parameters,
it is sent to the resources responder.

See :ref:`resources-responder-and-sipi` for details of how the resources responder then handles the request.

Change the Digital Representation of a Resource
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The request is taken care of in ``ValuesRouteV1.scala``. The PUT request is handled in path ``v1/filevalue/{resIri}`` which receives the resource Iri as a part of the URL:
*The submitted file will update the existing file values of the given resource.*

The file parameters are submitted as json and are parsed into a ``ChangeFileValueApiRequestV1``. To represent the conversion request for the Sipi responder,
a ``SipiResponderConversionFileRequestV1`` is created. A ``ChangeFileValueRequestV1`` containing the resource Iri and the message for Sipi is then created and sent to the values responder.

See :ref:`values-responder-and-sipi` for details of how the values responder then handles the request.


Non GUI-Case
------------
In this case, the API receives an HTTP multipart request containing the binary data.

Create a new Resource with a Digital Representation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The request is handled in ``ResourcesRouteV1.scala``. The multipart POST request consists of two named body parts: ``json`` containing the resource parameters (properties)
and ``file`` containing the binary data as well as the file name and its mime type.
Using Python's `request module <http://docs.python-requests.org/en/master/user/quickstart/#post-a-multipart-encoded-file>`_,
a request could look like this:

.. _python_code:

 .. code::

    import requests, json

    params = {...} // resource parameters
    files = {'file': (filename, open(path + filename, 'rb'), mimetype)} // filename, binary data, and mime type

    r = requests.post(knora_url + '/resources',
                      data={'json': json.dumps(params)},
                      files=files,
                      headers=None)

The binary data is saved to a temporary location by Knora. The route then creates a ``SipiResponderConversionPathRequestV1``
representing the information about the file (i.e. the temporary path to the file) to be attached to the new resource. Along with the other parameters,
it is sent to the resources responder.

See :ref:`resources-responder-and-sipi` for details of how the resources responder then handles the request.

Change the Digital Representation of a Resource
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The request is taken care of in ``ValuesRouteV1.scala``. The multipart PUT request is handled in path ``v1/filevalue/{resIri}`` which receives the resource Iri as a part of the URL:
*The submitted file will update the existing file values of the given resource.*

For the request, no json parameters are required. So its body just consists of the binary data (cf. :ref:`Python code example <python_code>`).
The values route stores the submitted binaries as a temporary file and creates a ``SipiResponderConversionPathRequestV1``.
A ``ChangeFileValueRequestV1`` containing the resource Iri and the message for Sipi is then created and sent to the values responder.

See :ref:`values-responder-and-sipi` for details of how the values responder then handles the request.


.. _resources-responder-and-sipi:

Further Handling of the GUI and the non GUI-case in the Resources Responder
---------------------------------------------------------------------------
Once a ``SipiResponderConversionFileRequestV1`` (GUI-case) or a ``SipiResponderConversionPathRequestV1`` (non GUI-case) has been created and passed to the resources responder,
the GUI and the non GUI-case can be handled in a very similar way. This is why they are both implementations of the trait ``SipiResponderConversionRequestV1``.

The resource responder calls the ontology responder to check if all required properties were submitted for the given resource type. Also it is checked
if the given resource type may have a digital representation. The resources responder then sends a message to Sipi responder that does a request to the Sipi server. Depending on the type of the message (``SipiResponderConversionFileRequestV1`` or ``SipiResponderConversionPathRequestV1``), a different Sipi route is called.
In the first case (GUI-case), the file is already managed by Sipi and only the filename has to be indicated. In the latter case, Sipi is told about the location where Knora has saved the binary data to.

To make this handling easy for Knora, both messages have their own implementation for creating the parameters for Sipi (declared in the trait as ``toFormData``). If Knora deals with a ``SipiResponderConversionPathRequestV1``,
it has to delete the temporary file after it has been processed by SIPI. Here, we assume that we deal with an image.

For both cases, Sipi returns the same answer containing the following information:

 - ``file_type``: the type of the file that has been handled by Sipi (image | video | audio | text | binary)
 - ``mimetype_full`` and ``mimetype_thumb``: mime types of the full image representation and the thumbnail
 - ``original_mimetype``: the mime type of the original file
 - ``original_filename``: the name of the original file
 - ``nx_full``, ``ny_full``, ``nx_thumb``, and ``ny_thumb``: the x and y dimensions of both the full image and the thumbnail
 - ``filename_full`` and ``filename_full``: the names of the full image and the thumbnail (needed to request the images from Sipi)

The ``file_type`` is important because representations for resources are restricted to media types: image, audio, video or a generic binary file. If a resource type requires an image representations
(subclass of ``StillImageRepresentation``), the ``file_type`` has to be an image.
Otherwise, the ontology's restrictions would be violated. Because of this requirement, there is a construct ``fileType2FileValueProperty`` mapping file types to file value properties.
Also all the possible file types are defined in enumeration.

Depending on the given file type, Sipi responder can create the apt message (here: ``StillImageFileValueV1``) to save the data to the triplestore.

.. _values-responder-and-sipi:

Further Handling of the GUI and the non GUI-case by the Values Responder
---------------------------------------------------------------------------
In the values responder, ``ChangeFileValueRequestV1`` is passed to the method ``changeFileValueV1``. Unlike ordinary value change requests,
the Iris of the value objects to be updated are not known yet. Because of this, all the existing file values of the given resource Iri have to be queried first.
Also their quality levels are queried because in case of a ``StillImageFileValue``, we have to deal with a file value for the thumbnail and another one for the full quality representation.
When these two file values are being updated, the quality levels have to be considered for the sake of consistency (otherwise a full quality value's ``knora-base:previous-value`` may point to a thumbnail file value).

With the file values being returned, we actually know about the current Iris of the value objects. Now the Sipi responder is called to handle the file conversion request (cf. :ref:`resources-responder-and-sipi`).
After that, it is checked that the ``file_type`` returned by Sipi responder corresponds to the property type of the existing file values. For example, if the ``file_type`` is an image, the property pointing to the current file values
must be a ``hasStillImageFileValue``. Otherwise, the user submitted a non image file that has to be rejected.

Depending on the ``file_type``, messages of type ``ChangeValueRequestV1`` can be created.
For each existing file value, such a message is instantiated containing the current value Iri and the new value to be created (returned by the sipi responder).
These messages are passed to ``changeValueV1`` because with the described handling done in ``changeFileValueV1``, the file values can be changed like any other value type.

In case of success, a ``ChangeFileValueResponseV1`` is sent back to the client, containing a list of the single ``ChangeValueResponseV1``.

.. [#] Of course, also the GUI uses the API. But the user does not need to know about it.

Retrieving Files from Sipi
==========================

URL creation
------------

Binary representions of Knora locations are served by Sipi. For each file value, Knora creates several locations representing different quality levels:

.. code::

   "resinfo": {
      "locations": [
         {
            "duration": ​0,
            "nx": ​95,
            "path": "http://sipiserver:port/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
            "ny": ​128,
            "fps": ​0,
            "format_name": "JPEG",
            "origname": "ad+s167_druck1=0001.tif",
            "protocol": "file"
         },
         {
            "duration": ​0,
             "nx": ​82,
             "path": "http://sipiserver:port/knora/incunabula_0000000002.jp2/full/82,110/0/default.jpg",
             "ny": ​110,
             "fps": ​0,
             "format_name": "JPEG2000",
             "origname": "ad+s167_druck1=0001.tif",
             "protocol": "file"
         },
         {
             "duration": ​0,
             "nx": ​163,
             "path": "http://sipiserver:port/knora/incunabula_0000000002.jp2/full/163,219/0/default.jpg",
             "ny": ​219,
             "fps": ​0,
             "format_name": "JPEG2000",
             "origname": "ad+s167_druck1=0001.tif",
             "protocol": "file"
         }
         ...
      ],
   "restype_label": "Seite",
   "resclass_has_location": true,

Each of these paths has to be handled by the browser by making a call to Sipi, obtaining the binary representation in the desired quality.
To deal with different image quality levels, Sipi implements the `IIIF standard <http://iiif.io/api/image/2.0/>`_. The different quality level paths
are created by Knora in ``ValueUtilV1``.

Whenever Sipi serves a binary representation of a Knora file value (indicated by using the prefix ``knora`` in the path), it has to make a request to Knora's
Sipi responder to get the user's permissions on the requested file. Sipi's request to Knora contains a cookie with the Knora session id the user has obtained when logging in to Knora:
As a response to a successful login, Knora returns the user's session id and this id is automatically sent to Sipi by the browser, setting a second cookie for the communication with Sipi.
The reason the Knora session id is set in two cookies, is the fact that cookies can not be shared among different domains. Since Knora and Sipi are likely to be running
under different domains, this solution offers the necessary flexibility.

.. _sharing_sessionid_with_sipi:

Sharing the Session ID with Sipi
--------------------------------

Whenever a file is requested, Sipi asks Knora about the currents user's permissions on the given file. This is achieved by sharing the Knora session id with Sipi.
When the user logs in to Knora using his browser, a request is sent to Sipi submitting the session id the user got back from Knora, setting a second session cookie.
Now the user has two session cookies containing the same session id: one for the communication with Knora and one for the communication with Sipi. However, Sipi does not handle sessions.
It just sends the given Knora session id to Knora.
