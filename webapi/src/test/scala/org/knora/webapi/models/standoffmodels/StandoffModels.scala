/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.v2.responder.standoffmessages.{
  CreateMappingRequestMetadataV2,
  CreateMappingRequestV2,
  CreateMappingRequestXMLV2
}
import org.knora.webapi.sharedtestdata.SharedTestDataV1.ANYTHING_PROJECT_IRI
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

import java.util.UUID

sealed abstract case class DefineStandoffMapping private (
  mappingName: String,
  projectIRI: String,
  label: String
) {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e tests.
   *
   * @return JSON-LD serialization of the request that can be processed by the API V2.
   */
  def toJSONLD(): String =
    Map(
      "knora-api:mappingHasName" -> mappingName.toJson,
      "knora-api:attachedToProject" -> Map(
        JsonLDKeywords.ID -> projectIRI
      ).toJson,
      "rdfs:label" -> label.toJson,
      JsonLDKeywords.CONTEXT -> Map(
        "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
        "knora-api" -> OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
      ).toJson
    ).toJson.prettyPrint

  /**
   * Create a [[CreateMappingRequestV2]] message representation of the request. This can be used in unit tests.
   *
   * @param xml                  the mapping XML.
   * @param featureFactoryConfig the [[FeatureFactoryConfig]].
   * @param user                 the user issuing the request.
   * @return a [[CreateMappingRequestV2]] message representation of the request that can be processed by an Akka actor.
   */
  def toMessage(
    xml: String,
    featureFactoryConfig: FeatureFactoryConfig,
    user: UserADM
  ): CreateMappingRequestV2 = {
    val mappingMetadata = CreateMappingRequestMetadataV2(
      label = label,
      projectIri = projectIRI.toSmartIri,
      mappingName = mappingName
    )
    CreateMappingRequestV2(
      metadata = mappingMetadata,
      xml = CreateMappingRequestXMLV2(xml),
      featureFactoryConfig = featureFactoryConfig,
      requestingUser = user,
      apiRequestID = UUID.randomUUID()
    )
  }

}

/**
 * Helper object for creating a custom standoff mapping.
 *
 * Can be instantiated by calling `DefineStandoffMapping.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 *
 * To generate a [[CreateMappingRequestV2]] message, call `.toMessage`
 */
object DefineStandoffMapping {

  /**
   * Smart constructor for instantiating a [[DefineStandoffMapping]] request.
   *
   * @param mappingName the name of the mapping.
   * @param projectIRI  the IRI of the project to which the mapping gets attached. Optional.
   *                    If not provided, the "anything" project will be used.
   * @param label       the rdfs:label of the mapping. Optional.
   * @return a [[DefineStandoffMapping]] object.
   */
  def make(
    mappingName: String,
    projectIRI: Option[String] = None,
    label: Option[String] = None
  ): DefineStandoffMapping =
    new DefineStandoffMapping(
      mappingName = mappingName,
      projectIRI = projectIRI match {
        case Some(iri) => iri
        case None      => ANYTHING_PROJECT_IRI
      },
      label = label match {
        case Some(v) => v
        case None    => "custom mapping"
      }
    ) {}
}

object XXX {}
