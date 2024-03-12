<!---
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Version

The version endpoint provides all versions of used components in the DSP stack.

## Example request

`GET /version`

## Example response

```json
HTTP/1.1 200 OK
Content-Length: 247
Content-Type: application/json
Date: Mon, 11 Mar 2024 17:40:32 GMT
Server: webapi/v30.9.0

{
    "buildCommit": "bbb0e65c7",
    "buildTime": "2024-03-11T17:40:17.322491Z",
    "fuseki": "2.1.5",
    "pekkoHttp": "1.0.1",
    "scala": "2.13.13",
    "sipi": "3.9.0",
    "webapi": "v30.9.0"
}

```
