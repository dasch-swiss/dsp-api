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

## Endpoint Overview

**User Operations:**
- `GET: /admin/users` : return all users
- `GET: /admin/users/[iri | email | username]/<identifier>` : return single user identified by [IRI | email | username]
- `POST: /admin/users/` : create new user
- `PUT: /admin/users/iri/<userIri>/BasicUserInformation` : update user's basic user information
- `PUT: /admin/users/iri/<userIri>/Password` : update user's password
- `PUT: /admin/users/iri/<userIri>/Status` : update user's status
- `DELETE: /admin/users/iri/<userIri>` : delete user (set status to false)

**User's project membership operations**

- `GET: /admin/users/iri/<userIri>/project-memberships` : get user's project memberships
- `POST: /admin/users/iri/<userIri>/project-memberships/<projectIri>` : add user to project (to ProjectMember group)
- `DELETE: /admin/users/iri/<userIri>/project-memberships/<projectIri>` : remove user from project (to ProjectMember group)

**User's group membership operations**

- `GET: /admin/users/iri/<userIri>/project-admin-memberships` : get user's ProjectAdmin group memberships
- `POST: /admin/users/iri/<userIri>/project-admin-memberships/<projectIri>` : add user to ProjectAdmin group
- `DELETE: /admin/users/iri/<userIri>/project-admin-memberships/<projectIri>` : remove user from ProjectAdmin group

- `GET: /admin/users/iri/<userIri>/group-memberships` : get user's normal group memberships
- `POST: /admin/users/iri/<userIri>/group-memberships/<groupIri>` : add user to normal group
- `DELETE: /admin/users/iri/<userIri>/group-memberships/<groupIri>` : remove user from normal group

- `PUT: /admin/users/iri/<userIri>/SystemAdmin` : Add/remove user to/from SystemAdmin group

## User Operations

### Get users

  - Required permission: SystemAdmin
  - GET: `/admin/users`

### Get user

  - Required permission: SystemAdmin / self
  - GET:`/admin/users/[iri | email | username ]/<identifier>`

### Create user

  - Required permission: none, self-registration is allowed
  - Required information: email (unique), given name, family name,
    password, password, status, systemAdmin
  - Returns information about the newly created user
  - TypeScript Docs: userFormats - `CreateUserApiRequestV1`
  - POST: `/admin/users`
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

### Update basic user information**

  - Required permission: SystemAdmin / self
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
  - PUT: `/admin/users/iri/<userIri>/BasicUserInformation`
  - BODY:
    ```
    {
      "email": "donald.big.duck@example.org",
      "givenName": "Big Donald",
      "familyName": "Duckmann",
      "lang": "de"
    }
    ```

### Update user's password

  - Required permission: SystemAdmin / self
  - Changeable information: password
  - PUT: `/admin/users/iri/<userIri>/Password`
  - BODY:
    ```
    {
      "oldPassword": "test",
      "newPassword": "test1234"
    }
    ```

### Delete user

  - Required permission: SystemAdmin / self
  - Remark: The same as updating a user and changing `status` to
    `false`. To un-delete, set `status` to `true`.
  - PUT: `/admin/users/iri/<userIri>/Status`
  - BODY:
    ```
    {
      "status": false // true or false
    }
    ```

### Delete user (-\update user)**

  - Required permission: SystemAdmin / self
  - Remark: The same as updating a user and changing `status` to
    `false`. To un-delete, set `status` to `true`.
  - DELETE: `/admin/users/iri/<userIri>`
  - BODY: empty


## User's project membership operations

### Get user's project memberships

  - GET: `/admin/users/iri/<userIri>/project-memberships`

### Add/remove user to/from project

  - Required permission: SystemAdmin / ProjectAdmin / self (if
    project self-assignment is enabled)
  - Required information: project IRI, user IRI
  - Effects: `knora-base:isInProject` user property
  - POST / DELETE: `/admin/users/iri/<userIri>/projects/<projectIri>`
  - BODY: empty

## User's group membership operations

### Get user's project admin memberships

  - GET: `/admin/users/iri/<userIri>/project-admin-memberships`

### Add/remove user to/from project admin group

  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: project IRI, user IRI
  - Effects: `knora-base:isInProjectAdminGroup` user property
  - POST / DELETE: `/admin/users/iri/<userIri>/project-admin-memberships/<projectIri>`
  - BODY: empty

### Get user's group memberships**

  - GET: `/admin/users/iri/<userIri>/group-memberships`

### Add/remove user to/from 'normal' group** (not *SystemAdmin* or *ProjectAdmin*)

  - Required permission: SystemAdmin / hasProjectAllAdminPermission
    / hasProjectAllGroupAdminPermission /
    hasProjectRestrictedGroupAdminPermission (for this group) / User
    (if group self-assignment is enabled)
  - Required information: group IRI, user IRI
  - Effects: `knora-base:isInGroup`
  - POST / DELETE: `/admin/users/iri/<userIri>/group-memberships/<groupIri>`
  - BODY: empty

### Add/remove user to/from system admin group

  - Required permission: SystemAdmin / self
  - Effects property: `knora-base:isInSystemAdminGroup` with value
    `true` or `false`
  - PUT: `/admin/users/iri/<userIri>/SystemAdmin`
  - BODY:
    ```JSON
    {
      "newSystemAdminMembershipStatus": false
    }
    ```

## Example Data

The following is an example for user information stored in the `admin` named graph:

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
