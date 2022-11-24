<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Health

The health endpoint provides information about the health state of the dsp-stack.


## Example request
`GET /health`


## Example response
```
{
    "name":"AppState",
    "message" : "Application is healthy",
    "severity":"non fatal",
    "status":"healthy"
}
```
