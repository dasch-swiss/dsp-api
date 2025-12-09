/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.User

final class OntologiesServerEndpoints(endpoints: OntologiesEndpoints, restService: OntologiesRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    // GET
    endpoints.getOntologiesMetadataProject.zServerLogic(restService.getOntologyMetadataByProjectOption),
    endpoints.getOntologiesMetadataProjects.zServerLogic(restService.getOntologyMetadataByProjects),
    endpoints.getOntologyPathSegments.serverLogic(restService.dereferenceOntologyIri),
    endpoints.getOntologiesAllentities.serverLogic(restService.getOntologyEntities),
    endpoints.getOntologiesCanreplacecardinalities.serverLogic(restService.canChangeCardinality),
    endpoints.getOntologiesClassesIris.serverLogic(restService.findClassByIri),
    endpoints.getOntologiesCandeleteclass.serverLogic(restService.canDeleteClass),
    endpoints.getOntologiesProperties.serverLogic(restService.findPropertyByIri),
    endpoints.getOntologiesCandeleteproperty.serverLogic(restService.canDeleteProperty),
    endpoints.getOntologiesCandeleteontology.serverLogic(restService.canDeleteOntology),
    // DELETE
    endpoints.deleteOntologiesClassesComment.serverLogic(restService.deleteClassComment),
    endpoints.deleteOntologiesClasses.serverLogic(restService.deleteClass),
    endpoints.deleteOntologiesComment.serverLogic(restService.deleteOntologyComment),
    endpoints.deletePropertiesComment.serverLogic(restService.deletePropertyComment),
    endpoints.deleteOntologiesProperty.serverLogic(restService.deleteProperty),
    endpoints.deleteOntologies.serverLogic(restService.deleteOntology),
    // PATCH
    endpoints.patchOntologiesCardinalities.serverLogic(restService.deleteCardinalitiesFromClass),
    // POST
    endpoints.postOntologiesClasses.serverLogic(restService.createClass),
    endpoints.postOntologiesCardinalities.serverLogic(restService.addCardinalities),
    endpoints.postOntologiesCandeletecardinalities.serverLogic(restService.canDeleteCardinalitiesFromClass),
    endpoints.postOntologiesProperties.serverLogic(restService.createProperty),
    endpoints.postOntologies.serverLogic(restService.createOntology),
    // PUT
    endpoints.putOntologiesMetadata.serverLogic(restService.changeOntologyMetadata),
    endpoints.putOntologiesClasses.serverLogic(restService.changeClassLabelsOrComments),
    endpoints.putOntologiesCardinalities.serverLogic(restService.replaceCardinalities),
    endpoints.putOntologiesGuiorder.serverLogic(restService.changeGuiOrder),
    endpoints.putOntologiesProperties.serverLogic(restService.changePropertyLabelsOrComments),
    endpoints.putOntologiesPropertiesGuielement.serverLogic(restService.changePropertyGuiElement),
  )
}
object OntologiesServerEndpoints {
  private[ontologies] val layer = ZLayer.derive[OntologiesServerEndpoints]
}
