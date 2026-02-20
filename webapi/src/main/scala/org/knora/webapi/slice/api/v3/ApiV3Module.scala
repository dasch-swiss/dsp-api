/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.*
import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.`export`.domain.ProjectMigrationExportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationImportService
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectIri
import org.knora.webapi.slice.api.v3.`export`.ExportServerEndpoints
import org.knora.webapi.slice.api.v3.export_.ExportService
import org.knora.webapi.slice.api.v3.ontology.OntologyRestServiceV3
import org.knora.webapi.slice.api.v3.projects.*
import org.knora.webapi.slice.api.v3.resources.ResourcesEndpointsV3
import org.knora.webapi.slice.api.v3.resources.ResourcesRestServiceV3
import org.knora.webapi.slice.api.v3.resources.ResourcesServerEndpointsV3
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.security.Authenticator

object ApiV3Module {

  type Dependencies =
    // format: off
    Authenticator &
    AuthorizationRestService &
    ExportService &
    IriConverter &
    KnoraProjectService &
    OntologyRepo &
    ProjectMigrationExportService &
    ProjectMigrationImportService &
    ResourcesRepo &
    StringFormatter
    // format: on

  type Provided = ApiV3ServerEndpoints

  val layer: URLayer[Dependencies, ApiV3ServerEndpoints] =
    V3BaseEndpoint.layer >+>
      V3Authorizer.layer >+>
      OntologyRestServiceV3.layer >+>
      ExportServerEndpoints.layer >+>
      ResourcesEndpointsV3.layer >+>
      V3ProjectsEndpoints.layer >+>
      ResourcesRestServiceV3.layer >+>
      V3ProjectsRestService.layer >+>
      ResourcesServerEndpointsV3.layer >+>
      V3ProjectsServerEndpoints.layer >>>
      ApiV3ServerEndpoints.layer
}

object ApiV3 {
  val basePath                                                     = "v3"
  val V3ProjectsProjectIri: EndpointInput[KnoraProject.ProjectIri] = ApiV3.basePath / "projects" / projectIri
}
