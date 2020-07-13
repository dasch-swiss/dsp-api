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

- `GET: /admin/lists[?projectIri=<projectIri>]` : return all lists optionally filtered by project
- `GET: /admin/lists/<listIri>` : return complete list with children
- `POST: /admin/lists` : create new list
- `POST: /admin/lists/<nodeIri>` : create new child node under the supplied parent node IRI
- NOT IMPLEMENTED: `DELETE: /admin/lists/<listIri>` : delete list including children if not used
- `GET: /admin/lists/infos/<listIri>` : return list information (without children)
- `PUT: /admin/lists/infos/<listIri>` : update list information

**List Node operations**

- `GET: /admin/lists/nodes/<nodeIri>` : return list node information (without children)
- NOT IMPLEMENTED: `POST: /admin/lists/nodes/<nodeIri>` : update list node information
- NOT IMPLEMENTED: `DELETE: /admin/lists/nodes/<nodeIri>` : delete list node including children if not used

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
  
```
    {
        "projectIri": "someprojectiri",
        "labels": [{ "value": "Neue Liste", "language": "de"}],
        "comments": []
    } 
```

Additionally, each list can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) specified by the `id` in the request body as below:

```
    {
        "id": "http://rdfh.ch/lists/0001/a-list-with-IRI",
        "projectIri": "http://rdfh.ch/projects/0001",
        "labels": [{ "value": "Neue Liste mit IRI", "language": "de"}],
        "comments": []
    }
```

### Create new child node

  - Required permission: SystemAdmin / ProjectAdmin
  - Appends a new child node under the supplied nodeIri. If the supplied nodeIri
    is the listIri, then a new child node is appended to the top level. Children
    are currently only appended.
  - POST: `/admin/lists/<nodeIri>`
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

### Get list's information

 - Required permission: none
 - Return list information (without children)
 - GET: `/admin/lists/infos/<listIri>`
 
### Update list's information

 - Required permission: none
 - Update list information
 - PUT: `/admin/lists/infos/<listIri>`
 - BODY:
 
```
   {
       "listIri": "listIri",
       "projectIri": "someprojectiri",
       "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
       "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
   }
```

## List Node Operations

### Get List Node Information

 - Required permission: none
 - Return list node information (without children)
 - GET: `/admin/lists/nodes/<nodeIri>`
