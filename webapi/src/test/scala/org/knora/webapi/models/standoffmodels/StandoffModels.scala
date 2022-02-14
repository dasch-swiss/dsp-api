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

object DefineStandoffMapping {
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

sealed abstract case class XXX private () {}
