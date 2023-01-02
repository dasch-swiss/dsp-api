<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
- Optional information: group descriptions
- Returns information about the newly created group
- TypeScript Docs: groupFormats - CreateGroupApiRequestV1
- POST: `/admin/groups`
- BODY:

```json
    {
      "name": "NewGroup",
      "descriptions": [
        {"value": "NewGroupDescription", "language": "en"},
        {"value": "NeueGruppenBeschreibung", "language": "de"}
      ],
      "project": "http://rdfh.ch/projects/00FF",
      "status": true,
      "selfjoin": false
    }
```

Additionally, each group can have an optional custom IRI (of @ref:[Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) 
specified by the `id` in the request body as below:

```json
    { 
      "id": "http://rdfh.ch/groups/00FF/a95UWs71KUklnFOe1rcw1w",  
      "name": "GroupWithCustomIRI",
      "descriptions": [{"value": "A new group with a custom IRI", "language": "en"}],
      "project": "http://rdfh.ch/projects/00FF",
      "status": true,
      "selfjoin": false
    }
```

### Update group information

- Required permission: SystemAdmin / hasProjectAllAdminPermission
/ hasProjectAllGroupAdminPermission /
hasProjectRestrictedGroupAdminPermission (for this group)
- Changeable information: `name`, `descriptions`, `selfjoin`
- TypeScript Docs: groupFormats - ChangeGroupApiRequestADM
- PUT: `/admin/groups/<groupIri>`
- BODY:

```json
{
  "name": "UpdatedGroupName",
  "descriptions": [{"value": "UpdatedGroupDescription", "language": "en"}],
  "selfjoin": false
}
```

### Change Group Status:

- Required permission: SystemAdmin / hasProjectAllAdminPermission
- Changeable information: `status`
- Remark: Deleting a group, removes all members from the group.
- PUT: `/admin/groups/<groupIri>/status`
- BODY:

```json
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
     knora-admin:groupDescriptions "A description of the group"@en ;
     knora-admin:belongsToProject <http://rdfh.ch/projects/[UUID]> ;
     knora-admin:status "true"^^xsd:boolean ;
     knora-admin:hasSelfJoinEnabled "false"^^xsd:boolean .
```

## Member Operations

### Get Group Members

- Returns all group members
- Required permission: SystemAdmin / ProjectAdmin
- GET: `/admin/groups/<groupIri>/members`
