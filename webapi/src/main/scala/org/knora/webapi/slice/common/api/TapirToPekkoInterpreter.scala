/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.apache.pekko.http.scaladsl.server.Route
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.core.ActorSystem

final case class TapirToPekkoInterpreter()(actorSystem: ActorSystem) {
  implicit val executionContext: ExecutionContext = actorSystem.system.dispatcher
  private case class GenericErrorResponse(error: String)
  private object GenericErrorResponse {
    implicit val codec: JsonCodec[GenericErrorResponse] = DeriveJsonCodec.gen[GenericErrorResponse]
  }

  private def customizedErrorResponse(m: String): ValuedEndpointOutput[?] =
    ValuedEndpointOutput(jsonBody[GenericErrorResponse], GenericErrorResponse(m))

  private val serverOptions =
    PekkoHttpServerOptions.customiseInterceptors
      .defaultHandlers(customizedErrorResponse)
      .metricsInterceptor(ZioMetrics.default[Future]().metricsInterceptor())
      .notAcceptableInterceptor(None)
      .options

  private val interpreter: PekkoHttpServerInterpreter = PekkoHttpServerInterpreter(serverOptions)

  def toRoute(endpoint: ServerEndpoint[PekkoStreams & WebSockets, Future]): Route =
    interpreter.toRoute(endpoint)
}

object TapirToPekkoInterpreter {
  val layer = ZLayer.derive[TapirToPekkoInterpreter]
}
