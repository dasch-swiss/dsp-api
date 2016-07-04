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

.. _reading-and-searching-resources:

Reading and Searching Resources
===============================

***********************************************
Get the Representation of a Resource by its IRI
***********************************************

Simple Request
--------------

A resource can be obtained by making a GET request to the API providing its IRI. Because a Knora IRI has the format of a URL, its IRI has to be URL encoded.

Get the resource with the IRI ``http://data.knora.org/c5058f3a`` (an incunabula book contained in the test data). In order to do so, make a HTTP GET request to the resources route
(path segment ``resources`` in the API call) and append the URL encoded IRI:

::

    curl http://www.knora.org/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a

As an answer, the client receives a JSON that represents the requested resource. It has the following members:
 - ``status``: The Knora status code, ``0`` if everything went well
 - ``userdata``: Data about the user that made the request
 - ``resinfo``: Data describing the requested resource and its class
 - ``resdata``: Short information about the resource and its class (including information about the given user's permissions on the resource)
 - ``incoming``: Resources pointing to the requested resource
 - ``props``: Properties of the requested resource.

Provide Request Parameters
--------------------------

To make a request more specific, the following parameters can be appended to the URL (``http://www.knora.org/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?param1=value1``):
 - ``reqtype=info|context|rights``: Specifies the type of request. Setting the parameter value ``info`` returns short information about the requested resource (contains only ``resinfo``). The parameter value ``context`` returns context information (``resource_context``) about the requested resource: Either the dependent parts of a compound resource (e.g. pages of a book) or the parent resource of a dependent resource (e.g. the book a pages belongs to). By default, a context query does not return information about the requested resource itself, but only about its context. The parameter ``rights`` returns the given user's permissions on the requested resource.
 - ``resinfo=true``: Can be used in combination with ``reqtype=context``: If set, ``resinfo`` is added to the response representing information about
   the requested resource (complementary to its context).

Obtain an HTML Representation of a Resource
-------------------------------------------

In order to get a HTML representation of a resource (not a JSON), the path segment ``resources.html`` can be used:

::

    curl http://www.knora.org/resources.html/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?reqtype=properties

The request returns the properties of the requested resource as an HTML document.     
