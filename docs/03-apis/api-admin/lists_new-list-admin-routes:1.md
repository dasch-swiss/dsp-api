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
## To use this endpoint activate `new-list-admin-routes:1` [feature toggle](../feature-toggles.md) in the header of requests.

## Endpoint Overview

**List Item Operations:**

- `POST: /admin/lists` : create new list item (root or child node)

- `GET: /admin/lists[?projectIri=<projectIri>]` : return all lists optionally filtered by project

- `GET: /admin/lists/<listItemIri>` : if given a root node IRI, return complete list with children. 
Otherwise, if given a child node IRI, it returns node completely with its immediate children.

- `GET: /admin/lists/<listItemIri>/info` : return information (without children) of the node whose IRI is given 
(root or child).

- `PUT: /admin/lists/<listItemIri>` : update information of the node (root or child).
- `PUT: /admin/lists/<listItemIri>/name` : update the name of the node (root or child).
- `PUT: /admin/lists/<listItemIri>/lables` : update labels of the node (root or child).
- `PUT: /admin/lists/<listItemIri>/comments` : update comments of the node (root or child).

### Get lists

 - Required permission: none
 - Return all lists optionally filtered by project
 - GET: `/admin/lists[?projectIri=<projectIri>]`

### Get list item (entire list or a node)

 - Required permission: none
 - Return complete list with all children of the list, if the IRI of the list (i.e. root node) is given. 
 Otherwise if the IRI of a child node is given, return the entire node with its immediate children.
 - GET: `/admin/lists/<listItemIri>`


### Get item information (entire list or a node)

 - Required permission: none
 - Return basic information about the node (root or child) leaving out its list of children.
 - GET: `/admin/lists/<listItemIri>/info`
 
### Create new item (entire list or a node)

  - Required permission: SystemAdmin / ProjectAdmin
  - POST: `/admin/lists`
  - BODY:
  
**To create an entirely new list**, the IRI of the project must be given in the body of the request to which the list 
is supposed to be attached. 
Further basic information about the list such as its `labels` and `comments` must also be provided. 
Optionally, the request body can contain a `name` for the list which must be unique in the project.

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
        "name": "a name",
        "labels": [{ "value": "Neue Liste mit IRI", "language": "de"}],
        "comments": []
    }
```

**To create a new list node**, the IRI of its parent node must be given in the request body by `parentNodeIri`. 
Furthermore, the request body should also contain the project IRI of the list and basic information of the node as below:

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

### Update basic information (entire list or a node)
The basic information of a list or a node such as its labels, comments, name, or all of them can be updated. The parameters that 
must be updated together with the new value must be given in the JSON body of the request together with the IRI of the 
list item (root or child node) and the IRI of the project it belongs to. 

 - Required permission: SystemAdmin / ProjectAdmin
 - Update list information
 - PUT: `/admin/lists/<listItemIri>`
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

