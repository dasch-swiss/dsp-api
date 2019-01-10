/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import org.knora.webapi.messages.app.appmessages._

/**
  * Starts Knora by bringing everything into scope by using the cake pattern. The [[LiveCore]] trait provides
  * an actor system, which is used by methods defined in the [[KnoraService]] trait, which itself provides
  * three methods: ''checkActorSystem'', ''startService'', and ''stopService''.
  */
object Main extends App with LiveCore with KnoraService {

    val arglist = args.toList

    // loads demo data
    if (arglist.contains("loadDemoData")) applicationStateActor ! SetLoadDemoDataState(true)
    if (arglist.contains("--load-demo-data")) applicationStateActor ! SetLoadDemoDataState(true)
    if (arglist.contains("-d")) applicationStateActor ! SetLoadDemoDataState(true)

    // allows reloading of data over HTTP
    if (arglist.contains("allowReloadOverHTTP")) applicationStateActor ! SetAllowReloadOverHTTPState(true)
    if (arglist.contains("--allow-reload-over-http")) applicationStateActor ! SetAllowReloadOverHTTPState(true)
    if (arglist.contains("-r")) applicationStateActor ! SetAllowReloadOverHTTPState(true)

    // starts prometheus monitoring reporter
    // if (arglist.contains("-p")) applicationStateActor ! SetPrometheusReporterState(true)

    // starts zipkin monitoring reporter
    // if (arglist.contains("-z")) applicationStateActor ! SetZipkinReporterState(true)

    // starts zipkin monitoring reporter
    // if (arglist.contains("-j")) applicationStateActor ! SetZipkinReporterState(true)

    // print config on startup
    // if (arglist.contains("-c")) applicationStateActor ! SetPrintConfigExtendedState(true)

    if (arglist.contains("--help")) {
        println(
            """
              | Usage: org.knora.webapi.Main <options>
              |    or  org.knora.webapi.Main -help
              |
              | Options:
              |
              |     allowReloadOverHTTP,
              |     --allow-reload-over-http,
              |     -r                          Allows reloading of data over HTTP.
              |
              |     -p                          Starts the Prometheus monitoring reporter.
              |
              |     -z                          Starts the Zipkin monitoring reporter.
              |
              |     -j                          Starts the Jaeger monitoring reporter.
              |
              |     -c                          Print the configuration on startup.
              |
              |     --help                      Shows this message.
            """.stripMargin)
    } else {
        /* Start the HTTP layer, allowing access */
        startService(true)

        /* add the method for shutting down our application to the shutdown hook, so that we can clean up */
        scala.sys.addShutdownHook(stopService())
    }
}