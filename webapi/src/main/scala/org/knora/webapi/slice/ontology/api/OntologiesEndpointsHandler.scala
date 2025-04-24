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
    SecuredEndpointHandler(endpoints.postOntologiesClasses, restService.createClass),
    SecuredEndpointHandler(endpoints.putOntologiesClasses, restService.changeClassLabelsOrComments),
    SecuredEndpointHandler(endpoints.deleteOntologiesClassesComment, restService.deleteClassComment),
    SecuredEndpointHandler(endpoints.postOntologiesCardinalities, restService.addCardinalities),
    SecuredEndpointHandler(endpoints.getOntologiesCanreplacecardinalities, restService.canChangeCardinality),
    SecuredEndpointHandler(endpoints.putOntologiesCardinalities, restService.replaceCardinalities),
    SecuredEndpointHandler(endpoints.postOntologiesCandeletecardinalities, restService.canDeleteCardinalitiesFromClass),
    SecuredEndpointHandler(endpoints.patchOntologiesCardinalities, restService.deleteCardinalitiesFromClass),
    SecuredEndpointHandler(endpoints.putOntologiesGuiorder, restService.changeGuiOrder),
    SecuredEndpointHandler(endpoints.getOntologiesClassesIris, restService.getClasses),
    SecuredEndpointHandler(endpoints.getOntologiesCandeleteclass, restService.canDeleteClass),
    SecuredEndpointHandler(endpoints.deleteOntologiesClasses, restService.deleteClass),
    SecuredEndpointHandler(endpoints.deleteOntologiesComment, restService.deleteOntologyComment),
    SecuredEndpointHandler(endpoints.postOntologiesProperties, restService.createProperty),
    SecuredEndpointHandler(endpoints.putOntologiesProperties, restService.changePropertyLabelsOrComments),
    SecuredEndpointHandler(endpoints.deletePropertiesComment, restService.deletePropertyComment),
    SecuredEndpointHandler(endpoints.putOntologiesPropertiesGuielement, restService.changePropertyGuiElement),
    SecuredEndpointHandler(endpoints.getOntologiesProperties, restService.getProperties),
    SecuredEndpointHandler(endpoints.getOntologiesCandeleteproperty, restService.canDeleteProperty),
    SecuredEndpointHandler(endpoints.deleteOntologiesProperty, restService.deleteProperty),
    SecuredEndpointHandler(endpoints.postOntologies, restService.createOntology),
    SecuredEndpointHandler(endpoints.getOntologiesCandeleteontology, restService.canDeleteOntology),
    SecuredEndpointHandler(endpoints.deleteOntologies, restService.deleteOntology),
  ).map(mapper.mapSecuredEndpointHandler(_))
}

object OntologiesEndpointsHandler {
  val layer = ZLayer.derive[OntologiesEndpointsHandler]
}
