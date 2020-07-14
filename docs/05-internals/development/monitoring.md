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
