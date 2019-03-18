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

# Reading the User's Permissions on Resources and Values

In the @ref:[complex API schema](introduction.md#api-schema), each
resource and value is returned with the predicate `knora-api:userHasPermission`.
The object of this predicate is a string containing a permission code, which
indicates the requesting user's maximum permission on the resource or value.
These are the possible permission codes, in ascending order:

- `RV`: restricted view permission (least privileged)
- `V`: view permission
- `M` modify permission
- `D`: delete permission
- `CR`: change rights permission (most privileged)

Each permission implies all lesser permissions. For more details, see
@ref:[Permissions](../../02-knora-ontologies/knora-base.md#permissions).
