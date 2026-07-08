/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import zio.*

import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final class OntologyMappingServerEndpoints(
  endpoints: OntologyMappingEndpoints,
  restService: OntologyMappingRestService,
) {
  val serverEndpoints: List[EndpointT] = List(
    endpoints.putClassMapping.serverLogic(restService.putClassMapping),
    endpoints.deleteClassMapping.serverLogic(restService.deleteClassMapping),
    endpoints.putPropertyMapping.serverLogic(restService.putPropertyMapping),
    endpoints.deletePropertyMapping.serverLogic(restService.deletePropertyMapping),
  )
}

object OntologyMappingServerEndpoints {
  val layer = ZLayer.derive[OntologyMappingServerEndpoints]
}
