<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Starting the Knora Stack inside Docker Container

To run Knora locally, we provide `docker-compose.yml` which can be used to start Fuseki, Sipi,
Webapi running each in its own Docker container.

To run the whole stack:

```
$ make stack-up
```

For additional information please see the [Docker Compose documentation](https://docs.docker.com/compose/)
