<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Admin Endpoint

For the management of *users*, *projects*, *groups*, *lists*, and *permissions*, the DSP-API following a resource
centric approach, provides the following endpoints corresponding to the respective classes of objects that they have an
effect on, namely:

  - [Users endpoint](lists.md): `http://server:port/admin/users` - `knora-base:User`
  - [Projects endpoint](projects.md): `http://server:port/admin/projects` - `knora-base:knoraProject`
  - [Groups endpoint](groups.md): `http://server:port/admin/groups` - `knora-base:UserGroup`
  - [Lists endpoint](lists.md): `http://server:port/admin/lists` - `knora-base:ListNode`
  - [Permissions endpoint](permissions.md): `http://server:port/admin/permissions` - `knora-admin:Permission`

All information regarding users, projects, groups, lists and permissions is stored in the `http://www.knora.org/admin`
named graph.

Additionally there is the [stores endpoint](stores.md) which allows manipulation of the triplestore content.
