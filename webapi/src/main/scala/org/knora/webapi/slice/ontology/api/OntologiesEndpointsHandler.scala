/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api
import zio.ZLayer

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.ontology.api.service.OntologiesRestService

final class OntologiesEndpointsHandler(
  private val endpoints: OntologiesEndpoints,
  private val restService: OntologiesRestService,
  private val mapper: HandlerMapper,
) {

  val allHandlers = Seq(
    SecuredEndpointHandler(endpoints.postOntologies, restService.createOntology),
    SecuredEndpointHandler(endpoints.getOntologiesCandeleteontology, restService.canDeleteOntology),
    SecuredEndpointHandler(endpoints.deleteOntologies, restService.deleteOntology),
  ).map(mapper.mapSecuredEndpointHandler(_))
}

object OntologiesEndpointsHandler {
  val layer = ZLayer.derive[OntologiesEndpointsHandler]
}
