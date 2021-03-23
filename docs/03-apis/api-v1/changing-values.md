<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP. If not, see <http://www.gnu.org/licenses/>.
-->

# Changing a Value

To add values to an existing resource, the HTTP method `PUT`
has to be used. Changing values requires authentication since only known
users may change values.

## Modifying a Property Value

The request has to be sent to the Knora server using the `values` path
segment followed by the value's IRI:

```
HTTP PUT to http://host/values/valueIRI
```

The value IRI has to be URL-encoded.

To change an existing value (creating a new version of it), the
value's current IRI and its new value have to be submitted as JSON in
the HTTP body.

Depending on the type of the new value, one of the following formats
has to be used in order to create a new value (all these TypeScript interfaces are defined in module `changeValueFormats`):
    
* `changeRichtextValueRequest`
* `changeLinkValueRequest`
* `changeIntegerValueRequest`
* `changeDecimalValueRequest`
* `changeBooleanValueRequest`
* `changeUriValueRequest`
* `changeDateValueRequest`
* `changeColorValueRequest`
* `changeGeometryValueRequest`
* `changeHierarchicalListValueRequest`
* `changeIntervalValueRequest`
* `changeGeonameValueRequest`

## Modifying a File Value

To change a file value, the client first uploads the new file to
Sipi, following the procedure described in
[Adding Resources with Image Files](adding-resources.md#adding-resources-with-image-files).

Then the client sends a request to Knora, using this following route:

```
HTTP PUT to http://host/filevalue/resourceIRI
```

Here, `resourceIRI` is the URL-encoded IRI of the resource whose file value is
to be changed. The body of the request is a JSON object described in the TypeScript
interface `changeFileValueRequest` in module `changeValueFormats`, and contains
`file`, whose value is the `internalFilename` that Sipi returned. The request header's
content type must be set to `application/json`.

## Response on Value Change

When a value has been successfully changed, Knora sends back a JSON with
the new value's IRI. The value IRI identifies the value and can be used
to perform future Knora API V1 operations.

The JSON format of the response is described in the TypeScript interface
`changeValueResponse` in module `changeValueFormats`.
