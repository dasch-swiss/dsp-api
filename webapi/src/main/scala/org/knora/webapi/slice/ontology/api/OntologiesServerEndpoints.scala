/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.ontology.api.service.OntologiesRestService

final class OntologiesServerEndpoints(
  private val endpoints: OntologiesEndpoints,
  private val restService: OntologiesRestService,
) {

  val serverEndpoints = Seq(
    endpoints.getOntologiesMetadataProject.zServerLogic(restService.getOntologyMetadataByProjectOption),
    endpoints.getOntologiesMetadataProject.zServerLogic(restService.getOntologyMetadataByProjects),
    endpoints.getOntologyPathSegments.serverLogic(restService.dereferenceOntologyIri),
    endpoints.putOntologiesMetadata.serverLogic(restService.changeOntologyMetadata),
    endpoints.getOntologiesAllentities.serverLogic(restService.getOntologyEntities),
    endpoints.postOntologiesClasses.serverLogic(restService.createClass),
    endpoints.putOntologiesClasses.serverLogic(restService.changeClassLabelsOrComments),
    endpoints.deleteOntologiesClassesComment.serverLogic(restService.deleteClassComment),
    endpoints.postOntologiesCardinalities.serverLogic(restService.addCardinalities),
    endpoints.getOntologiesCanreplacecardinalities.serverLogic(restService.canChangeCardinality),
    endpoints.putOntologiesCardinalities.serverLogic(restService.replaceCardinalities),
    endpoints.postOntologiesCandeletecardinalities.serverLogic(restService.canDeleteCardinalitiesFromClass),
    endpoints.patchOntologiesCardinalities.serverLogic(restService.deleteCardinalitiesFromClass),
    endpoints.putOntologiesGuiorder.serverLogic(restService.changeGuiOrder),
    endpoints.getOntologiesClassesIris.serverLogic(restService.getClasses),
    endpoints.getOntologiesCandeleteclass.serverLogic(restService.canDeleteClass),
    endpoints.deleteOntologiesClasses.serverLogic(restService.deleteClass),
    endpoints.deleteOntologiesComment.serverLogic(restService.deleteOntologyComment),
    endpoints.postOntologiesProperties.serverLogic(restService.createProperty),
    endpoints.putOntologiesProperties.serverLogic(restService.changePropertyLabelsOrComments),
    endpoints.deletePropertiesComment.serverLogic(restService.deletePropertyComment),
    endpoints.putOntologiesPropertiesGuielement.serverLogic(restService.changePropertyGuiElement),
    endpoints.getOntologiesProperties.serverLogic(restService.getProperties),
    endpoints.getOntologiesCandeleteproperty.serverLogic(restService.canDeleteProperty),
    endpoints.deleteOntologiesProperty.serverLogic(restService.deleteProperty),
    endpoints.postOntologies.serverLogic(restService.createOntology),
    endpoints.getOntologiesCandeleteontology.serverLogic(restService.canDeleteOntology),
    endpoints.deleteOntologies.serverLogic(restService.deleteOntology),
  )
}
object OntologiesServerEndpoints {
  val layer = ZLayer.derive[OntologiesServerEndpoints]
}
