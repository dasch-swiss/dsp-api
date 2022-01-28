<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Lists Endpoint

## Endpoint Overview

**List Item Operations:**

- `GET: /admin/lists[?projectIri=<projectIri>]` : return all lists optionally filtered by project
- `GET: /admin/lists/<listItemIri>` : return complete list with all children if IRI of the list (i.e. root node) is given
If IRI of the child node is given, return the node with its immediate children
- `GET: /admin/lists/infos/<listIri>` : return list information (without children)
- `GET: /admin/lists/nodes/<nodeIri>` : return list node information (without children)
- `GET: /admin/lists/<listIri>/info` : return list basic information (without children)

- `POST: /admin/lists` : create new list
- `POST: /admin/lists/<parentNodeIri>` : create new child node under the supplied parent node IRI

- `PUT: /admin/lists/<listItemIri>` : update node information (root or child)
- `PUT: /admin/lists/<listItemIri>/name` : update the name of the node (root or child)
- `PUT: /admin/lists/<listItemIri>/labels` : update labels of the node (root or child)
- `PUT: /admin/lists/<listItemIri>/comments` : update comments of the node (root or child)
- `PUT: /admin/lists/<nodeIri>/position` : update position of a child node within its current parent or by changing its 
parent node

- `DELETE: /admin/lists/<listItemIri>` : delete a list (i.e. root node) or a child node and all its children, if not used

## List Item Operations

### Get lists

 - Required permission: none
 - Return all lists optionally filtered by project
 - GET: `/admin/lists[?projectIri=<projectIri>]`

### Get list

 - Required permission: none
 - Return complete `list` (or `node`) including basic information of the list (or child node), `listinfo` (or `nodeinfo`),
and all its children
 - GET: `/admin/lists/<listIri>`

### Get list's information

- Required permission: none
- Return list information, `listinfo` (without children).
- GET: `/admin/lists/infos/<listIri>`

### Get list node Information

- Required permission: none
- Return node information, `nodeinfo`, (without children).
- GET: `/admin/lists/nodes/<nodeIri>`

### Get list's information (merged)

- Required permission: none
- Return list (or node) basic information, `listinfo` (or `nodeinfo`), without its children
- GET: `/admin/lists/<listIri>/info`

### Create new list

  - Required permission: SystemAdmin / ProjectAdmin
  - Required fields: `projectIri`, `labels`, `comments`
  - POST: `/admin/lists`
  - BODY:
  
```json
    {
        "projectIri": "someprojectiri",
        "labels": [{ "value": "New list", "language": "en"}],
        "comments": []
    } 
```

Additionally, each list can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```json
  {
    "id": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
    "projectIri": "http://rdfh.ch/projects/0001",
    "name": "a new list",
    "labels": [{ "value": "New list with IRI", "language": "en"}],
    "comments": [{ "value": "New comment", "language": "en"}]
  }
```

The response will contain the basic information of the list, `listinfo` and an empty list of its children, as below:
```json
{
    "list": {
        "children": [],
        "listinfo": {
            "comments": [{ "value": "New comment", "language": "en"}],
            "id": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
            "isRootNode": true,
            "labels": [
                {
                    "value": "New list with IRI",
                    "language": "en"
                }
            ],
            "name": "a new list",
            "projectIri": "http://rdfh.ch/projects/0001"
        }
    }
}
```

### Create new child node

- Required permission: SystemAdmin / ProjectAdmin
- Required fields: `parentNodeIri`, `projectIri`, `labels`,
- Appends a new child node under the supplied nodeIri. If the supplied nodeIri
  is the listIri, then a new child node is appended to the top level. If a position is given
  for the new child node, the node will be created and inserted in the specified position, otherwise
  the node is appended to the end of parent's children.
- POST: `/admin/lists/<parentNodeIri>`
- BODY:

```json
     {   
         "parentNodeIri": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
         "projectIri": "http://rdfh.ch/projects/0001",
         "name": "a child",
         "labels": [{ "value": "New List Node", "language": "en"}]
    }
```

Additionally, each child node can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```json
{    "id": "http://rdfh.ch/lists/0001/8u37MxBVMbX3XQ8-d31x6w",
     "parentNodeIri": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
     "projectIri": "http://rdfh.ch/projects/0001",
     "name": "a child",
     "labels": [{ "value": "New List Node", "language": "en"}]
}
```

The response will contain the basic information of the node, `nodeinfo`, as below:
```json
{
    "nodeinfo": {
        "comments": [],
        "hasRootNode": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
        "id": "http://rdfh.ch/lists/0001/8u37MxBVMbX3XQ8-d31x6w",
        "labels": [
            {
                "value": "New List Node",
                "language": "en"
            }
        ],
        "name": "a new child",
        "position": 1
    }
}
```
The new node can be created and inserted in a specific position which must be given in the payload as shown below. If necessary,
according to the given position, the sibling nodes will be shifted. Note that `position` cannot have a value higher than the
number of existing children.

```json
{   "parentNodeIri": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
    "projectIri": "http://rdfh.ch/projects/0001",
    "name": "Inserted new child",
    "position": 0,
    "labels": [{ "value": "New List Node", "language": "en"}]
}
```

In case the new node should be appended to the list of current children, either `position: -1` must be given in the
payload or the `position` parameter must be left out of the payload.
 
### Update list's or node's information
The basic information of a list (or node) such as its labels, comments, name, or all of them can be updated. The parameters that 
must be updated together with the new value must be given in the JSON body of the request together with the IRI of the 
list and the IRI of the project it belongs to. 

 - Required permission: SystemAdmin / ProjectAdmin
 - Required fields: `listIri`, `projectIri`
 - Update list information
 - PUT: `/admin/lists/<listIri>`
 - BODY:
 
```json
 {   "listIri": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
      "projectIri": "http://rdfh.ch/projects/0001",
      "name": "new name for the list",
      "labels": [{ "value": "a new label for the list", "language": "en"}],
      "comments": [{ "value": "a new comment for the list", "language": "en"}]
  }
```

The response will contain the basic information of the list, `listinfo` (or `nodeinfo`), without its children, as below:
```json
{
    "listinfo": {
        "comments": [
            {
                "value": "a new comment for the list",
                "language": "en"
            }
        ],
        "id": "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A",
        "isRootNode": true,
        "labels": [
            {
                "value": "a new label for the list",
                "language": "en"
            }
        ],
        "name": "new name for the list",
        "projectIri": "http://rdfh.ch/projects/0001"
    }
}
```
If only name of the list must be updated, it can be given as below in the body of the request:

```json
   {
       "listIri": "listIri",
       "projectIri": "someprojectiri",
       "name": "another name"
  }
```

Alternatively, basic information `name`, `labels`, or `comments` of the root node (i.e. list) can be updated individually 
as explained below.

### Update list or node's name

 - Required permission: SystemAdmin / ProjectAdmin
 - Update name of the list (i.e. root node) or a child node whose IRI is specified by `<listItemIri>`.
 - PUT: `/admin/lists/<listItemIri>/name`
 - BODY:
 The new name of the node must be given in the body of the request as shown below:
 ```json
{
    "name": "a new name"
}
```
There is no need to specify the project IRI because it is automatically extracted using the given `<listItemIRI>`.

### Update list or node's labels

 - Required permission: SystemAdmin / ProjectAdmin
 - Update labels of the list (i.e. root node) or a child node whose IRI is specified by `<listItemIri>`.
 - PUT: `/admin/lists/<listItemIri>/labels`
 - BODY:
 The new set of labels of the node must be given in the body of the request as shown below:
 ```json
{
    "labels": [{"language": "se", "value": "nya märkningen"}]
}
```
There is no need to specify the project IRI because it is automatically extracted using the given `<listItemIRI>`.

### Update list or node's comments

 - Required permission: SystemAdmin / ProjectAdmin
 - Update comments of the list (i.e. root node) or a child node whose IRI is specified by `<listItemIri>`.
 - PUT: `/admin/lists/<listItemIri>/labels`
 - BODY:
 The new set of comments of the node must be given in the body of the request as shown below:
 ```json
{
    "comments": [{"language": "se", "value": "nya kommentarer"}]
}
```
There is no need to specify the project IRI because it is automatically extracted using the given `<listItemIRI>`.

### Repositioning a child node

The position of an existing child node can be updated. The child node can be either repositioned within its 
current parent node, or can be added to another parent node in a specific position. The IRI of the parent node 
and the new position of the child node must be given in the request body. 

If a node is supposed to be repositioned to the end of a parent node's children, give `position: -1`.

Suppose a parent node `parentNode1` has five children in positions 0-4, to change the position of its child node 
`childNode4` from its original position 3 to position 1 the request body should specify the IRI of its parent node 
and the new position as below:
```json
   {
      "parentNodeIri": "<parentNode1-IRI>",
      "position": 1
  }
```

Then the node `childNode4` will be put in position 1, and its siblings will be shifted accordingly. The new position given 
in the request body cannot be the same as the child node's original position. If `position: -1` is given, the node will 
be moved to the end of children list, and its siblings will be shifted to left. In case of repositioning the node 
within its current parent, the maximum permitted position is the length of its children list, i.e. in this example the 
highest allowed position is 4.

To reposition a child node `childNode4` to another parent node `parentNode2` in a specific position, for 
example `position: 3`, the IRI of the new parent node and the position the node must be placed within children of 
`parentNode2` must be given as:

```json
   {
      "parentNodeIri": "<parentNode2-IRI>",
      "position": 3
  }
```

In this case, the `childNode4` is removed from the list of children of its old parent `parentNode1` and its old 
siblings are shifted accordingly. Then the node `childNode4` is added to the specified new parent, i.e. `parentNode2`, in 
the given position. The new siblings are shifted accordingly.

Note that, the furthest the node can be placed is at the end of the list of the children of `parentNode2`. That means 
if `parentNode2` had 3 children with positions 0-2, then `childNode4` can be placed in position 0-3 within children 
of its new parent node. If the `position: -1` is given, the node will be appended to the end of new parent's children, 
and new siblings will not be shifted. 

Values less than -1 are not permitted for parameter `position`.
 
- Required permission: SystemAdmin / ProjectAdmin
- Response: returns the updated parent node with all its children.
- Put `/admin/lists/<nodeIri>/position`

### Delete a list or a node
An entire list or a single node of it can be completely deleted, if not in use. Before deleting an entire list 
(i.e. root node), the data and ontologies are checked for any usage of the list or its children. If not in use, the list 
and all its children are deleted.

Similarily, before deleting a single node of a list, it is verified that the node itself and none of its children are used.
If not in use, the node and all its children are deleted. Once a node is deleted, its parent node is updated by shifting the 
remaining child nodes with respect to the position of the deleted node. 

- Required permission: SystemAdmin / ProjectAdmin
- Response:
    - If the IRI of the list (i.e. root node) is given, the `iri` of the deleted list with a flag `deleted: true` is returned.
    - If the IRI of a child node is given, the updated parent node is returned.

- Delete `/admin/lists/<listItemIri>`
