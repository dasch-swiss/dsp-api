<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Version

The version endpoint provides the versions of the used components in the Knora-stack.
The response has the type `application/json` and contains the following information:

1. name: has the value "version"

2. version numbers for the following components:
    - akkaHttp
    - gdbFree
    - gdbSE
    - sbt
    - scala
    - sipi
    - webapi


## Example request

`GET /version`


## Example response

```json
{
    "akkaHttp": "10.1.7",
    "gdbFree": "8.10.0-free",
    "gdbSE": "8.5.0-se",
    "name": "version",
    "sbt": "1.2.8",
    "scala": "2.12.8",
    "sipi": "v2.0.1",
    "webapi": "10.0.0-7-gc5a72b3-SNAPSHOT"
}
```
