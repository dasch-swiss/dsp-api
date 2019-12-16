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
- `PUT: /admin/lists/<listIRI>/<attribute>` : update list information

**List Node operations**

- NOT IMPLEMENTED: `GET: /admin/nodes/<nodeIRI>` : return list node information (with children)
- `GET: /admin/nodes/<nodeIRI>/Info` : return list node information (without children)
- `POST: /admin/nodes` : create new child node under the supplied parent node IRI
- NOT IMPLEMENTED: `PUT: /admin/nodes/<nodeIRI>` : create child node with given IRI und the supplied parent node IRI
- `PUT: /admin/nodes/<nodeIRI>/<attribute>` : update list node information
- NOT IMPLEMENTED: `DELETE: /admin/nodes/<nodeIRI>` : delete list node including children if not used

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
    

### Create new list with given IRI

  - Required permission: SystemAdmin or ProjectAdmin
  - PUT: `/admin/lists`
  - BODY:
    ```
    {
        
    } 
    ```
    

### Get list's information

 - Required permission: none
 - Return list information (without children)
 - GET: `/admin/lists/<listIRI>/Info`
 
### Update list's information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list information
 - PUT: `/admin/lists/<listIRI>/<attribute>`
 - attributes:
   * ListInfoName
   * ListInfoLabel
   * ListInfoComment
 - BODY:
    ```
    {
        "listIri" = "listIri",
        "projectIri" = "projectIri",
        "name" = "name",
        "labels" = [],
        "comments" = [] 
    }
    ```
 - Submit empty parameters to delete values, except `labels`, that always need at least one entry
 - `name` must be unique inside project


### Delete list

 - Required permission: SystemAdmin or ProjectAdmin
 - Delete List including children if not used
 - DELETE: `/admin/lists/<listIRI>`


## List Node Operations

### Get List Node

 - Required permission: none
 - Return list node with children
 - GET: `/admin/nodes/<nodeIRI>`
 
### Get node's information

 - Required permission: none
 - Return node information (without children)
 - GET: `/admin/node/<nodeIRI>/Info`

### Create new child node

  - Required permission: SystemAdmin or ProjectAdmin
  - Appends a new child node under the supplied nodeIri. If the supplied nodeIri
    is the listIri, then a new child node is appended to the top level. Children
    are currently only appended.
  - POST: `/admin/nodes`
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
    
### Create new child node with given IRI

  - Required permission: SystemAdmin or ProjectAdmin
  - Appends a new child node under the supplied nodeIri. If the supplied nodeIri
    is the listIri, then a new child node is appended to the top level. Children
    are currently only appended.
  - PUT: `/admin/nodes/<nodeIRI>`
  - BODY:
    ```
    {
        
    }
    ```
    
### Delete List node

 - Required permission: SystemAdmin or ProjectAdmin
 - Delete node including children if not used
 - DELETE: `/admin/nodes/<nodeIRI>`
 
### Update node's information

 - Required permission: SystemAdmin or ProjectAdmin
 - Update list information
 - PUT: `/admin/nodes/<nodeIRI>/<attribute>`
 - attributes:
   * NodeInfoName
   * NodeInfoLabel
   * NodeInfoComment
   * NodeInfoPosition
   * NodeInfoParent
 - BODY
   ```
   {
        "nodeIri": "nodeIri",
        "projectIri": "projectIri",
        "name": name,
        "labels": [],
        "comments": [],
        "position": 0,
        
   }
   ```
 - Submit empty parameters to delete values, except `labels`, that always need at least one entry
 - `name` must be unique inside list
 