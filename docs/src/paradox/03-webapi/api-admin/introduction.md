<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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
-->

# Introduction: Using the Admin API

@@toc { depth=2 }

## RESTful API

The Knora Admin API is a RESTful API that allows for reading and adding of
administrative resources from and to Knora and changing their values
using HTTP requests. The actual data is submitted as JSON (request and
response format). The various HTTP methods are applied according to the
widespread practice of RESTful APIs: GET for reading, POST for adding,
PUT for changing resources and values, and DELETE to delete resources or
values (see
[Using HTTP Methods for RESTful Services](http://www.restapitutorial.com/lessons/httpmethods.html)).

## Knora IRIs in the Admin API

Every resource that is created or hosted by Knora is identified by a
unique ID called an Internationalized Resource Identifier (@extref[IRI](rfc:3987)). The IRI is required for every API operation to identify the resource in question. A Knora IRI has itself the format of a URL.
For some API operations, the IRI has to be URL-encoded (HTTP GET requests).

TODO: Clarify that the admin API uses internal IRIs (depends on @github[#841](#841)).

## Admin Path Segment

Every request to Admin API includes `admin` as a path segment, e.g.
`http://host/admin/users/http%3A%2F%2Frdfh.ch%2Fusers%2Froot`.

## Admin API Response Format

If an API request is handled successfully, Knora responds
with a 200 HTTP status code. The actual answer from Knora (the
representation of the requested resource or information about the
executed API operation) is sent in the HTTP body, encoded as JSON.

## Placeholder `host` in sample URLs

Please note that all the sample URLs used in this documentation contain
`host` as a placeholder. The placeholder `host` has to be replaced by
the actual hostname (and port) of the server the Knora instance is
running on.

## Authentication

For all API operations that target at changing resources or values, the
client has to provide credentials (username and password) so that the
API server can authenticate the user making the request. Credentials can
be sent as a part of the HTTP header or as parts of the URL (TODO: add
a link to "Authentication in the Knora API Server").

## OpenAPI/Swagger

The Admin API uses
[OpenAPI](https://github.com/OAI/OpenAPI-Specification) for
documentation purposes. To try it out, run webapi and open
http://host/api-docs/swagger.json in <http://petstore.swagger.io> .
Alternatively, the documentation can be looked at by using
[ReDoc](https://github.com/Rebilly/ReDoc), which is provided in
`knora/docs/redoc/index.html`.

## Admin API Endpoints

TODO
