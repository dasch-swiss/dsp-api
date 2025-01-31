/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import com.typesafe.scalalogging.Logger
import org.apache.pekko
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes.MethodNotAllowed
import org.apache.pekko.http.scaladsl.model.StatusCodes.NotFound
import org.slf4j.LoggerFactory

import pekko.http.scaladsl.server.Directive0
import pekko.http.scaladsl.server.Directives.*

/**
 * Pekko HTTP directives which can be wrapped around a [[pekko.http.scaladsl.server.Route]]].
 */
trait AroundDirectives {
  protected lazy val metricsLogger: Logger =
    Logger(LoggerFactory.getLogger(s"M-this.getClass.getSimpleName"))

  /**
   * When wrapped around a [[pekko.http.scaladsl.server.Route]], logs the time it took for the route to run.
   */
  def logDuration: Directive0 = extractRequestContext.flatMap { ctx =>
    val start = System.currentTimeMillis()
    mapResponse { resp =>
      val took    = System.currentTimeMillis() - start
      val message = s"[${resp.status.intValue()}] ${ctx.request.method.name} ${ctx.request.uri} took: ${took}ms"
      if (shouldLogWarning(resp)) metricsLogger.warn(message)
      else metricsLogger.debug(message)
      resp
    }
  }

  private val doNotLogStatusCodes = List(MethodNotAllowed, NotFound)
  private def shouldLogWarning(resp: HttpResponse) = {
    val status = resp.status
    status.isFailure() && !doNotLogStatusCodes.contains(status)
  }
}
