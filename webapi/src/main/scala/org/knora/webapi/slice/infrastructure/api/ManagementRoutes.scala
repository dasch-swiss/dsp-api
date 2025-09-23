/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.model.StatusCode
import zio.*

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ManagementRoutes(
  private val endpoint: ManagementEndpoints,
  private val restService: ManagementRestService,
  private val mapper: HandlerMapper,
  private val tapirToPekko: TapirToPekkoInterpreter,
) {

  val routes = (
    List(
      PublicEndpointHandler(endpoint.getVersion, _ => ZIO.succeed(VersionResponse.current)),
      PublicEndpointHandler(endpoint.getHealth, _ => restService.healthCheck),
    ).map(mapper.mapPublicEndpointHandler)
      ++
        List(SecuredEndpointHandler(endpoint.postStartCompaction, restService.startCompaction))
          .map(mapper.mapSecuredEndpointHandler)
  ).map(tapirToPekko.toRoute)
}
object ManagementRoutes {
  val layer = zio.ZLayer.derive[ManagementRoutes]
}
