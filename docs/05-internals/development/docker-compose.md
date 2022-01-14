<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
$ export KNORA_GDB_IMAGE=daschswiss/graphdb-free:8.3.1 
```

To run the whole stack:

```
$ docker-compose up
```

For additional information please see the [Docker Compose documentation](https://docs.docker.com/compose/)
