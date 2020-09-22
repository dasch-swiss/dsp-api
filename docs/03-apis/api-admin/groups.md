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

# Groups Endpoint

## Endpoint Overview

**Group Operations:**

- `GET: /admin/groups` : return all groups
- `GET: /admin/groups/<groupIri>` : return single group identified by [IRI]
- `POST: /admin/groups` : create a new group
- `PUT: /admin/groups/<groupIri>` : update groups's basic information
- `PUT: /admin/groups/<groupIri>/status` : update group's status
- `DELETE: /admin/groups/<groupIri>` : delete group (set status to false)

**Member Operations:**

- `GET: /admin/groups/<groupIri>/members` : return all group members


## Group Operations

### Create Group

- Required permission: SystemAdmin / hasProjectAllAdminPermission
/ hasProjectAllGroupAdminPermission
- Required information: name (unique inside project), project IRI
- Optional information: group description
- Returns information about the newly created group
- TypeScript Docs: groupFormats - CreateGroupApiRequestV1
- POST: `/admin/groups`
- BODY:

```json
    {
      "name": "NewGroup",
      "description": "NewGroupDescription",
      "project": "http://rdfh.ch/projects/00FF",
      "status": true,
      "selfjoin": false
    }
```

Additionally, each group can have an optional custom IRI (of @ref:[Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) 
specified by the `id` in the request body as below:

```json
    { 
      "id": "http://rdfh.ch/groups/00FF/group-with-custom-Iri",  
      "name": "GroupWithCustomIRI",
      "description": "A new group with a custom IRI",
      "project": "http://rdfh.ch/projects/00FF",
      "status": true,
      "selfjoin": false
    }
```

### Update group information

- Required permission: SystemAdmin / hasProjectAllAdminPermission
/ hasProjectAllGroupAdminPermission /
hasProjectRestrictedGroupAdminPermission (for this group)
- Changeable information: `name`, `description`, `selfjoin`
- TypeScript Docs: groupFormats - ChangeGroupApiRequestADM
- PUT: `/admin/groups/<groupIri>`
- BODY:

```json
{
  "name": "UpdatedGroupName",
  "description": "UpdatedGroupDescription",
  "selfjoin": false
}
```

### Change Group Status:

- Required permission: SystemAdmin / hasProjectAllAdminPermission
- Changeable information: `status`
- Remark: Deleting a group, removes all members from the group.
- PUT: `/admin/groups/<groupIri>/status`
- BODY:

```
{
  "status": false
}
```

### Delete Group:

- Required permission: SystemAdmin / hasProjectAllAdminPermission
- Remark: The same as changing the groups `status` to
`false`. To un-delete, set `status` to `true`.
- DELETE: `/admin/groups/<groupIri>`

Example Group Information stored in admin named graph: :

```
<http://rdfh.ch/groups/[shortcode]/[UUID]>
     rdf:type knora-admin:UserGroup ;
     knora-admin:groupName "Name of the group" ;
     knora-admin:groupDescription "A description of the group" ;
     knora-admin:belongsToProject <http://rdfh.ch/projects/[UUID]> ;
     knora-admin:status "true"^^xsd:boolean ;
     knora-admin:hasSelfJoinEnabled "false"^^xsd:boolean .
```

## Member Operations

### Get Group Members

- Returns all group members
- Required permission: SystemAdmin / ProjectAdmin
- GET: `/admin/groups/<groupIri>/members`