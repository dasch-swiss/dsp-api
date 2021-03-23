<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP.  If not, see <http://www.gnu.org/licenses/>.
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
```
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