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

# Starting the Knora Stack inside Docker Container

To run Knora locally, we provide `docker-compose.yml` which can be used to start GraphDB, Sipi,
Webapi running each in its own Docker container.

For GraphDB it is additionally necessary to define two environment variables:

``` 
$ export KNORA_GDB_LICENSE # full path to the GraphDB-SE license file, e.g., /Users/name/GDB/GDB.license
$ export KNORA_GDB_HOME # full path to a local folder where GraphDB should store it's data, e.g., /users/name/GDB/home
```

Per default, GraphDB-SE is started. If GraphDB-Free is needed, because there is no awailable license,
then a third environment variable can be set to something like:

```
$ export KNORA_GDB_IMAGE=dhlabbasel/graphdb-free:8.3.1 
```

To run the whole stack:

```
$ docker-compose up
```

For additional information please see the [Docker Compose documentation](https://docs.docker.com/compose/)