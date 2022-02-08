/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.sharedtestdata.SharedTestDataV1.ANYTHING_PROJECT_IRI
import spray.json._
import spray.json.DefaultJsonProtocol._

sealed abstract case class DefineStandoffMapping private (
  mappingName: String,
  projectIRI: String,
  label: String
) {
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

  def toMessage(): Unit =
    // TODO: implement
    ()
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
