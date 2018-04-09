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

package org.knora.webapi.messages.v2.responder.standoffmessages

import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraJsonLDRequestReaderV2, KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDObject, JsonLDString}
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * An abstract trait representing a Knora v2 API request message that can be sent to `StandoffResponderV2`.
  */
sealed trait StandoffResponderRequestV2 extends KnoraRequestV2

/**
  * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
  * A successful response will be a [[CreateMappingResponseV2]].
  *
  * @param metadata the metadata describing the mapping.
  * @param xml the mapping in XML syntax.
  * @param userProfile the profile of the user making the request.
  * @param apiRequestID the ID of the API request.
  */
case class CreateMappingRequestV2(metadata: CreateMappingRequestMetadataV2, xml: CreateMappingRequestXMLV2, userProfile: UserProfileV1, apiRequestID: UUID) extends StandoffResponderRequestV2

/**
  * Represents the metadata describing the mapping that is to be created.
  *
  * @param label the label describing the mapping.
  * @param projectIri  the IRI of the project the mapping belongs to.
  * @param mappingName the name of the mapping to be created.
  */
case class CreateMappingRequestMetadataV2(label: String, projectIri: SmartIri, mappingName: String) extends StandoffResponderRequestV2

object CreateMappingRequestMetadataV2 extends KnoraJsonLDRequestReaderV2[CreateMappingRequestMetadataV2] {
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            userProfile: UserProfileV1): CreateMappingRequestMetadataV2 = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val label: String = jsonLDDocument.requireString(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

        val projectIri: SmartIri = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)

        val mappingName: String = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.MappingHasName, stringFormatter.toSparqlEncodedString)

        CreateMappingRequestMetadataV2(
            label = label,
            projectIri = projectIri,
            mappingName = mappingName
        )
    }
}

/**
  * Represents the mapping as an XML document.
  *
  * @param xml the mapping to be created.
  */
case class CreateMappingRequestXMLV2(xml: String) extends StandoffResponderRequestV2

/**
  * Provides the IRI of the created mapping.
  *
  * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
  */
case class CreateMappingResponseV2(mappingIri: IRI) extends KnoraResponseV2 {
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val body = JsonLDObject(Map(
            "@id" -> JsonLDString(mappingIri),
            "@type" -> JsonLDString(SmartIri(OntologyConstants.KnoraBase.XMLToStandoffMapping).toOntologySchema(targetSchema).toString)
        ))

        val context = JsonLDObject(Map(
            "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
            "rdf" -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "owl" -> JsonLDString("http://www.w3.org/2002/07/owl#"),
            "xsd" -> JsonLDString("http://www.w3.org/2001/XMLSchema#")
        ))

        JsonLDDocument(body, context)
    }
}


