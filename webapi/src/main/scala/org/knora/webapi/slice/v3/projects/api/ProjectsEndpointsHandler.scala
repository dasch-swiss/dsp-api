/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api

import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint.Full
import zio.Task
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.v3.projects.api.model.V3ErrorResponse
import org.knora.webapi.slice.v3.projects.api.model.V3ProjectException
import org.knora.webapi.slice.v3.projects.api.service.ProjectsRestService

/**
 * Custom endpoint handler for V3 endpoints that handles V3ErrorResponse instead of RequestRejectedException.
 */
case class V3PublicEndpointHandler[INPUT, OUTPUT](
  endpoint: Endpoint[Unit, INPUT, V3ErrorResponse, OUTPUT, PekkoStreams],
  handler: INPUT => Task[OUTPUT],
)

final case class ProjectsEndpointsHandler(
  projectsEndpoints: ProjectsEndpoints,
  restService: ProjectsRestService,
  mapper: HandlerMapper,
)(implicit r: zio.Runtime[Any]) {

  def mapV3PublicEndpointHandler[INPUT, OUTPUT](
    handlerAndEndpoint: V3PublicEndpointHandler[INPUT, OUTPUT],
  ): Full[Unit, Unit, INPUT, V3ErrorResponse, OUTPUT, PekkoStreams, Future] =
    handlerAndEndpoint.endpoint.serverLogic[Future](in => runV3ToFuture(handlerAndEndpoint.handler(in)))

  def runV3ToFuture[OUTPUT](zio: Task[OUTPUT]): Future[Either[V3ErrorResponse, OUTPUT]] =
    UnsafeZioRun.runToFuture(
      zio.refineOrDie { case e: V3ProjectException =>
        mapHttpStatusToErrorResponse(e.toV3ErrorResponse("generated-request-id"))
      }.either,
    )

  private def mapHttpStatusToErrorResponse(errorResponse: V3ErrorResponse): V3ErrorResponse =
    // The oneOf in Tapir will automatically route to the correct status code variant
    // based on the HTTP status code returned by the server logic
    errorResponse

  val getProjectByIdHandler =
    V3PublicEndpointHandler(projectsEndpoints.Public.getProjectById, restService.findProjectByShortcode)

  val getResourceCountsHandler =
    V3PublicEndpointHandler(projectsEndpoints.Public.getResourceCounts, restService.findResourceCountsByShortcode)

  private val handlers =
    List(
      mapV3PublicEndpointHandler(getProjectByIdHandler),
      mapV3PublicEndpointHandler(getResourceCountsHandler),
    )

  val allHandlers = handlers
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.fromZIO(
    for {
      endpoints   <- ZIO.service[ProjectsEndpoints]
      restService <- ZIO.service[ProjectsRestService]
      mapper      <- ZIO.service[HandlerMapper]
      r           <- ZIO.runtime[Any]
    } yield ProjectsEndpointsHandler(endpoints, restService, mapper)(r),
  )
}
