package org.knora.webapi.slice.admin.api.service

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.ProjectsWithBakfilesReport
import org.knora.webapi.slice.admin.domain.service.MaintenanceService
import org.knora.webapi.slice.common.api.RestPermissionService
import zio.{IO, Task, ZIO, ZLayer}
import zio.json.JsonDecoder
import zio.json.ast.Json

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

  private def executeTopLeftAction(topLeftParams: Option[Json]): IO[BadRequestException, Unit] =
    for {
      json <- ZIO
                .fromOption(topLeftParams)
                .orElseFail(BadRequestException(s"Missing arguments for $fixTopLeftAction"))
      report <- ZIO
                  .fromEither(JsonDecoder[ProjectsWithBakfilesReport].fromJsonAST(json))
                  .mapError(e => BadRequestException(s"Invalid arguments for $fixTopLeftAction: $e.getMessage"))
      _ <- maintenanceService.fixTopLeftDimensions(report).logError.forkDaemon
    } yield ()
}

object MaintenanceRestService {
  val layer = ZLayer.derive[MaintenanceRestService]
}
