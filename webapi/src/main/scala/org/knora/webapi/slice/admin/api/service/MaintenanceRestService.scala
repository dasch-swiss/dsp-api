/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ProjectsWithBakfilesReport
import org.knora.webapi.slice.admin.domain.service.MaintenanceService
import org.knora.webapi.slice.common.api.RestPermissionService

final case class MaintenanceRestService(
  securityService: RestPermissionService,
  maintenanceService: MaintenanceService
) {

  private val fixTopLeftAction = "fix-top-left"
  def executeMaintenanceAction(user: UserADM, action: String, jsonMaybe: Option[Json]): Task[Unit] =
    securityService.ensureSystemAdmin(user) *> {
      action match {
        case `fixTopLeftAction` => executeTopLeftAction(jsonMaybe)
        case _                  => ZIO.fail(BadRequestException(s"Unknown action $action"))
      }
    }

  private def getParamsAs[A](paramsMaybe: Option[Json], actionName: String)(implicit
    a: JsonDecoder[A]
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
}
