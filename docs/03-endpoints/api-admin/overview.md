<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Admin Endpoint

For the management of *users*, *projects*, and *groups*, the DSP-API
following a resource centric approach, provides three endpoints
corresponding to the three classes of objects that they have an effect
on, namely:

  - Users Endpoint: `http://server:port/admin/users` - `knora-base:User`
  - Projects Endpoint: `http://server:port/admin/projects` -
    `knora-base:knoraProject`
  - Groups Endpoint: `http://server:port/admin/groups` -
    `knora-base:UserGroup`

All information regarding users, projects and groups is stored in the
`http://www.knora.org/admin` named graph.

