<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Monitoring Knora

Monitoring is implemented by using the Prometheus / Grafana stack.

## Usage:

1)  Start webapi with the necessary -p option (e.g., from inside sbt:
    run -p or reStart -p
2)  Start the monitoring stack by executing the following line inside
    the monitoring
    folder:

<!-- end list -->

    $ WEBAPIHOST=<YourLocalIP> ADMIN_USER=admin ADMIN_PASSWORD=admin docker-compose up -d

3)  Head over to localhost:3000, log in using the admin username and
    password, and open the "Webapi Akka Actor System" dashboard.
4)  To shut down the monitoring stack, run the following line inside the
    monitoring folder:

<!-- end list -->

    $ docker-compose down
