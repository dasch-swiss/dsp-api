/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.ontology.OntologyTransformer
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ExportModule {
  type Dependencies =
    // format: off
    DspIngestClient &
    KnoraGroupService &
    KnoraProjectService &
    KnoraUserService &
    OntologyCache &
    OntologyTransformer &
    PermissionsResponder &
    TriplestoreService
    // format: on

  type Provided =
    // format: off
    ProjectDataImportService &
    ProjectMigrationExportService &
    ProjectMigrationImportService
    // format: on

  val layer: URLayer[Dependencies, Provided] =
    (ProjectMigrationStorageService.layer ++ ProjectMigrationImportValidator.layer) >>>
      (ProjectMigrationExportService.layer ++ ProjectMigrationImportService.layer ++ ProjectDataImportService.layer)
}
