/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.json.JsonDecoder
import zio.json.ast.Json

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ProjectsWithBakfilesReport
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService.fixTopLeftAction
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class MaintenanceRestService(
  securityService: AuthorizationRestService,
  maintenanceService: MaintenanceService,
) {

  def executeMaintenanceAction(user: User, action: String, jsonMaybe: Option[Json]): Task[Unit] =
    securityService.ensureSystemAdmin(user) *> {
      action match {
        case `fixTopLeftAction` => executeTopLeftAction(jsonMaybe)
        case _                  => ZIO.fail(BadRequestException(s"Unknown action $action"))
      }
    }

  private def getParamsAs[A](paramsMaybe: Option[Json], actionName: String)(implicit
    a: JsonDecoder[A],
  ): IO[BadRequestException, A] = {
    val missingArgsMsg                 = s"Missing arguments for $actionName"
    def invalidArgsMsg(reason: String) = s"Invalid arguments for $actionName: $reason"
    for {
      json   <- ZIO.fromOption(paramsMaybe).orElseFail(BadRequestException(missingArgsMsg))
      parsed  = JsonDecoder[A].fromJsonAST(json)
      result <- ZIO.fromEither(parsed).mapError(e => BadRequestException(invalidArgsMsg(e)))
    } yield result
  }

  private def executeTopLeftAction(topLeftParams: Option[Json]): IO[BadRequestException, Unit] =
    for {
      report <- getParamsAs[ProjectsWithBakfilesReport](topLeftParams, fixTopLeftAction)
      _      <- maintenanceService.fixTopLeftDimensions(report).logError.forkDaemon
    } yield ()
}

object MaintenanceRestService {
  val layer = ZLayer.derive[MaintenanceRestService]

  val fixTopLeftAction = "fix-top-left"

  val allActions: List[String] = List(fixTopLeftAction)
}
