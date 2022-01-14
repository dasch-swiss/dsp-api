<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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

  - Required permission:
    - SystemAdmin / self: for getting all properties
    - All other users: for getting only the public properties (`givenName` and `familyName`)
  - GET:`/admin/users/[iri | email | username ]/<identifier>`

### Create user

  - Required permission: none, self-registration is allowed
  - Required information: email (unique), given name, family name,
    password, status, systemAdmin
  - Username restrictions:
    - 4 - 50 characters long
    - Only contains alphanumeric characters, underscore and dot.
    - Underscore and dot can't be at the end or start of a username
    - Underscore or dot can't be used multiple times in a row
  - Returns information about the newly created user
  - TypeScript Docs: userFormats - `CreateUserApiRequestV1`
  - POST: `/admin/users`
  - BODY:
  
```json
    {
      "email": "donald.duck@example.org",
      "givenName": "Donald",
      "familyName": "Duck",
      "username": "donald.duck",
      "password": "test",
      "status": true,
      "lang": "en",
      "systemAdmin": false
    }
```
    
Additionally, each user can have an optional custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) 
specified by the `id` in the request body as below:

```json
    { 
      "id" : "http://rdfh.ch/users/FnjFfIQFVDvI7ex8zSyUyw",
      "email": "donald.duck@example.org",
      "givenName": "Donald",
      "familyName": "Duck",
      "username": "donald.duck",
      "password": "test",
      "status": true,
      "lang": "en",
      "systemAdmin": false
    }
```
     
### Update basic user information**

  - Required permission: SystemAdmin / self
  - Changeable information: username, email, given name, family name,
    password, status, SystemAdmin membership 
  - TypeScript Docs: userFormats - ChangeUserApiRequestADM
  - PUT: `/admin/users/iri/<userIri>/BasicUserInformation`
  - BODY:

```json
    {
      "username": "donald.big.duck",
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
  
```json
    {
      "requesterPassword": "test",
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
  - POST / DELETE: `/admin/users/iri/<userIri>/project-memberships/<projectIri>`
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
  
```
    {
      "systemAdmin": false
    }
```

## Example Data

The following is an example for user information stored in the `admin` named graph:

```
<http://rdfh.ch/users/c266a56709>
    rdf:type knora-admin:User ;
    knora-admin:username "user01.user1"^^xsd:string ;
    knora-admin:email "user01.user1@example.com"^^xsd:string ;
    knora-admin:givenName "User01"^^xsd:string ;
    knora-admin:familyName "User"^^xsd:string ;
    knora-admin:password "$e0801$FGl9FDIWw+D83OeNPGmD9u2VTqIkJopIQECgmb2DSWQLS0TeKSvYoWAkbEv6KxePPlCI3CP9MmVHuvnWv8/kag==$mlegCYdGXt+ghuo8i0rLjgOiNnGDW604Q5g/v7zwBPU="^^xsd:string ;
    knora-admin:preferredLanguage "de"^^xsd:string ;
    knora-admin:status "true"^^xsd:boolean ;
    knora-admin:isInProject <http://rdfh.ch/projects/00FF> ;
    knora-admin:isInSystemAdminGroup "false"^^xsd:boolean ;
    knora-admin:isInProjectAdminGroup <http://rdfh.ch/projects/00FF> .
```
