/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataV1.{ANYTHING_PROJECT_IRI, INCUNABULA_PROJECT_IRI}
import spray.json._
import spray.json.DefaultJsonProtocol._

class StandoffModelsSpec extends CoreSpec {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "StandoffModels," when {

    "defining a custom standoff mapping," should {

      "create a valid representation of the mapping with default values" in {
        val mappingName = "customMapping"
        val mapping     = DefineStandoffMapping.make(mappingName)

        mapping.mappingName should equal(mappingName)
        mapping.projectIRI should equal(ANYTHING_PROJECT_IRI)
        mapping.label should equal("custom mapping")
      }

      "create a valid representation of the mapping with custom values" in {
        val mappingName = "customMapping"
        val projectIRI  = INCUNABULA_PROJECT_IRI
        val customLabel = "this is a custom mapping with a custom label"
        val mapping = DefineStandoffMapping.make(
          mappingName = mappingName,
          projectIRI = Some(projectIRI),
          label = Some(customLabel)
        )

        mapping.mappingName should equal(mappingName)
        mapping.projectIRI should equal(projectIRI)
        mapping.label should equal(customLabel)
      }
    }

    "serializing to JSON-LD," should {
      "create a valid serialization of a standoff mapping request with default values" in {
        val mappingName = "customMapping"
        val mapping     = DefineStandoffMapping.make(mappingName)
        val json        = mapping.toJSONLD().parseJson

        val expectedJSON =
          s"""
             |{
             |  "knora-api:mappingHasName": "$mappingName",
             |  "rdfs:label": "custom mapping",
             |  "knora-api:attachedToProject": {
             |    "@id": "http://rdfh.ch/projects/0001"
             |  },
             |  "@context": {
             |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
             |  }
             |}
             |""".stripMargin.parseJson

        json should equal(expectedJSON)
      }
      "create a valid serialization of a standoff mapping request with custom values" in {
        val mappingName = "customMapping"
        val projectIRI  = INCUNABULA_PROJECT_IRI
        val customLabel = "this is a custom mapping with a custom label"
        val mapping = DefineStandoffMapping.make(
          mappingName = mappingName,
          projectIRI = Some(projectIRI),
          label = Some(customLabel)
        )
        val json = mapping.toJSONLD().parseJson

        val expectedJSON =
          s"""
             |{
             |  "knora-api:mappingHasName": "$mappingName",
             |  "rdfs:label": "$customLabel",
             |  "knora-api:attachedToProject": {
             |    "@id": "$projectIRI"
             |  },
             |  "@context": {
             |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
             |  }
             |}
             |""".stripMargin.parseJson

        json should equal(expectedJSON)
      }
    }
  }
}
