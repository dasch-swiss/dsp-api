/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.standoffmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV1
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff

/**
 * An abstract trait representing a Knora v1 API request message that can be sent to `StandoffResponderV1`.
 */
sealed trait StandoffResponderRequestV1 extends KnoraRequestV1

/**
 * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
 * A successful response will be a [[CreateMappingResponseV1]].
 *
 * @param xml                  the mapping in XML.
 * @param projectIri           the IRI of the project the mapping belongs to.
 * @param mappingName          the name of the mapping to be created.
 * @param userProfile          the profile of the user making the request.
 */
case class CreateMappingRequestV1(
  xml: String,
  label: String,
  projectIri: IRI,
  mappingName: String,
  userProfile: UserADM,
  apiRequestID: UUID
) extends StandoffResponderRequestV1

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
case class GetMappingResponseV1(
  mappingIri: IRI,
  mapping: MappingXMLtoStandoff,
  standoffEntities: StandoffEntityInfoGetResponseV1
)

/**
 * Represents a request that gets an XSL Transformation represented by a `knora-base:XSLTransformation`.
 *
 * @param xsltTextRepresentationIri the IRI of the `knora-base:XSLTransformation`.
 * @param userProfile               the profile of the user making the request.
 */
case class GetXSLTransformationRequestV1(
  xsltTextRepresentationIri: IRI,
  userProfile: UserADM
) extends StandoffResponderRequestV1

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

  implicit val createMappingApiRequestV1Format: RootJsonFormat[CreateMappingApiRequestV1] = jsonFormat3(
    CreateMappingApiRequestV1
  )
  implicit val createMappingResponseV1Format: RootJsonFormat[CreateMappingResponseV1] = jsonFormat1(
    CreateMappingResponseV1
  )
}
