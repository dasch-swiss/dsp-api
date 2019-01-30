/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v1.responder.standoffmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import spray.json._


/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `StandoffResponderV1`.
  */
sealed trait StandoffResponderRequestV1 extends KnoraRequestV1

/**
  * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
  * A successful response will be a [[CreateMappingResponseV1]].
  *
  * @param xml         the mapping in XML.
  * @param projectIri  the IRI of the project the mapping belongs to.
  * @param mappingName the name of the mapping to be created.
  * @param userProfile the profile of the user making the request.
  */
case class CreateMappingRequestV1(xml: String, label: String, projectIri: IRI, mappingName: String, userProfile: UserADM, apiRequestID: UUID) extends StandoffResponderRequestV1

/**
  * Provides the IRI of the created mapping.
  *
  * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
  */
case class CreateMappingResponseV1(mappingIri: IRI) extends KnoraResponseV1 {
    def toJsValue: JsValue = RepresentationV1JsonProtocol.createMappingResponseV1Format.write(this)
}

/**
  * Represents a request to get a mapping from XML elements and attributes to standoff entities.
  *
  * @param mappingIri  the IRI of the mapping.
  * @param userProfile the profile of the user making the request.
  */
case class GetMappingRequestV1(mappingIri: IRI, userProfile: UserADM) extends StandoffResponderRequestV1

/**
  * Represents a response to a [[GetMappingRequestV1]].
  *
  * @param mappingIri       the IRI of the requested mapping.
  * @param mapping          the requested mapping.
  * @param standoffEntities the standoff entities referred to in the mapping.
  */
case class GetMappingResponseV1(mappingIri: IRI, mapping: MappingXMLtoStandoff, standoffEntities: StandoffEntityInfoGetResponseV1)

/**
  * Represents a request that gets an XSL Transformation represented by a `knora-base:XSLTransformation`.
  *
  * @param xsltTextRepresentationIri the IRI of the `knora-base:XSLTransformation`.
  * @param userProfile               the profile of the user making the request.
  */
case class GetXSLTransformationRequestV1(xsltTextRepresentationIri: IRI, userProfile: UserADM) extends StandoffResponderRequestV1

/**
  * Represents a response to a [[GetXSLTransformationRequestV1]].
  *
  * @param xslt the XSLT to be applied to the XML created from standoff.
  */
case class GetXSLTransformationResponseV1(xslt: String)

/**
  * Represents an API request to create a mapping.
  *
  * @param project_id  the project in which the mapping is to be added.
  * @param label       the label describing the mapping.
  * @param mappingName the name of the mapping (will be appended to the mapping IRI).
  */
case class CreateMappingApiRequestV1(project_id: IRI, label: String, mappingName: String) {
    def toJsValue: JsValue = RepresentationV1JsonProtocol.createMappingApiRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for standoff handling.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val createMappingApiRequestV1Format: RootJsonFormat[CreateMappingApiRequestV1] = jsonFormat3(CreateMappingApiRequestV1)
    implicit val createMappingResponseV1Format: RootJsonFormat[CreateMappingResponseV1] = jsonFormat1(CreateMappingResponseV1)
}
