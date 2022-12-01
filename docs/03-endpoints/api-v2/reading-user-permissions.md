<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Reading the User's Permissions on Resources and Values

In the [complex API schema](introduction.md#api-schema), each
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
[Permissions](../../02-dsp-ontologies/knora-base.md#permissions).
