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
- `GET: /admin/lists/<listIri>` : return complete list with children
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
 - Return complete list with children
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
        "id": "http://rdfh.ch/lists/0001/a-list-with-IRI",
        "projectIri": "http://rdfh.ch/projects/0001",
        "labels": [{ "value": "Neue Liste mit IRI", "language": "de"}],
        "comments": []
    }
```
### Get list's information

 - Required permission: none
 - Return list information (without children)
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
   {
       "listIri": "listIri",
       "projectIri": "someprojectiri",
       "name": "a new name",
       "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
       "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
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

Alternatively, basic information `name`, `labels`, or `comments` or a list or a child node can be updated individually 
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

### Get List Node Information

 - Required permission: none
 - Return list node information (without children)
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
        "parentNodeIri": "parentNodeIri",
        "projectIri": "someprojectiri",
        "name": "first",
        "labels": [{ "value": "New First Child List Node Value", "language": "en"}],
        "comments": [{ "value": "New First Child List Node Comment", "language": "en"}]
    }
```

Additionally, each child node can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```json
    {
        "id": "http://rdfh.ch/lists/0001/a-child-node-with-IRI",
        "parentNodeIri": "http://rdfh.ch/lists/0001/a-list-with-IRI",
        "projectIri": "http://rdfh.ch/projects/0001",
        "name": "child node with a custom IRI",
        "labels": [{ "value": "New child node with IRI", "language": "en"}],
        "comments": [{ "value": "New child node comment", "language": "en"}]
    }
```
