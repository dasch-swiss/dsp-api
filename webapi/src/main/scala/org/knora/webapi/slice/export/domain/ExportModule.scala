package org.knora.webapi.slice.`export`.domain

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.URLayer
import zio.ZLayer

object ExportModule {
  type Dependencies =
    // format: off
    AppConfig &
    KnoraApi &
    KnoraProjectService &
    TriplestoreService
    // format: on

  type Provided =
    // format: off
    ProjectDataExportService &
    ProjectDataImportService
    // format: on

  val layer: URLayer[Dependencies, Provided] =
    ProjectDataExportStorage.layer >>> ProjectDataExportService.layer >+> ProjectDataImportService.layer
}
