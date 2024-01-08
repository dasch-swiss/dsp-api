/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.directives

import org.apache.pekko
import org.apache.pekko.http.cors.scaladsl.CorsDirectives

import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.KnoraExceptionHandler

import pekko.http.scaladsl.server
import pekko.http.scaladsl.server.Directives.handleExceptions
import pekko.http.scaladsl.server.Directives.handleRejections
import pekko.http.scaladsl.server.ExceptionHandler
import pekko.http.scaladsl.server.RejectionHandler

/**
 * DSP-API HTTP directives, used by wrapping around a routes, to influence
 * rejections and exception handling
 */
object DSPApiDirectives {

  // Our rejection handler. Here we are using the default one from the CORS lib
  def rejectionHandler: RejectionHandler = CorsDirectives.corsRejectionHandler.withFallback(RejectionHandler.default)

  // Our exception handler
  def exceptionHandler(appConfig: AppConfig): ExceptionHandler = KnoraExceptionHandler(appConfig)

  // Combining the two handlers for convenience
  def handleErrors(appConfig: AppConfig): server.Directive[Unit] =
    handleRejections(rejectionHandler) & handleExceptions(exceptionHandler(appConfig))
}
