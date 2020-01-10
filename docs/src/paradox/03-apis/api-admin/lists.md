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

**List Operations:**

- `GET: /admin/lists[?projectIRI=<projectIRI>]` : return all lists optionally filtered by project
- `GET: /admin/lists/<listIRI>` : return complete list with children
- `POST: /admin/lists` : create new list
- NOT IMPLEMENTED: `PUT: /admin/lists/<listIRI>` : create new list with given IRI
- NOT IMPLEMENTED: `DELETE: /admin/lists/<listIRI>` : delete list including children if not used
- `GET: /admin/lists/<listIRI>/Info` : return list information (without children)
- `PUT: /admin/lists/<listIRI>/ListInfoName` : update list name information
- `PUT: /admin/lists/<listIRI>/ListInfoLabel` : update list label information
- `PUT: /admin/lists/<listIRI>/ListInfoComment` : update list comment information



**List Node operations**

- NOT IMPLEMENTED: `GET: /admin/lists/nodes/<nodeIRI>` : return list node information (with children)
- `GET: /admin/lists/nodes/<nodeIRI>/Info` : return list node information (without children)
- `POST: /admin/lists/nodes` : create new child node under the supplied parent node IRI
- NOT IMPLEMENTED: `PUT: /admin/lists/nodes/<nodeIRI>` : create child node with given IRI und the supplied parent node IRI
- `PUT: /admin/lists/nodes/<nodeIRI>/NodeInfoName` : update list node name information
- `PUT: /admin/lists/nodes/<nodeIRI>/NodeInfoLabel` : update list node label information
- `PUT: /admin/lists/nodes/<nodeIRI>/NodeInfoComment` : update list node comment information
- NOT IMPLEMENTED: `PUT: /admin/lists/nodes/<nodeIRI>/NodeInfoPosition` : update list node position information
- NOT IMPLEMENTED: `PUT: /admin/lists/nodes/<nodeIRI>/NodeInfoParent` : update list node parent information
- NOT IMPLEMENTED: `DELETE: /admin/lists/nodes/<nodeIRI>` : delete list node including children if not used

## List Operations

### Get lists

 - Required permission: none
 - Return all lists optionally filtered by project
 - GET: `/admin/lists[?projectIRI=<projectIRI>]`

### Get list

 - Required permission: none
 - Return complete list with children
 - GET: `/admin/lists/<listIRI>`


### Create new list

  - Required permission: SystemAdmin or ProjectAdmin
  - POST: `/admin/lists`
  - BODY:
    ```
    {
        "projectIri": "someprojectiri",
        "labels": [{ "value": "Neue Liste", "language": "de"}],
        "comments": []
    } 
    ```

### Get list's information

 - Required permission: none
 - Return list information (without children)
 - GET: `/admin/lists/<listIRI>/Info`
 
### Update lists's name information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list name information
 - PUT: `/admin/lists/<listIri>/ListInfoName`
 - BODY
   ```
   {
        "listIri": "listIri",
        "projectIri": "projectIri",
        "name": "name"
   }
   ```
 - Submit empty parameter (`"name": ""`) to delete name
 - `name` must be unique inside project
 
### Update list's label information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list label information
 - PUT: `/admin/lists/<listIri>/ListInfoLabel`
 - BODY
   ```
   {
        "listIri": "listIri",
        "projectIri": "projectIri",
        "labels": [{"value": "New label", "language": "en"}]
   }
   ```
 - At least one label must be submitted
 
### Update list's comment information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list comment information
 - PUT: `/admin/lists/<listIri>/ListInfoName`
 - BODY
   ```
   {
        "listIri": "listIri",
        "projectIri": "projectIri",
        "comment": [{"value": "New Comment", "language": "en"}]
   }
   ```
 - Submit empty parameter (`"comment": []`) to delete comments
 


### Delete list

 - Required permission: SystemAdmin or ProjectAdmin
 - Delete List including children if not used
 - DELETE: `/admin/lists/<listIRI>`


## List Node Operations

### Get List Node

 - Required permission: none
 - Return list node with children
 - GET: `/admin/lists/nodes/<nodeIRI>`
 
### Get node's information

 - Required permission: none
 - Return node information (without children)
 - GET: `/admin/lists/node/<nodeIRI>/Info`

### Create new child node

  - Required permission: SystemAdmin or ProjectAdmin
  - Appends a new child node under the supplied nodeIri. If the supplied nodeIri
    is the listIri, then a new child node is appended to the top level. Children
    are currently only appended.
  - POST: `/admin/lists/nodes`
  - BODY:
    ```
    {
        "parentNodeIri": "nodeIri",
        "projectIri": "someprojectiri",
        "name": "first",
        "labels": [{ "value": "New First Child List Node Value", "language": "en"}],
        "comments": [{ "value": "New First Child List Node Comment", "language": "en"}]
    }
    ```
    
### Delete List node

 - Required permission: SystemAdmin or ProjectAdmin
 - Delete node including children if not used
 - DELETE: `/admin/lists/nodes/<nodeIRI>`
 
### Update node's name information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list node name information
 - PUT: `/admin/lists/nodes/<nodeIRI>/NodeInfoName`
 - BODY
   ```
   {
        "nodeIri": "nodeIri",
        "projectIri": "projectIri",
        "name": "name"
   }
   ```
 - Submit empty parameter (`"name": ""`) to delete name
 - `name` must be unique inside project
 
### Update node's label information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list node label information
 - PUT: `/admin/lists/nodes/<nodeIRI>/NodeInfoLabel`
 - BODY
   ```
   {
        "nodeIri": "nodeIri",
        "projectIri": "projectIri",
        "labels": [{"value": "New label", "language": "en"}]
   }
   ```
 - At least one label must be submitted
 
### Update node's comment information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list node comment information
 - PUT: `/admin/lists/nodes/<nodeIRI>/NodeInfoName`
 - BODY
   ```
   {
        "nodeIri": "nodeIri",
        "projectIri": "projectIri",
        "comment": [{"value": "New Comment", "language": "en"}]
   }
   ```
 - Submit empty parameter (`"comment": []`) to delete comments
 