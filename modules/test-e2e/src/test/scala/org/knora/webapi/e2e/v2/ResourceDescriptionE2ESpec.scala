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
   * Reads a single value back through the single-value endpoint, so a test sees only its own value.
   * A value keeps its UUID across updates while its IRI changes, so the IRI returned on creation keeps
   * addressing the current version.
   */
  private def getDescriptionValue(resourceIri: String, valueIri: String) =
    for {
      resource <- TestApiClient
                    .getJsonLdDocument(uri"/v2/values/$resourceIri/${valueUuidOf(valueIri)}", anythingUser1)
                    .flatMap(_.assert200)
                    .map(_.body)
      description <-
        ZIO.fromEither(descriptionsOf(resource).flatMap(_.headOption.toRight(s"No description in $resource")))
      text <- ZIO.fromEither(description.getRequiredString(KA.ValueAsString))
    } yield text

  /** A `/v2/values` payload with one plain-text description; with `valueIri` it updates that value. */
  private def descriptionRequest(
    resourceIri: String,
    resourceType: String,
    text: String,
    valueIri: Option[String] = None,
  ): String =
    s"""{
       |  "@id" : "$resourceIri",
       |  "@type" : "$resourceType",
       |  "knora-api:hasDescription" : {
       |    ${valueIri.fold("")(iri => s""""@id" : "$iri",""")}
       |    "@type" : "knora-api:TextValue",
       |    "knora-api:valueAsString" : "$text"
       |  },
       |  "@context" : {
       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
       |  }
       |}""".stripMargin

  /** Adds a plain-text description and returns the new value's IRI. */
  private def addDescription(resourceIri: String, resourceType: String, text: String) =
    TestApiClient
      .postJsonLdDocument(uri"/v2/values", descriptionRequest(resourceIri, resourceType, text), anythingUser1)
      .flatMap(_.assert200)
      .flatMap(response => ZIO.fromEither(response.body.getRequiredString(JsonLDKeywords.ID)))
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
        description  <- ZIO.fromEither(descriptions.headOption.toRight(s"No description in $resource"))
        xml          <- ZIO.fromEither(description.getRequiredString(KA.TextValueAsXml))
      } yield assertTrue(
        descriptions.size == 1,
        xml.contains("the description"),
        xml.contains("<strong>"),
      )
    },
    test("adds a description to an existing thing and reads it back, then updates it") {
      val update =
        (valueIri: String) => descriptionRequest(aThingIri, "anything:Thing", "the updated description", Some(valueIri))
      for {
        valueIri  <- addDescription(aThingIri, "anything:Thing", "the first description")
        savedText <- getDescriptionValue(aThingIri, valueIri)
        _         <- TestApiClient.putJsonLdDocument(uri"/v2/values", update(valueIri), anythingUser1).flatMap(_.assert200)
        updated   <- getDescriptionValue(aThingIri, valueIri)
      } yield assertTrue(savedText == "the first description", updated == "the updated description")
    },
    test("adds a second description to the same thing, so both come back") {
      for {
        firstIri   <- addDescription(aThingIri, "anything:Thing", "description set one")
        secondIri  <- addDescription(aThingIri, "anything:Thing", "description set two")
        firstText  <- getDescriptionValue(aThingIri, firstIri)
        secondText <- getDescriptionValue(aThingIri, secondIri)
      } yield assertTrue(Set(firstText, secondText) == Set("description set one", "description set two"))
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
      for {
        valueIri  <- addDescription(aTestRegionIri, "knora-api:Region", "a description of the region")
        savedText <- getDescriptionValue(aTestRegionIri, valueIri)
      } yield assertTrue(savedText == "a description of the region")
    },
  )
}
