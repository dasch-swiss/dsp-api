/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.*
import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.export_.ExportServerEndpoints
import org.knora.webapi.slice.api.v3.resources.ResourcesEndpointsV3
import org.knora.webapi.slice.api.v3.resources.ResourcesRestServiceV3
import org.knora.webapi.slice.api.v3.resources.ResourcesServerEndpointsV3
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.security.Authenticator

object ApiV3Module {

  type Dependencies =
    // format: off
    Authenticator &
    ExportServerEndpoints &
    KnoraProjectService &
    OntologyRepo &
    ResourcesRepo &
    StringFormatter
    // format: on

  type Provided = ApiV3ServerEndpoints

  val layer: URLayer[Dependencies, ApiV3ServerEndpoints] =
    ZLayer.makeSome[Dependencies, ApiV3ServerEndpoints](
      ApiV3ServerEndpoints.layer,
      ResourcesEndpointsV3.layer,
      ResourcesRestServiceV3.layer,
      ResourcesServerEndpointsV3.layer,
      V3BaseEndpoint.layer,
    )
}

object ApiV3 {
  val basePath                                                     = "v3"
  val V3ProjectsProjectIri: EndpointInput[KnoraProject.ProjectIri] = ApiV3.basePath / "projects" / projectIri
}
