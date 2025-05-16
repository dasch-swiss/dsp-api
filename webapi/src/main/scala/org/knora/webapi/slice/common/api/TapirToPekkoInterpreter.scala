/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.model.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSConfig.*
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.ztapir.*
import zio.Task
import zio.ZLayer
import zio.http.Response
import zio.http.Routes
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

final case class TapirToPekkoInterpreter() {

  private case class GenericErrorResponse(error: String)
  private object GenericErrorResponse {
    given JsonCodec[GenericErrorResponse] = DeriveJsonCodec.gen[GenericErrorResponse]
  }

  private def customizedErrorResponse(m: String): ValuedEndpointOutput[?] =
    ValuedEndpointOutput(jsonBody[GenericErrorResponse], GenericErrorResponse(m))

  private val serverOptions =
    ZioHttpServerOptions.customiseInterceptors
      .defaultHandlers(customizedErrorResponse)
      .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
      .corsInterceptor(
        CORSInterceptor.customOrThrow(
          CORSConfig.default
            .copy(
              allowedOrigin = AllowedOrigin.Matching(_ => true),
              allowedMethods = AllowedMethods.Some(
                Set(Method.GET, Method.PUT, Method.POST, Method.DELETE, Method.PATCH, Method.HEAD, Method.OPTIONS),
              ),
              allowedCredentials = AllowedCredentials.Allow,
            )
            .exposeAllHeaders,
        ),
      )
      .notAcceptableInterceptor(None)
      .options

  def toHttp[R2](ses: List[ZServerEndpoint[R2, ZioStreams with WebSockets]]): Routes[R2, Response] =
    ZioHttpInterpreter(serverOptions).toHttp(ses)
}

object TapirToPekkoInterpreter {
  val layer = ZLayer.derive[TapirToPekkoInterpreter]
}
