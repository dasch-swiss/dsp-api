/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.admin.api.model.MaintenanceRequests._
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class MaintenanceService(
  knoraProjectService: KnoraProjectService,
  triplestoreService: TriplestoreService,
) {
  def fixTopLeftDimensions(report: ProjectsWithBakfilesReport): Task[Unit] =
    ZIO.logInfo(s"Starting fix top left maintenance") *>
      TopLeftCorrectionAction(knoraProjectService, triplestoreService).execute(report) *>
      ZIO.logInfo(s"Finished fix top left maintenance")
}

object MaintenanceService {
  val layer = ZLayer.derive[MaintenanceService]
}
