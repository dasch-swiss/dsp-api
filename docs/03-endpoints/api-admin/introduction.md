<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Introduction: Using the Admin API

The DSP Admin API makes it possible to administrate projects, users, user groups, permissions, and hierarchical lists.

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
unique ID called an Internationalized Resource Identifier ([IRI](https://tools.ietf.org/html/rfc3987)). The IRI is required for every API operation to identify the resource in question. A Knora IRI has itself the format of a URL.
For some API operations, the IRI has to be URL-encoded (HTTP GET requests).

Unlike the DSP-API v2, the admin API uses internal IRIs, i.e. the actual IRIs
that are stored in the triplestore (see [Knora IRIs](../api-v2/knora-iris.md)).

## Admin Path Segment

Every request to Admin API includes `admin` as a path segment, e.g.
`http://host/admin/users/iri/http%3A%2F%2Frdfh.ch%2Fusers%2Froot`.

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
be sent as a part of the HTTP header or as parts of the URL (see
[Authentication in Knora](../../05-internals/design/principles/authentication.md)).

## Admin API Endpoints

TODO
