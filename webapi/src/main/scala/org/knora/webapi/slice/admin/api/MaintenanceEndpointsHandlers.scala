/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZIO
import zio.ZLayer
import zio.json.*
import zio.json.ast.Json

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.domain.model.User

final case class MaintenanceServerEndpoints(
  private val endpoints: MaintenanceEndpoints,
  private val restService: MaintenanceRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.postMaintenance.serverLogic((user: User) => { case (action: String, jsonMaybe: Option[String]) =>
      ZIO
        .foreach(jsonMaybe)(str => ZIO.fromEither(str.fromJson[Json]).orElseFail(BadRequestException("Invalid JSON")))
        .flatMap(restService.executeMaintenanceAction(user, action, _))
    }),
  )
}

object MaintenanceServerEndpoints {
  val layer = ZLayer.derive[MaintenanceServerEndpoints]
}
