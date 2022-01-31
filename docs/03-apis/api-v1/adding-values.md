<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Adding a Value

In order to add values to an existing resource, the HTTP method `POST`
has to be used. The request has to be sent to the Knora server using the
`values` path segment. Creating values requires authentication since
only known users may add values.

## Adding a Property Value

In order to add a value to a resource, its property type, value, and
project has to be indicated in the JSON. Also the IRI of the resource
the new value belongs has to be provided in the JSON.

```
HTTP POST to http://host/v1/values
```

  - Depending on the type of the new value, one of the following formats
    (all TypeScript interfaces defined in module `addValueFormats`) has
    to be used in order to create a new value:
    
      - `addRichtextValueRequest`
      - `addLinkValueRequest`
      - `addIntegerValueRequest`
      - `addDecimalValueRequest`
      - `addBooleanValueRequest`
      - `addUriValueRequest`
      - `addDateValueRequest` (see `dateString` in
        `basicMessageComponents` for the date format)
      - `addColorValueRequest`
      - `addGeometryValueRequest`
      - `addHierarchicalListValueRequest`
      - `addintervalValueRequest`
      - `addGeonameValueRequest`

## Response on Value Creation

When a value has been successfully created, Knora sends back a JSON with
the new value's IRI. The value IRI identifies the value and can be used
to perform future DSP-API V1 operations.

The JSON format of the response is described in the TypeScript interface
`addValueResponse` in module `addValueFormats`.
