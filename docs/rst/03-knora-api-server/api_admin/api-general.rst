.. Copyright © 2015-2018 the contributors (see Contributors.md).

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


Introduction: Using Admin API
=============================

RESTful API
-----------

Knora Admin API is a RESTful API that allows for reading and adding of administrative resources from and to Knora and
changing their values using HTTP requests. The actual data is submitted as JSON (request and response format). The
diverse HTTP methods are applied according to the widespread practice of RESTful APIs: GET for reading, POST for adding,
PUT for changing resources and values, and DELETE to delete resources or values (see RESTful_API_).

.. _RESTful_API: http://www.restapitutorial.com/lessons/httpmethods.html

Knora IRIs
----------

Every resource that is created or hosted by Knora is identified by a unique id, a so called Internationalized Resource
Identifier (IRI). The IRI is required for every API operation to identify the resource in question. A Knora IRI has
itself the format of a URL. For some API operations, the IRI has to be URL-encoded (HTTP GET requests).

Admin Path Segment
------------------

Every request to Admin API includes ``admin`` as a path segment, e.g. ``http://host/admin/users/http%3A%2F%2Frdfh.ch%2Fusers%2Froot``.

Knora API Response Format
-------------------------

In case an API request could be handled successfully, Knora responds with a 200 HTTP status code. The actual answer
from Knora (the representation of the requested resource or information about the executed API operation) is sent in the
HTTP body, encoded as JSON (using UTF-8).

Placeholder ``host`` in sample URLs
-----------------------------------

Please note that all the sample URLs used in this documentation contain ``host`` as a placeholder. The placeholder
``host`` has to be replaced by the actual hostname (and port) of the server the Knora instance is running on.

Authentication
--------------

For all API operations that target at changing resources or values, the client has to provide credentials (username and
password) so that the API server can authenticate the user making the request. Credentials can be sent as a part of the
HTTP header or as parts of the URL (see :ref:`authentication`).

OpenAPI / Swagger
-----------------

The Admin API uses OpenAPI_ for documentation purposes. To try it out, run `webapi` and open
`http://host/api-docs/swagger.json` in http://petstore.swagger.io .

.. _OpenAPI: http://www.restapitutorial.com/lessons/httpmethods.html

Different Admin API Endpoints
-----------------------------

- TBD -