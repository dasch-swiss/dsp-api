/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.admin.api.AdminPathVariables.projectIri
import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.V3BaseEndpoint

final case class ResourcesEndpoints(baseEndpoint: V3BaseEndpoint) {

  val getResourcesResourcesPerOntology = baseEndpoint.withUserEndpoint.get
    .in(ApiV3.basePath / projectIri / "resourcesPerOntology")
    .out(jsonBody[List[OntologyAndResourceClasses]])

}

object ResourcesEndpoints {
  val layer = ZLayer.derive[ResourcesEndpoints]
}
