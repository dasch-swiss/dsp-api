/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.SmartIri
import org.knora.webapi.util.search.gravsearch.GravsearchParser

import scala.concurrent.Future

/**
  * Handles requests to read and write Knora values.
  */
class ValuesResponderV2 extends Responder {
    override def receive: Receive = {
        case createValueRequest: CreateValueRequestV2 => future2Message(sender(), createValueV2(createValueRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Creates a new value in an existing resource.
      *
      * @param createValueRequest the request to create the value.
      * @return a [[CreateValueResponseV2]].
      */
    def createValueV2(createValueRequest: CreateValueRequestV2): Future[CreateValueResponseV2] = {
        def makeTaskFuture(userIri: IRI): Future[CreateValueResponseV2] = {
            for {
                // Get ontology information about the property and about its cardinality in the resource class.

                // Ensure that this is a value property.

                // Get the resource's metadata and the values, if any, that the resource already has for the property.

                resourceWithPropertyValues <- getResourceWithPropertyValues(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyIri = createValueRequest.createValue.propertyIri,
                    requestingUser = createValueRequest.requestingUser
                )

                // Check that the resource class's cardinality for the property allows another value to be added
                // for that property.

                // Check that the new value would not duplicate an existing value.

            } yield CreateValueResponseV2(valueIri = "")
        }

        for {
            // Don't allow anonymous users to create values.
            userIri <- Future {
                if (createValueRequest.requestingUser.isAnonymousUser) {
                    throw ForbiddenException("Anonymous users aren't allowed to create values")
                } else {
                    createValueRequest.requestingUser.id
                }
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                createValueRequest.apiRequestID,
                createValueRequest.createValue.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Returns a resource's metadata and its values for the specified property.
      *
      * @param resourceIri    the resource IRI.
      * @param propertyIri    the property IRI.
      * @param requestingUser the user making the request.
      * @return a [[ReadResourceV2]] containing only the resource's metadata and its values for the specified property.
      */
    private def getResourceWithPropertyValues(resourceIri: IRI, propertyIri: SmartIri, requestingUser: UserADM): Future[ReadResourceV2] = {
        // TODO: when text values in Gravsearch query results are shortened, make a way for this query to get the complete value.
        val gravsearchQuery =
            s"""
               |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
               |
               |CONSTRUCT {
               |  ?resource knora-api:isMainResource true .
               |  ?resource <$propertyIri> ?propertyValue .
               |} WHERE {
               |  BIND(<$resourceIri> AS ?resource)
               |  ?resource a knora-api:Resource .
               |  ?resource <$propertyIri> ?propertyValue .
               |}
            """.stripMargin

        for {
            parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
            searchResponse <- (responderManager ? GravsearchRequestV2(parsedGravsearchQuery, requestingUser)).mapTo[ReadResourcesSequenceV2]

            _ = if (searchResponse.numberOfResources == 0) {
                throw NotFoundException(s"Resource <$resourceIri> not found")
            }

            _ = if (searchResponse.numberOfResources > 1) {
                throw AssertionException(s"More than one resource returned with IRI <$resourceIri>")
            }
        } yield searchResponse.resources.head
    }
}
