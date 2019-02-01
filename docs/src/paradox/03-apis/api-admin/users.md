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

# Users Endpoint

  - **Get users**

      - GET: `/admin/users`

  - **Get user**

      - GET:`/admin/users/<userIri>`

  - **Create user**:

      - Required permission: none, self-registration is allowed
      - Required information: email (unique), given name, family name,
        password, password, status, systemAdmin
      - Returns information about the newly created user
      - TypeScript Docs: userFormats - `CreateUserApiRequestV1`
      - POST: `/admin/users/`
      - BODY:

```
{
  "email": "donald.duck@example.org",
  "givenName": "Donald",
  "familyName": "Duck",
  "password": "test",
  "status": true,
  "lang": "en",
  "systemAdmin": false
}
```

  - **Update user information**:

      - Required permission: SystemAdmin / User
      - Changeable information: email, given name, family name,
        password, status, SystemAdmin membership
      - Remark: There are four distinct use case / payload combination.
        It is not possible to mix cases, e.g., sending `newUserStatus`
        and basic user information at the same time will result in an
        error: (1) change password: oldPassword, newPassword, (2) change
        status: newUserStatus, (3) change system admin membership:
        newSystemAdminMembershipStatus, and (4) change basic user
        information: email, givenName, familyName, lang
      - TypeScript Docs: userFormats - ChangeUserApiRequestV1
      - PUT: `/admin/users/<userIri>`
      - BODY:

```
{
  "email": "donald.big.duck@example.org",
  "givenName": "Big Donald",
  "familyName": "Duckmann",
  "lang": "de"
}
```

  - **Update user's password**

      - Required permission: User
      - Changeable information: password
      - PUT: `/admin/users/<userIri>`
      - BODY:

```
{
  "oldPassword": "test",
  "newPassword": "test1234"
}
```

  - **Delete user**:

      - Required permission: SystemAdmin / User
      - Remark: The same as updating a user and changing `status` to
        `false`. To un-delete, set `status` to `true`.
      - PUT: `/admin/users/<userIri>`
      - BODY:

```
{
  "status": false // true or false
}
```

  - **Delete user (-\update user)**

      - Required permission: SystemAdmin / User
      - Remark: The same as updating a user and changing `status` to
        `false`. To un-delete, set `status` to `true`.
      - DELETE: `/admin/projects/<projectIri>`
      - BODY: empty

  - **Get user's project memberships**

      - GET: `/admin/users/projects/<userIri>`

  - **Add/remove user to/from project**:

      - Required permission: SystemAdmin / ProjectAdmin / User (if
        project self-assignment is enabled)
      - Required information: project IRI, user IRI
      - Effects: `knora-base:isInProject` user property
      - POST / DELETE: `/admin/users/projects/<userIri>/<projectIri>`
      - BODY: empty

  - **Get user's project admin memberships**

      - GET: `/admin/users/projects-admin/<userIri>`

  - **Add/remove user to/from project admin group**

      - Required permission: SystemAdmin / ProjectAdmin
      - Required information: project IRI, user IRI
      - Effects: `knora-base:isInProjectAdminGroup` user property
      - POST / DELETE: `/v1/users/projects-admin/<userIri>/<projectIri>`
      - BODY: empty

  - **Get user's group memberships**

      - GET: `/admin/users/groups/<userIri>`

  - **Add/remove user to/from 'normal' group** (not *SystemAdmin* or
    *ProjectAdmin*):

      - Required permission: SystemAdmin / hasProjectAllAdminPermission
        / hasProjectAllGroupAdminPermission /
        hasProjectRestrictedGroupAdminPermission (for this group) / User
        (if group self-assignment is enabled)
      - Required information: group IRI, user IRI
      - Effects: `knora-base:isInGroup`
      - POST / DELETE: `/admin/users/groups/<userIri>/<groupIri>`
      - BODY: empty

  - **Add/remove user to/from system admin group**:

      - Required permission: SystemAdmin / User
      - Effects property: `knora-base:isInSystemAdminGroup` with value
        `true` or `false`
      - PUT: `/admin/users/<userIri>`
      - BODY:

```JSON
{
  "newSystemAdminMembershipStatus": false // true or false
}
```

Example User Information stored in admin graph: :

```
<http://rdfh.ch/users/c266a56709>
    rdf:type knora-base:User ;
    knora-base:username "user01.user1"^^xsd:string ;
    knora-base:email "user01.user1@example.com"^^xsd:string ;
    knora-base:givenName "User01"^^xsd:string ;
    knora-base:familyName "User"^^xsd:string ;
    knora-base:password "$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="^^xsd:string ;
    knora-base:preferredLanguage "de"^^xsd:string ;
    knora-base:status "true"^^xsd:boolean ;
    knora-base:isInProject <http://rdfh.ch/projects/00FF> ;
    knora-base:isInSystemAdminGroup "false"^^xsd:boolean ;
    knora-base:isInProjectAdminGroup <http://rdfh.ch/projects/00FF> .
```
