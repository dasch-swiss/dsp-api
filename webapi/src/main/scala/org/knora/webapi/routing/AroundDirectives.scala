/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.knora.webapi.instrumentation.InstrumentationSupport

/**
 * Akka HTTP directives which can be wrapped around a [[akka.http.scaladsl.server.Route]]].
 */
trait AroundDirectives extends InstrumentationSupport {

  /**
   * When wrapped around a [[akka.http.scaladsl.server.Route]], logs the time it took for the route to run.
   */
  def logDuration: Directive0 = extractRequestContext.flatMap { ctx =>
    val start = System.currentTimeMillis()
    mapResponse { resp =>
      val took = System.currentTimeMillis() - start
      metricsLogger.info(
        s"[${resp.status.intValue()}] ${ctx.request.method.name} " + s"${ctx.request.uri} took: ${took}ms"
      )
      resp
    }
  }
}
