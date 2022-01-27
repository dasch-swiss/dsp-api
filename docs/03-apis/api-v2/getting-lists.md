<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Getting Lists

## Getting a complete List

In order to request a complete list, make a HTTP GET request to the `lists` route appending the Iri of the list's root node (URL-encoded):

```
HTTP GET to http://host/v2/lists/listRootNodeIri
```

Lists are only returned in the complex schema. The response to a list request is a `List` (see interface `List` in module `ListResponse`). 


## Getting a single Node

In order to request a single node of a list, make a HTTP GET request to the `node` route appending the node's Iri (URL-encoded):

```
HTTP GET to http://host/v2/node/nodeIri
```

Nodes are only returned in the complex schema.  The response to a node request is a `ListNode` (see interface `List` in module `ListResponse`).
