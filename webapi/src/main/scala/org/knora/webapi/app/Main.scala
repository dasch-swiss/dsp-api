/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Terminated
import org.knora.webapi.messages.app.appmessages.{AppStart, SetAllowReloadOverHTTPState, SetLoadDemoDataState}

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
      new Thread(() => {
        val terminate: Future[Terminated] = system.terminate()
        Await.result(terminate, 30.seconds)
      })
    )

    system.registerOnTermination {
      println("ActorSystem terminated")
    }
  }
}
