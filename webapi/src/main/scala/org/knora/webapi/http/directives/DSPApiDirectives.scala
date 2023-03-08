/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.directives

import akka.actor.ActorSystem
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.RejectionHandler
import ch.megard.akka.http.cors.scaladsl.CorsDirectives

import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.KnoraExceptionHandler

/**
 * DSP-API HTTP directives, used by wrapping around a routes, to influence
 * rejections and exception handling
 */
object DSPApiDirectives {

  // Our rejection handler. Here we are using the default one from the CORS lib
  def rejectionHandler: RejectionHandler = CorsDirectives.corsRejectionHandler.withFallback(RejectionHandler.default)

  // Our exception handler
  def exceptionHandler(system: ActorSystem, appConfig: AppConfig): ExceptionHandler = KnoraExceptionHandler(appConfig)

  // Combining the two handlers for convenience
  def handleErrors(system: ActorSystem, appConfig: AppConfig): server.Directive[Unit] =
    handleRejections(rejectionHandler) & handleExceptions(exceptionHandler(system, appConfig))
}
