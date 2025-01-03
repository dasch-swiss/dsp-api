/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.ActionName.{ImportProjectsToDb, UpdateAssetMetadata}
import swiss.dasch.domain.*
import zio.{ZIO, ZLayer}

final case class MaintenanceEndpointsHandler(
  fileChecksumService: FileChecksumService,
  imageService: StillImageService,
  maintenanceActions: MaintenanceActions,
  maintenanceEndpoints: MaintenanceEndpoints,
  projectService: ProjectService,
  sipiClient: SipiClient,
  authorizationHandler: AuthorizationHandler,
) extends HandlerFunctions {

  private val postMaintenanceEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.postMaintenanceActionEndpoint
      .serverLogic(userSession => { case (action, shortcodes) =>
        for {
          _ <- authorizationHandler.ensureAdminScope(userSession)
          paths <-
            ZIO
              .ifZIO(ZIO.succeed(shortcodes.isEmpty))(
                projectService.listAllProjects(),
                projectService.findProjects(shortcodes),
              )
              .mapError(ApiProblem.InternalServerError(_))
          _ <- ZIO.logDebug(s"Maintenance endpoint called $action, $shortcodes, $paths")
          _ <- action match {
                 case UpdateAssetMetadata => maintenanceActions.updateAssetMetadata(paths).forkDaemon.logError
                 case ImportProjectsToDb  => maintenanceActions.importProjectsToDb().forkDaemon.logError
               }
        } yield s"work in progress for projects ${paths.map(_.shortcode).mkString(", ")} (for details see logs)"
      })

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(postMaintenanceEndpoint)
}

object MaintenanceEndpointsHandler {
  val layer = ZLayer.derive[MaintenanceEndpointsHandler]
}
