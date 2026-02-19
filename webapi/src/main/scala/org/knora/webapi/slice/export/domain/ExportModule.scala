/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService

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
    ProjectMigrationExportService &
    ProjectMigrationImportService
    // format: on

  val layer: URLayer[Dependencies, Provided] =
    ProjectMigrationStorageService.layer >>>
      ProjectMigrationExportService.layer >+>
      ProjectMigrationImportService.layer
}
