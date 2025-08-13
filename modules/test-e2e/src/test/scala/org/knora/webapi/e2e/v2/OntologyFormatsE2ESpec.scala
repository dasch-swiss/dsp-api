/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import zio.json.ast.*
import zio.json.*
import zio.*
import zio.test.*
import org.knora.webapi.testservices.ResponseOps.assert200

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.*
import sttp.client4.*
import sttp.model.*

object OntologyFormatsE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] =
    List(
      RdfDataObject("test_data/project_ontologies/freetest-onto.ttl", "http://www.knora.org/ontology/0001/freetest"),
      RdfDataObject("test_data/project_ontologies/minimal-onto.ttl", "http://www.knora.org/ontology/0001/minimal"),
    )

  /**
   * Represents an HTTP GET test that requests ontology information.
   *
   * @param uri                         the URL path to be used in the request.
   * @param fileBasename                the basename of the test data file containing the expected response.
   */
  private case class HttpGetTest(uri: Uri, fileBasename: String, persistTtl: Boolean = false) {
    def makeFile(fileEnding: String = "jsonld"): Path =
      Paths.get("test_data", "generated_test_data", "ontologyR2RV2", s"$fileBasename.$fileEnding")

    private def storeAsTtl(): Unit = {
      val jsonStr = readFile()
      val model   = RdfModel.fromJsonLD(jsonStr)
      val ttlStr  = RdfFormatUtil.format(model, Turtle)
      val newFile = makeFile("ttl")
      Files.createDirectories(newFile.getParent)
      val _ = FileUtil.writeTextFile(newFile, ttlStr)
      ()
    }

    /**
     * Reads the expected response file.
     *
     * @return the contents of the file.
     */
    def readFile(): String =
      FileUtil.readTextFile(makeFile())

    def fileExists: Boolean =
      Files.exists(makeFile())

    def writeReceived(responseStr: String): Unit = {
      val newOutputFile = makeFile()
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(newOutputFile, responseStr)
      if (persistTtl) storeAsTtl()
      else ()
    }
  }

  private val mediaTypeJsonLd: MediaType = MediaType.unsafeParse("application/ld+json")
  private val mediaTypeTurtle: MediaType = MediaType.unsafeParse("text/turtle")
  private val mediaTypeRdfXml: MediaType = MediaType.unsafeParse("application/rdf+xml")

  private def checkTestCase(httpGetTest: HttpGetTest) =
    for {
      responseJsonLd <- getResponse(httpGetTest.uri, mediaTypeJsonLd)
      responseTtl    <- getResponse(httpGetTest.uri, mediaTypeTurtle)
      responseRdfXml <- getResponse(httpGetTest.uri, mediaTypeRdfXml)
      _ = if (!httpGetTest.fileExists) {
            httpGetTest.writeReceived(responseJsonLd)
            throw new AssertionError(s"File not found ${httpGetTest.makeFile().toAbsolutePath}")
          }
      approvedJsonLd = httpGetTest.readFile()
      _ = if (responseJsonLd.fromJson[Json] != approvedJsonLd.fromJson[Json]) {
            httpGetTest.writeReceived(responseJsonLd)
            throw new AssertionError(
              s"""|
                  |The response did not equal the approved data.
                  |
                  |Response:
                  |
                  |$responseJsonLd
                  |
                  |
                  |${"=" * 120}
                  |
                  |
                  |Approved data:
                  |
                  |$approvedJsonLd
                  |
                  |""".stripMargin,
            )
          }
    } yield assertTrue(
      RdfModel.fromTurtle(responseTtl) == RdfModel.fromJsonLD(responseJsonLd),
      RdfModel.fromRdfXml(responseRdfXml) == RdfModel.fromJsonLD(responseJsonLd),
    )

  private def getResponse(uri: Uri, mediaType: MediaType) =
    TestApiClient.getAsString(uri, _.header("Accept", mediaType.toString())).flatMap(_.assert200)

  private object TestCases {
    private val knoraApiOntologySimple =
      HttpGetTest(uri"/v2/ontologies/allentities/${KnoraApiV2Simple.KnoraApiOntologyIri}", "knoraApiOntologySimple")

    private val knoraApiOntologyComplex =
      HttpGetTest(
        uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}",
        "knoraApiOntologyWithValueObjects",
      )

    private val salsahGuiOntology =
      HttpGetTest(uri"/ontology/salsah-gui/v2", "salsahGuiOntology")

    private val standoffOntology =
      HttpGetTest(uri"/ontology/standoff/v2", "standoffOntologyWithValueObjects")

    private val knoraApiDateSegmentSimple =
      HttpGetTest(uri"/v2/ontologies/classes/${KnoraApiV2Simple.Date}", "knoraApiDate")

    private val knoraApiDateSegmentComplex =
      HttpGetTest(uri"/v2/ontologies/classes/${KnoraApiV2Complex.DateValue}", "knoraApiDateValue")

    private val knoraApiHasColorSegmentSimple =
      HttpGetTest(uri"/v2/ontologies/properties/${KnoraApiV2Simple.HasColor}", "knoraApiSimpleHasColor")

    private val knoraApiHasColorSegmentComplex =
      HttpGetTest(uri"/v2/ontologies/properties/${KnoraApiV2Complex.HasColor}", "knoraApiWithValueObjectsHasColor")

    private val anythingOntologyMetadata =
      HttpGetTest(uri"/v2/ontologies/metadata/${SharedTestDataADM.anythingProjectIri}", "anythingOntologyMetadata")

    private val anythingOntologySimple =
      HttpGetTest(
        uri"/v2/ontologies/allentities/${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost_SIMPLE}",
        "anythingOntologySimple",
      )

    private val anythingOntologyComplex =
      HttpGetTest(
        uri"/v2/ontologies/allentities/${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
        "anythingOntologyWithValueObjects",
        persistTtl = true,
      )

    private val anythingThingWithAllLanguages =
      HttpGetTest(
        uri"/v2/ontologies/classes/${SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost}?allLanguages=true",
        "anythingThingWithAllLanguages",
      )

    private val anythingOntologyThingSimple =
      HttpGetTest(
        uri"/v2/ontologies/classes/${SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost_SIMPLE}",
        "anythingThingSimple",
      )

    private val anythingOntologyThingComplex =
      HttpGetTest(
        uri"/v2/ontologies/classes/${SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost}",
        "anythingThing",
      )

    private val anythingOntologyHasListItemSimple =
      HttpGetTest(
        uri"/v2/ontologies/properties/${SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost_SIMPLE}",
        "anythingHasListItemSimple",
      )

    private val anythingOntologyHasListItemComplex =
      HttpGetTest(
        uri"/v2/ontologies/properties/${SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost}",
        "anythingHasListItem",
      )

    val testCases: Seq[HttpGetTest] = Seq(
      // built-in ontologies
      knoraApiOntologySimple,
      knoraApiOntologyComplex,
      salsahGuiOntology,
      standoffOntology,
      // class segments of built-in ontologies
      knoraApiDateSegmentSimple,
      knoraApiDateSegmentComplex,
      // property segments of built-in ontologies
      knoraApiHasColorSegmentSimple,
      knoraApiHasColorSegmentComplex,
      // project ontologies
      anythingOntologyMetadata,
      anythingOntologySimple,
      anythingOntologyComplex,
      anythingThingWithAllLanguages,
      // class segments of project ontologies
      anythingOntologyThingSimple,
      anythingOntologyThingComplex,
      // property segments of project ontologies
      anythingOntologyHasListItemSimple,
      anythingOntologyHasListItemComplex,
    )
  }

  override val e2eSpec = suite("serve the knora-api ontology in the simple schema on two separate endpoints")(
    test(s"serve in JSON-LD, Turtle and RDF/XML") {
      checkAll(Gen.fromIterable(TestCases.testCases))(checkTestCase)
    },
    test("as JSON-LD") {
      for {
        allEntities <- getResponse(
                         uri"/v2/ontologies/allentities/${KnoraApiV2Simple.KnoraApiOntologyIri}",
                         mediaTypeJsonLd,
                       )
        knoraApi <- getResponse(uri"/ontology/knora-api/simple/v2", mediaTypeJsonLd)
      } yield assertTrue(
        RdfModel.fromJsonLD(allEntities) == RdfModel.fromJsonLD(knoraApi),
      )
    },
    test("as Turtle") {
      for {
        ontologyAllEntitiesResponseTurtle <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Simple.KnoraApiOntologyIri}",
            mediaTypeTurtle,
          )
        knoraApiResponseTurtle <- getResponse(uri"/ontology/knora-api/simple/v2", mediaTypeTurtle)
      } yield assertTrue(
        RdfModel.fromTurtle(ontologyAllEntitiesResponseTurtle) == RdfModel.fromTurtle(knoraApiResponseTurtle),
      )
    },
    test("as RDF/XML") {
      for {
        ontologyAllEntitiesResponseRdfXml <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Simple.KnoraApiOntologyIri}",
            mediaTypeRdfXml,
          )
        knoraApiResponseRdfXml <- getResponse(uri"/ontology/knora-api/simple/v2", mediaTypeRdfXml)
      } yield assertTrue(
        RdfModel.fromRdfXml(ontologyAllEntitiesResponseRdfXml) == RdfModel.fromRdfXml(knoraApiResponseRdfXml),
      )
    },
    test("serve the knora-api in the complex schema on two separate endpoints JSON-LD") {
      for {
        ontologyAllEntitiesResponseJson <-
          getResponse(uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}", mediaTypeJsonLd)
        knoraApiResponseJson <- getResponse(uri"/ontology/knora-api/v2", mediaTypeJsonLd)
      } yield assertTrue(
        RdfModel.fromJsonLD(ontologyAllEntitiesResponseJson) == RdfModel.fromJsonLD(knoraApiResponseJson),
      )
    },
    test("serve the knora-api in the complex schema on two separate endpoints Turtle") {
      for {
        ontologyAllEntitiesResponseTurtle <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}",
            mediaTypeTurtle,
          )
        knoraApiResponseTurtle <- getResponse(uri"/ontology/knora-api/v2", mediaTypeTurtle)
      } yield assertTrue(
        RdfModel.fromTurtle(ontologyAllEntitiesResponseTurtle) == RdfModel.fromTurtle(knoraApiResponseTurtle),
      )
    },
    test("serve the knora-api in the complex schema on two separate endpoints RDF/XML") {
      for {
        ontologyAllEntitiesResponseRdfXml <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}",
            mediaTypeRdfXml,
          )
        knoraApiResponseRdfXml <- getResponse(uri"/ontology/knora-api/v2", mediaTypeRdfXml)
      } yield assertTrue(
        RdfModel.fromRdfXml(ontologyAllEntitiesResponseRdfXml) == RdfModel.fromRdfXml(knoraApiResponseRdfXml),
      )
    },
    test("serve the knora-api ontology in the complex schema on two separate endpoints Turtle") {
      for {
        ontologyAllEntitiesResponseTurtle <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}",
            mediaTypeTurtle,
          )
        knoraApiResponseTurtle <- getResponse(uri"/ontology/knora-api/v2", mediaTypeTurtle)
      } yield assertTrue(
        RdfModel.fromTurtle(ontologyAllEntitiesResponseTurtle) == RdfModel.fromTurtle(knoraApiResponseTurtle),
      )
    },
    test("serve the knora-api ontology in the complex schema on two separate endpoints RDF/XML") {
      for {
        ontologyAllEntitiesResponseRdfXml <-
          getResponse(
            uri"/v2/ontologies/allentities/${KnoraApiV2Complex.KnoraApiOntologyIri}",
            mediaTypeRdfXml,
          )
        knoraApiResponseRdfXml <- getResponse(uri"/ontology/knora-api/v2", mediaTypeRdfXml)
      } yield assertTrue(
        RdfModel.fromRdfXml(ontologyAllEntitiesResponseRdfXml) == RdfModel.fromRdfXml(knoraApiResponseRdfXml),
      )
    },
  )
}
