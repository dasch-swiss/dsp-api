/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.standoffmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.KnoraResponseV1

/**
 * Provides the IRI of the created mapping.
 *
 * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
 */
case class CreateMappingResponseV1(mappingIri: IRI) extends KnoraResponseV1 {
  def toJsValue: JsValue = RepresentationV1JsonProtocol.createMappingResponseV1Format.write(this)
}

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
