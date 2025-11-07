/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.V3BaseEndpoint

class ResourcesEndpointsV3(baseEndpoint: V3BaseEndpoint) {

  val getResourcesResourcesPerOntology = baseEndpoint.publicEndpoint.get
    .in(ApiV3.V3ProjectsProjectIri / "resourcesPerOntology")
    .out(jsonBody[List[OntologyAndResourceClasses]])

}

object ResourcesEndpointsV3 {
  val layer = ZLayer.derive[ResourcesEndpointsV3]
}
