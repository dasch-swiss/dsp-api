/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.ActionName.{ApplyTopLeftCorrection, ImportProjectsToDb, UpdateAssetMetadata}
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
                 case UpdateAssetMetadata    => maintenanceActions.updateAssetMetadata(paths).forkDaemon.logError
                 case ApplyTopLeftCorrection => maintenanceActions.applyTopLeftCorrections(paths).forkDaemon.logError
                 case ImportProjectsToDb     => maintenanceActions.importProjectsToDb().forkDaemon.logError
               }
        } yield s"work in progress for projects ${paths.map(_.shortcode).mkString(", ")} (for details see logs)"
      })

  val needsOriginalsEndpoint: ZServerEndpoint[Any, Any] = maintenanceEndpoints.needsOriginalsEndpoint
    .serverLogic(userSession =>
      imagesOnlyMaybe =>
        authorizationHandler.ensureAdminScope(userSession) *>
          maintenanceActions
            .createNeedsOriginalsReport(imagesOnlyMaybe.getOrElse(true))
            .forkDaemon
            .logError
            .as("work in progress"),
    )

  val needsTopLeftCorrectionEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.needsTopLeftCorrectionEndpoint
      .serverLogic(userSession =>
        _ =>
          authorizationHandler.ensureAdminScope(userSession) *>
            maintenanceActions
              .createNeedsTopLeftCorrectionReport()
              .forkDaemon
              .logError
              .as("work in progress"),
      )

  val wasTopLeftCorrectionAppliedEndpoint: ZServerEndpoint[Any, Any] =
    maintenanceEndpoints.wasTopLeftCorrectionAppliedEndpoint
      .serverLogic(userSession =>
        _ =>
          authorizationHandler.ensureAdminScope(userSession) *>
            maintenanceActions
              .createWasTopLeftCorrectionAppliedReport()
              .forkDaemon
              .logError
              .as("work in progress"),
      )

  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(
      postMaintenanceEndpoint,
      needsOriginalsEndpoint,
      needsTopLeftCorrectionEndpoint,
      wasTopLeftCorrectionAppliedEndpoint,
    )
}

object MaintenanceEndpointsHandler {
  val layer = ZLayer.derive[MaintenanceEndpointsHandler]
}
