/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.app

import org.knora.webapi.messages.app.appmessages.{AppStart, SetAllowReloadOverHTTPState, SetLoadDemoDataState}

import akka.actor.Terminated
// import zio.BootstrapRuntime

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Starts Knora by bringing everything into scope by using the cake pattern.
  * The [[LiveCore]] trait provides an actor system and the main application
  * actor.
  */
object Main extends App with LiveCore {

  val arglist = args.toList

  // loads demo data
  if (arglist.contains("loadDemoData")) appActor ! SetLoadDemoDataState(true)
  if (arglist.contains("--load-demo-data")) appActor ! SetLoadDemoDataState(true)
  if (arglist.contains("-d")) appActor ! SetLoadDemoDataState(true)

  // allows reloading of data over HTTP
  if (arglist.contains("allowReloadOverHTTP")) appActor ! SetAllowReloadOverHTTPState(true)
  if (arglist.contains("--allow-reload-over-http")) appActor ! SetAllowReloadOverHTTPState(true)
  if (arglist.contains("-r")) appActor ! SetAllowReloadOverHTTPState(true)

  if (arglist.contains("--help")) {
    println("""
              | Usage: org.knora.webapi.Main <options>
              |    or  org.knora.webapi.Main -help
              |
              | Options:
              |
              |     allowReloadOverHTTP,
              |     --allow-reload-over-http,
              |     -r                          Allows reloading of data over HTTP.
              |
              |     -c                          Print the configuration on startup.
              |
              |     --help                      Shows this message.
            """.stripMargin)
  } else {
    /* Start the HTTP layer, loading data from the repository */
    appActor ! AppStart(ignoreRepository = false, requiresIIIFService = true)

    /**
      * Adds shutting down of our actor system to the shutdown hook.
      * Because we are blocking, we will run this on a separate thread.
      */
    scala.sys.addShutdownHook(
      new Thread(
        () => {
          val terminate: Future[Terminated] = system.terminate()
          Await.result(terminate, 30.seconds)
        }
      )
    )

    system.registerOnTermination {
      println("ActorSystem terminated")
    }
  }
}

// object Runtime extends BootstrapRuntime
