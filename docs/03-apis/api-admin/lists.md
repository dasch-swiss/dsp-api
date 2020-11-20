<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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

# Lists Endpoint

## Endpoint Overview

**List Item Operations:**

- `GET: /admin/lists[?projectIri=<projectIri>]` : return all lists optionally filtered by project
- `GET: /admin/lists/<listItemIri>` : return complete list with all children if IRI of the list (i.e. root node) is given. 
If IRI of the child node is given, return the node with its immediate children. 
- `GET: /admin/lists/infos/<listIri>` : return list information (without children)
- `GET: /admin/lists/nodes/<nodeIri>` : return list node information (without children)

- `POST: /admin/lists` : create new list
- `POST: /admin/lists/<parentNodeIri>` : create new child node under the supplied parent node IRI

- `PUT: /admin/lists/<listItemIri>` : update node information (root or child.)
- `PUT: /admin/lists/<listItemIri>/name` : update the name of the node (root or child).
- `PUT: /admin/lists/<listItemIri>/labels` : update labels of the node (root or child).
- `PUT: /admin/lists/<listItemIri>/comments` : update comments of the node (root or child).
- NOT IMPLEMENTED: `DELETE: /admin/lists/nodes/<nodeIri>` : delete list node including children if not used
- NOT IMPLEMENTED: `DELETE: /admin/lists/<listIri>` : delete list including children if not used

## List Operations

### Get lists

 - Required permission: none
 - Return all lists optionally filtered by project
 - GET: `/admin/lists[?projectIri=<projectIri>]`

### Get list

 - Required permission: none
 - Return complete `list` including basic information of the list, `listinfo`, and all its children.
 - GET: `/admin/lists/<listIri>`


### Create new list

  - Required permission: SystemAdmin / ProjectAdmin
  - POST: `/admin/lists`
  - BODY:
  
```json
    {
        "projectIri": "someprojectiri",
        "labels": [{ "value": "Neue Liste", "language": "de"}],
        "comments": []
    } 
```

Additionally, each list can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```json
  {
    "id": "http://rdfh.ch/lists/0001/a-list",
    "projectIri": "http://rdfh.ch/projects/0001",
    "name": "a new list",
    "labels": [{ "value": "Neue Liste mit IRI", "language": "de"}],
    "comments": []
  }
```

The response will contain the basic information of the list, `listinfo` and an empty list of its children, as below:
```json
{
    "list": {
        "children": [],
        "listinfo": {
            "comments": [],
            "id": "http://rdfh.ch/lists/0001/a-list",
            "isRootNode": true,
            "labels": [
                {
                    "value": "Neue Liste mit IRI",
                    "language": "de"
                }
            ],
            "name": "a new list",
            "projectIri": "http://rdfh.ch/projects/0001"
        }
    }
}
```
### Get list's information

 - Required permission: none
 - Return list information, `listinfo` (without children).
 - GET: `/admin/lists/infos/<listIri>`
 
### Update list's information
The basic information of a list such as its labels, comments, name, or all of them can be updated. The parameters that 
must be updated together with the new value must be given in the JSON body of the request together with the IRI of the 
list and the IRI of the project it belongs to. 

 - Required permission: SystemAdmin / ProjectAdmin
 - Update list information
 - PUT: `/admin/lists/<listIri>`
 - BODY:
 
```json
 {   "listIri": "http://rdfh.ch/lists/0001/a-list",
      "projectIri": "http://rdfh.ch/projects/0001",
      "name": "new name for the list",
      "labels": [{ "value": "a new label for the list", "language": "en"}],
      "comments": [{ "value": "a new comment for the list", "language": "en"}]
  }
```

The response will contain the basic information of the list, `listinfo`, without its children, as below:
```json
{
    "listinfo": {
        "comments": [
            {
                "value": "a new comment for the list",
                "language": "en"
            }
        ],
        "id": "http://rdfh.ch/lists/0001/a-list",
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


## List Node Operations
### Get node

 - Required permission: none
 - Return complete `node` including basic information of the list, `nodeinfo`, and all its immediate children.
 - GET: `/admin/lists/<nodeIri>`

### Get List Node Information

 - Required permission: none
 - Return node information, `nodeinfo`, (without children).
 - GET: `/admin/lists/nodes/<nodeIri>`


### Create new child node

  - Required permission: SystemAdmin / ProjectAdmin
  - Appends a new child node under the supplied nodeIri. If the supplied nodeIri
    is the listIri, then a new child node is appended to the top level. Children
    are currently only appended.
  - POST: `/admin/lists/<parentNodeIri>`
  - BODY:
  
```json
     {   
         "parentNodeIri": "http://rdfh.ch/lists/0001/a-list",
         "projectIri": "http://rdfh.ch/projects/0001",
         "name": "a child",
         "labels": [{ "value": "New List Node", "language": "en"}],
         "comments": []
    }
```

Additionally, each child node can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```json
{    "id": "http://rdfh.ch/lists/0001/a-childNode",
     "parentNodeIri": "http://rdfh.ch/lists/0001/a-list",
     "projectIri": "http://rdfh.ch/projects/0001",
     "name": "a child",
     "labels": [{ "value": "New List Node", "language": "en"}],
     "comments": []
}
```

The response will contain the basic information of the node, `nodeinfo`, as below:
```json
{
    "nodeinfo": {
        "comments": [],
        "hasRootNode": "http://rdfh.ch/lists/0001/a-list",
        "id": "http://rdfh.ch/lists/0001/a-childNode",
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

### Update node's information
The basic information of a node such as its labels, comments, name, or all of them can be updated. The parameters that 
must be updated together with the new value must be given in the JSON body of the request together with the IRI of the 
node and the IRI of the project it belongs to. 

 - Required permission: SystemAdmin / ProjectAdmin
 - Update node information
 - PUT: `/admin/lists/<nodeIri>`
 - BODY:
 
```json
  {   "listIri": "http://rdfh.ch/lists/0001/a-childNode",
       "projectIri": "http://rdfh.ch/projects/0001",
       "name": "new node name",
       "labels": [{ "value": "new node label", "language": "en"}],
       "comments": [{ "value": "new node comment", "language": "en"}]
   }
```

The response will contain the basic information of the node as `nodeInfo` without its children, as below:

```json
{
    "nodeinfo": {
        "comments": [
            {
                "value": "new node comment",
                "language": "en"
            }
        ],
        "hasRootNode": "http://rdfh.ch/lists/0001/a-list",
        "id": "http://rdfh.ch/lists/0001/a-childNode",
        "labels": [
            {
                "value": "new node label",
                "language": "en"
            }
        ],
        "name": "new node name",
        "position": 0
    }
}
```

If only name of the node must be updated, it can be given as below in the body of the request:

```json
   {
      "listIri": "nodeIri",
      "projectIri": "projectIri",
      "name": "another name"
  }
```

Alternatively, basic information of the child node can be updated individually as explained above (See 
[update node name](#update-list-or-nodes-name), [update node labels](#update-list-or-nodes-labels), and 
[update node comments](#update-list-or-nodes-comments)).