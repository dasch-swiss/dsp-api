/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.json.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.Owl
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDBoolean
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * Tests `knora-api:hasDescription`, the 0-n rich-text property every resource class inherits from
 * `knora-api:Resource`, at the HTTP boundary: creation with and without a description, adding and
 * updating description values, repeated values, the inherited class-level cardinality, and the side
 * effect that a directly-used knora-base class (`knora-api:Region`) accepts it too.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ResourceDescriptionE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private val hasDescription = KA.KnoraApiV2PrefixExpansion + "hasDescription"
  private val aThingIri      = "http://rdfh.ch/0001/a-thing"
  private val aTestRegionIri = "http://rdfh.ch/0001/A5NfXW4QRxOnBPULCTvH5w"

  private def valueUuidOf(valueIri: String): String = valueIri.substring(valueIri.lastIndexOf('/') + 1)

  // The property is rendered as a bare object when it carries a single value and as an array otherwise.
  private def descriptionsOf(resource: JsonLDObject): Either[String, Seq[JsonLDObject]] =
    resource
      .getRequiredObject(hasDescription)
      .map(Seq(_))
      .orElse(resource.getRequiredArray(hasDescription).map(_.value.collect { case o: JsonLDObject => o }))

  /**
   * Reads a single value back through the single-value endpoint, so a test sees only its own value:
   * the endpoint returns a resource stub with just the requested value under its property.
   */
  private def getDescriptionValue(resourceIri: String, valueIri: String) =
    for {
      resource <- TestApiClient
                    .getJsonLdDocument(uri"/v2/values/$resourceIri/${valueUuidOf(valueIri)}", anythingUser1)
                    .flatMap(_.assert200)
                    .map(_.body)
      descriptions <- ZIO.fromEither(descriptionsOf(resource))
    } yield descriptions.head

  override val e2eSpec: Spec[env, Any] = suite("ResourceDescriptionE2ESpec")(
    test("creates an anything:Thing with a rich-text description and reads it back") {
      val textValueAsXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text><p><strong>this is</strong> the description</p></text>""".stripMargin
      val createRequest =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:hasDescription" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${textValueAsXml.toJson},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "a thing with a description",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      for {
        created <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", createRequest, anythingUser1).flatMap(_.assert200)
        resourceIri  <- ZIO.fromEither(created.body.getRequiredString(JsonLDKeywords.ID))
        resource     <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        descriptions <- ZIO.fromEither(descriptionsOf(resource.body))
        xml          <- ZIO.fromEither(descriptions.head.getRequiredString(KA.TextValueAsXml))
      } yield assertTrue(
        descriptions.size == 1,
        xml.contains("the description"),
        xml.contains("<strong>"),
      )
    },
    test("adds a description to an existing thing and reads it back, then updates it") {
      val createRequest =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:hasDescription" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "the first description"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      def updateRequest(valueIri: String) =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:hasDescription" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "the updated description"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      for {
        created   <- TestApiClient.postJsonLdDocument(uri"/v2/values", createRequest, anythingUser1).flatMap(_.assert200)
        valueIri  <- ZIO.fromEither(created.body.getRequiredString(JsonLDKeywords.ID))
        saved     <- getDescriptionValue(aThingIri, valueIri)
        savedText <- ZIO.fromEither(saved.getRequiredString(KA.ValueAsString))

        // knora-api:valueHasUUID stays stable across updates, even though the value's own IRI changes,
        // so the original value IRI's UUID keeps addressing the current version through the values endpoint.
        _ <-
          TestApiClient.putJsonLdDocument(uri"/v2/values", updateRequest(valueIri), anythingUser1).flatMap(_.assert200)
        savedUpdated     <- getDescriptionValue(aThingIri, valueIri)
        savedUpdatedText <- ZIO.fromEither(savedUpdated.getRequiredString(KA.ValueAsString))
      } yield assertTrue(
        savedText == "the first description",
        savedUpdatedText == "the updated description",
      )
    },
    test("adds a second description to the same thing, so both come back") {
      val firstRequest =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:hasDescription" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "description set one"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val secondRequest =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:hasDescription" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "description set two"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      for {
        first     <- TestApiClient.postJsonLdDocument(uri"/v2/values", firstRequest, anythingUser1).flatMap(_.assert200)
        firstIri  <- ZIO.fromEither(first.body.getRequiredString(JsonLDKeywords.ID))
        second    <- TestApiClient.postJsonLdDocument(uri"/v2/values", secondRequest, anythingUser1).flatMap(_.assert200)
        secondIri <- ZIO.fromEither(second.body.getRequiredString(JsonLDKeywords.ID))

        savedFirst      <- getDescriptionValue(aThingIri, firstIri)
        savedFirstText  <- ZIO.fromEither(savedFirst.getRequiredString(KA.ValueAsString))
        savedSecond     <- getDescriptionValue(aThingIri, secondIri)
        savedSecondText <- ZIO.fromEither(savedSecond.getRequiredString(KA.ValueAsString))
      } yield assertTrue(
        Set(savedFirstText, savedSecondText) == Set("description set one", "description set two"),
      )
    },
    test("creates an anything:Thing without a description, which reads back without hasDescription") {
      val createRequest =
        """{
          |  "@type" : "anything:Thing",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "a thing without a description",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}""".stripMargin
      for {
        created <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", createRequest, anythingUser1).flatMap(_.assert200)
        resourceIri <- ZIO.fromEither(created.body.getRequiredString(JsonLDKeywords.ID))
        resource    <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
      } yield assertTrue(!resource.body.value.contains(hasDescription))
    },
    test("the anything:Thing class carries an inherited 0-n cardinality on knora-api:hasDescription") {
      val classIri                                                      = SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost
      def onProperty(restriction: JsonLDObject): Either[String, String] =
        restriction.getRequiredObject(Owl.OnProperty).flatMap(_.getIri)
      for {
        response <-
          TestApiClient.getJsonLdDocument(uri"/v2/ontologies/classes/$classIri", anythingUser1).flatMap(_.assert200)
        graph   <- ZIO.fromEither(response.body.getRequiredArray(JsonLDKeywords.GRAPH))
        classObj = graph.value.collect { case o: JsonLDObject => o }
                     .find(_.getRequiredString(JsonLDKeywords.ID).contains(classIri))
        subClassOf <- ZIO.fromEither(
                        classObj.toRight(s"$classIri not found in $graph").flatMap(_.getRequiredArray(Rdfs.SubClassOf)),
                      )
        restriction =
          subClassOf.value.collect { case o: JsonLDObject => o }.find(onProperty(_).contains(hasDescription))
        found          <- ZIO.fromEither(restriction.toRight(s"No restriction on $hasDescription in $subClassOf"))
        minCardinality <- ZIO.fromEither(found.getRequiredInt(Owl.MinCardinality))
        isInherited     = found.value.get(KA.IsInherited).contains(JsonLDBoolean(true))
      } yield assertTrue(minCardinality == 0, isInherited)
    },
    test("a knora-api:Region accepts a hasDescription value") {
      val createRequest =
        s"""{
           |  "@id" : "$aTestRegionIri",
           |  "@type" : "knora-api:Region",
           |  "knora-api:hasDescription" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "a description of the region"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin
      for {
        created   <- TestApiClient.postJsonLdDocument(uri"/v2/values", createRequest, anythingUser1).flatMap(_.assert200)
        valueIri  <- ZIO.fromEither(created.body.getRequiredString(JsonLDKeywords.ID))
        saved     <- getDescriptionValue(aTestRegionIri, valueIri)
        savedText <- ZIO.fromEither(saved.getRequiredString(KA.ValueAsString))
      } yield assertTrue(savedText == "a description of the region")
    },
  )
}
