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

import akka.pattern._
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, GetXSLTransformationRequestV2, GetXSLTransformationResponseV2}
import org.knora.webapi.responders.ActorBasedResponder
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}

import scala.concurrent.Future

/**
  * An abstract class with standoff utility methods for v2 responders.
  */
abstract class ResponderWithStandoffV2 extends ActorBasedResponder {

    /**
      * Gets mappings referred to in query results [[Map[IRI, ResourceWithValueRdfData]]].
      *
      * @param queryResultsSeparated query results referring to mappings.
      * @param requestingUser        the user making the request.
      * @return the referred mappings.
      */
    protected def getMappingsFromQueryResultsSeparated(queryResultsSeparated: Map[IRI, ResourceWithValueRdfData], requestingUser: UserADM): Future[Map[IRI, MappingAndXSLTransformation]] = {

        // collect the Iris of the mappings referred to in the resources' text values
        val mappingIris: Set[IRI] = queryResultsSeparated.flatMap {
            case (_, assertions: ResourceWithValueRdfData) =>
                ConstructResponseUtilV2.getMappingIrisFromValuePropertyAssertions(assertions.valuePropertyAssertions)
        }.toSet

        // get all the mappings
        val mappingResponsesFuture: Vector[Future[GetMappingResponseV2]] = mappingIris.map {
            mappingIri: IRI =>
                for {
                    mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingIri, requestingUser = requestingUser)).mapTo[GetMappingResponseV2]
                } yield mappingResponse
        }.toVector

        for {
            mappingResponses: Vector[GetMappingResponseV2] <- Future.sequence(mappingResponsesFuture)

            // get the default XSL transformations
            mappingsWithFuture: Vector[Future[(IRI, MappingAndXSLTransformation)]] = mappingResponses.map {
                mapping: GetMappingResponseV2 =>

                    for {
                        // if given, get the default XSL transformation
                        xsltOption: Option[String] <- if (mapping.mapping.defaultXSLTransformation.nonEmpty) {
                            for {
                                xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(mapping.mapping.defaultXSLTransformation.get, requestingUser = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                            } yield Some(xslTransformation.xslt)
                        } else {
                            Future(None)
                        }
                    } yield mapping.mappingIri -> MappingAndXSLTransformation(mapping = mapping.mapping, standoffEntities = mapping.standoffEntities, XSLTransformation = xsltOption)

            }

            mappings: Vector[(IRI, MappingAndXSLTransformation)] <- Future.sequence(mappingsWithFuture)
            mappingsAsMap: Map[IRI, MappingAndXSLTransformation] = mappings.toMap
        } yield mappingsAsMap

    }

}