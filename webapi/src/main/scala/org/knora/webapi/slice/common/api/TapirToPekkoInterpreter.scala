/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.Task
import zio.ZLayer
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
        CORSInterceptor.customOrThrow(CORSConfig.default.copy(allowedOrigin = AllowedOrigin.All).exposeAllHeaders),
      )
      .notAcceptableInterceptor(None)
      .options

  val interpreter = ZioHttpInterpreter(serverOptions)
}

object TapirToPekkoInterpreter {
  val layer = ZLayer.derive[TapirToPekkoInterpreter]
}
