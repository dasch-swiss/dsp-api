/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import dsp.errors.BadRequestException
import zio.ZLayer
import zio.ZIO
import zio.json.ast.Json
import zio.json.*
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class MaintenanceEndpointsHandlers(
  endpoints: MaintenanceEndpoints,
  restService: MaintenanceRestService,
  mapper: HandlerMapper,
) {

  private val postMaintenanceHandler =
    SecuredEndpointHandler[(String, Option[String]), Unit](
      endpoints.postMaintenance,
      (user: User) => { case (action: String, jsonMaybe: Option[String]) =>
        ZIO
          .foreach(jsonMaybe)(str => ZIO.fromEither(str.fromJson[Json]).orElseFail(BadRequestException("Invalid JSON")))
          .flatMap(restService.executeMaintenanceAction(user, action, _))
      },
    )

  val allHandlers = List(postMaintenanceHandler).map(mapper.mapSecuredEndpointHandler(_))
}

object MaintenanceEndpointsHandlers {
  val layer = ZLayer.derive[MaintenanceEndpointsHandlers]
}
