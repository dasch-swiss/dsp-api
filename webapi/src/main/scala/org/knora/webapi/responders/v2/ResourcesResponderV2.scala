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
import org.knora.webapi.{IRI, NotFoundException}
import org.knora.webapi.messages.v1.responder.standoffmessages.{GetMappingRequestV1, GetMappingResponseV1, GetXSLTransformationRequestV1, GetXSLTransformationResponseV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}

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

            // separate resources and values
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = resourceRequestResponse, userProfile = userProfile)

            // check if the requested resource was returned
            _ = if (queryResultsSeparated.get(resourceIri).isEmpty) {
                throw NotFoundException(s"The requested resource $resourceIri could not be found: maybe you do not have the right to see it or it is marked as deleted.")
            }

            // collect the Iris of the mappings referred to in text values
            mappingIris: Set[IRI] = ConstructResponseUtilV2.getMappingIrisFromValuePropertyAssertions(queryResultsSeparated(resourceIri).valuePropertyAssertions)

            // get all the mappings
            mappingResponsesFuture: Vector[Future[GetMappingResponseV1]] = mappingIris.map {
                mappingIri =>

                    for {
                        mappingResponse: GetMappingResponseV1 <- (responderManager ? GetMappingRequestV1(mappingIri = mappingIris.head, userProfile = userProfile)).mapTo[GetMappingResponseV1]
                    } yield mappingResponse
            }.toVector

            mappingResponses: Vector[GetMappingResponseV1] <- Future.sequence(mappingResponsesFuture)

            // get the default XSL transformations
            mappingsWithFuture: Vector[Future[(IRI, MappingAndXSLTransformation)]] = mappingResponses.map {
                (mapping: GetMappingResponseV1) =>

                    for {
                    // if given, get the default XSL Transformation
                        xsltOption: Option[String] <- if (mapping.mapping.defaultXSLTransformation.nonEmpty) {
                            for {
                                xslTransformation: GetXSLTransformationResponseV1 <- (responderManager ? GetXSLTransformationRequestV1(mapping.mapping.defaultXSLTransformation.get, userProfile = userProfile)).mapTo[GetXSLTransformationResponseV1]
                            } yield Some(xslTransformation.xslt)
                        } else {
                            Future(None)
                        }
                    } yield mapping.mappingIri -> MappingAndXSLTransformation(mapping = mapping.mapping, standoffEntities = mapping.standoffEntities, XSLTransformation = xsltOption)

            }

            mappings: Vector[(IRI, MappingAndXSLTransformation)] <- Future.sequence(mappingsWithFuture)

            // TODO: possibly more than one resource was requested!
        }  yield ReadResourcesSequenceV2(numberOfResources = 1, resources = Vector(ConstructResponseUtilV2.createFullResourceResponse(resourceIri, queryResultsSeparated(resourceIri), mappings = mappings.toMap, settings)), settings = settings)

    }

}

