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
License along with Knora.  If not, see <http://www.gnu.org/licenses/.
-->

# Admin Endpoint

For the management of *users*, *projects*, and *groups*, the Knora API
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

