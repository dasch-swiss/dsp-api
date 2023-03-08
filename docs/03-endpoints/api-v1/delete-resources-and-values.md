<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Deleting Resources and Values

Knora does not actually delete resources or values; it just marks them
as deleted. To mark a resource or value as deleted, you must use the
HTTP method `DELETE` has to be used. This requires authentication.

## Mark a Resource as Deleted

The delete request has to be sent to the Knora server using the
`resources` path
    segment.

```
HTTP DELETE to http://host/resources/resourceIRI?deleteComment=String
```

The resource IRI must be URL-encoded. The `deleteComment` is an optional
comment explaining why the resource is being marked as deleted.

## Mark a Value as Deleted

The delete request has to be sent to the Knora server using the `values`
path segment, providing the valueIRI:

```
HTTP DELETE to http://host/values/valueIRI?deleteComment=String
```

The value IRI must be URL-encoded. The `deleteComment` is an optional
comment explaining why the value is being marked as deleted.

Once a value has been marked as deleted, no new versions of it can be
made.
