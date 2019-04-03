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

  - **Create group**:

      - Required permission: SystemAdmin / hasProjectAllAdminPermission
        / hasProjectAllGroupAdminPermission
      - Required information: name (unique inside project), project IRI
      - Optional information: group description
      - Returns information about the newly created group
      - TypeScript Docs: groupFormats - CreateGroupApiRequestV1
      - POST: `/admin/groups`
      - BODY:

```
{
  "name": "NewGroup",
  "description": "NewGroupDescription",
  "project": "http://rdfh.ch/projects/00FF",
  "status": true,
  "selfjoin": false
}
```

  - **Update group information**:

      - Required permission: SystemAdmin / hasProjectAllAdminPermission
        / hasProjectAllGroupAdminPermission /
        hasProjectRestrictedGroupAdminPermission (for this group)
      - Changeable information: name, description, status, selfjoin
      - TypeScript Docs: groupFormats - ChangeGroupApiRequestV1
      - PUT: `/admin/groups/<groupIri>`
      - BODY:

```
{
  "name": "UpdatedGroupName",
  "description": "UpdatedGroupDescription".
  "status": true,
  "selfjoin": false
}
```

  - **Delete group (-\update group)**:

      - Required permission: SystemAdmin / hasProjectAllAdminPermission
      - Remark: The same as updating a group and changing `status` to
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

