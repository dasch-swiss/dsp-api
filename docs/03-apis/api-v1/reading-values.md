<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Reading Values

In order to get an existing value, the HTTP method GET has to be used.
The request has to be sent to the Knora server using the `values` path
segment. Reading values may require authentication since some resources
may have restricted viewing permissions.

## Reading a Value

The representation of a value can be obtained by making a GET request
providing the value's IRI:

```
HTTP GET to http://host/v1/values/valueIRI
```

In the response, the value's type and value are returned (see TypeScript
interface `valueResponse` in module `valueResponseFormats`).

## Getting a Value's Version History

In order to get the history of a value (its current and previous
versions), the IRI of the resource it belongs to, the IRI of the
property type that connects the resource to the value, and its
**current** value IRI have to be submitted. Each of these elements is
appended to the URL and separated by a slash. Please note that all of
these have to be URL encoded.

Additionally to `values`, the path segment `history` has to be used:

```
HTTP GET to http://host/v1/values/history/resourceIRI/propertyTypeIRI/valueIRI
```

In the response, the value's versions returned (see TypeScript interface
`valueVersionsResponse` in module `valueResponseFormats`).

## Getting a Linking Value

In order to get information about a link between two resources, the path
segment `links` has to be used. The IRI of the source object, the IRI of
the property type linking the the two objects, and the IRI of the target
object have to be provided in the URL separated by slashes. Each of
these has to be URL
    encoded.

```
HTTP GET to http://host/links/sourceObjectIRI/linkingPropertyIRI/targetObjectIRI
```

In the response, information about the link is returned such as a
reference count indicating how many links of the specified direction
(source to target) and type (property) between the two objects exist
(see TypeScript interface `linkResponse` in module
`valueResponseFormats`).
