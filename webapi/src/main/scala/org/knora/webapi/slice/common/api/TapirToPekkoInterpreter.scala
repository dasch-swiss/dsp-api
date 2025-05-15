/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.Route
import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.admin.api.FilesServerEndpoints
import org.knora.webapi.slice.admin.api.GroupsServerEndpoints
import org.knora.webapi.slice.admin.api.ListsServerEndpoints
import org.knora.webapi.slice.admin.api.MaintenanceServerEndpoints
import org.knora.webapi.slice.admin.api.PermissionsServerEndpoints
import org.knora.webapi.slice.admin.api.ProjectsLegalInfoServerEndpoints
import org.knora.webapi.slice.admin.api.ProjectsServerEndpoints
import org.knora.webapi.slice.admin.api.StoreServerEndpoints
import org.knora.webapi.slice.admin.api.UsersServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.lists.api.ListsServerEndpointsV2
import org.knora.webapi.slice.ontology.api.OntologiesServerEndpoints
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoServerEndpoints
import org.knora.webapi.slice.resources.api.ResourcesApiServerEndpoints
import org.knora.webapi.slice.search.api.SearchServerEndpoints
import org.knora.webapi.slice.security.api.AuthenticationServerEndpointsV2
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.ztapir.ZServerEndpoint
import zio.Task
import zio.ZLayer
import zio.http.Response
import zio.http.Routes
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
