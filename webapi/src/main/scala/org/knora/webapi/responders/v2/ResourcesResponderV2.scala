/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2

import akka.pattern._
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.standoffmessages.{GetMappingRequestV1, GetMappingResponseV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValues}

import scala.concurrent.Future

class ResourcesResponderV2 extends Responder {

    def receive = {
        case resourcesGetRequest: ResourcesGetRequestV2 => future2Message(sender(), getResources(resourcesGetRequest.resourceIris, resourcesGetRequest.userProfile), log)
    }

    private def getResources(resourceIris: Set[IRI], userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        // TODO: get all the resources: possibly more than one resource is requested
        val resourceIri = resourceIris.head

        for {
            resourceRequestSparql <- Future(queries.sparql.v2.txt.getResourcePropertiesAndValues(
                triplestore = settings.triplestoreType,
                resourceIri = resourceIri
            ).toString())

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(resourceRequestSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ResourceWithValues] = ConstructResponseUtilV2.splitResourcesAndValueObjects(constructQueryResults = resourceRequestResponse)

            // collect the Iris of the mappings
            mappingIris: Set[IRI] = ConstructResponseUtilV2.getMappingIrisFromValuePropertyAssertions(queryResultsSeparated(resourceIri).valuePropertyAssertions)

            // get all the mappings
            mappingResponsesFuture: Vector[Future[GetMappingResponseV1]] = mappingIris.map {
                mappingIri =>

                    for {
                        mappingResponse: GetMappingResponseV1 <- (responderManager ? GetMappingRequestV1(mappingIri = mappingIris.head, userProfile = userProfile)).mapTo[GetMappingResponseV1]
                    } yield mappingResponse
            }.toVector

            mappingResponses: Vector[GetMappingResponseV1] <- Future.sequence(mappingResponsesFuture)

            mappings: Map[IRI, MappingAndXSLTransformation] = mappingResponses.map {
                (mapping: GetMappingResponseV1) =>

                    // if given, get the default XSL Transformation
                    

                    mapping.mappingIri -> MappingAndXSLTransformation(mapping = mapping.mapping, standoffEntities = mapping.standoffEntities, XSLTransformation = None)

            }.toMap

        }  yield ReadResourcesSequenceV2(numberOfResources = resourceIris.size, resources = Vector(ConstructResponseUtilV2.createFullResourceResponse(resourceIri, mappings = mappings, queryResultsSeparated)))

    }

}

