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
TODO: implement values route


Non GUI-Case
------------
In this case, the API receives an HTTP multipart request containing the binary data.

Create a new Resource with a Digital Representation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The request is handled in ``ResourcesRouteV1.scala``. The multipart POST request consists of two named body parts: ``json`` containing the resource parameters (properties)
and ``file`` containing the binary data as well as the file name and its mime type.
Using Python's `request module <http://docs.python-requests.org/en/master/user/quickstart/#post-a-multipart-encoded-file>`_,
a request could look like this:

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
TODO: implement values route

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


Further Handling of the GUI and the non GUI-case by the Values Responder
---------------------------------------------------------------------------
TODO: implement SIPI responder call from values responder






.. [#] Of course, also the GUI uses the API. But the user does not need to know about it.


