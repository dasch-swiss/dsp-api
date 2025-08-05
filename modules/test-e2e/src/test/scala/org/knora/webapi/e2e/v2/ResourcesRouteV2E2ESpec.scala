/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import sttp.model.Uri
import sttp.model.Uri.*
import zio.json.*
import zio.json.ast.Json

import java.nio.file.Paths
import java.time.Instant
import scala.collection.mutable.ArrayBuffer

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.e2e.InstanceChecker
import org.knora.webapi.e2e.v2.ResponseCheckerV2.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.ResponseOps.assert404
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.*

/**
 * Tests the API v2 resources route.
 */
class ResourcesRouteV2E2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private var aThingLastModificationDate = Instant.now
  private val hamletResourceIri          = new MutableTestIri
  private val aThingIri                  = "http://rdfh.ch/0001/a-thing"
  private val aThingWithHistoryIri       = "http://rdfh.ch/0001/thing-with-history"
  private val reiseInsHeiligeLandIri     = "http://rdfh.ch/0803/2a6221216701"

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
  )

  private val instanceChecker: InstanceChecker = InstanceChecker.make

  private def successResponse(message: String): String =
    s"""{
       |  "knora-api:result" : "$message",
       |  "@context" : {
       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
       |  }
       |}""".stripMargin

  private def updateResourceMetadataResponse(
    newLastModificationDate: Instant,
    maybeNewLabel: String,
    resourceIri: String,
    maybeNewPermissions: String,
  ): String =
    s"""{
       |    "knora-api:lastModificationDate": {
       |        "@value": "$newLastModificationDate",
       |        "@type": "xsd:dateTimeStamp"
       |    },
       |    "rdfs:label": "$maybeNewLabel",
       |    "knora-api:resourceIri": "$resourceIri",
       |    "knora-api:hasPermissions": "$maybeNewPermissions",
       |    "knora-api:resourceClassIri": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
       |    "@context": {
       |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
       |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
       |        "xsd": "http://www.w3.org/2001/XMLSchema#",
       |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
       |    }
       |}""".stripMargin

  private def testData(fileName: String): String = readTestData("resourcesR2RV2", fileName)

  "The resources v2 endpoint" should {
    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$reiseInsHeiligeLandIri").flatMap(_.assert200),
      )

      val expectedAnswerJSONLD = testData("BookReiseInsHeiligeLand.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri),
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in Turtle" in {
      // Test correct handling of q values in the Accept header by requesting as string with turtle content type.
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", _.header("Accept", "text/turtle"))
          .flatMap(_.assert200),
      )
      val expectedAnswerTurtle = testData("BookReiseInsHeiligeLand.ttl")
      assert(RdfModel.fromTurtle(responseAsString) == RdfModel.fromTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", _.header("Accept", "application/rdf+xml"))
          .flatMap(_.assert200),
      )
      val expectedAnswerRdfXml = testData("BookReiseInsHeiligeLand.rdf")
      assert(RdfModel.fromRdfXml(responseAsString) == RdfModel.fromRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resourcespreview/$reiseInsHeiligeLandIri").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("BookReiseInsHeiligeLandPreview.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource preview request for a Thing resource using the complex schema" in {
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingIri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("AThing.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header) in JSON-LD" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$reiseInsHeiligeLandIri?schema=simple").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("BookReiseInsHeiligeLandSimple.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          responseAsString,
          "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        ),
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in Turtle" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getAsString(
            uri"/v2/resources/$reiseInsHeiligeLandIri",
            _.header("X-Knora-Accept-Schema", "simple").header("Accept", "text/turtle"),
          )
          .flatMap(_.assert200),
      )
      val expectedAnswerTurtle = testData("BookReiseInsHeiligeLandSimple.ttl")
      assert(RdfModel.fromTurtle(responseAsString) == RdfModel.fromTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in RDF/XML" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getAsString(
            uri"/v2/resources/$reiseInsHeiligeLandIri",
            _.header("X-Knora-Accept-Schema", "simple").header("Accept", "application/rdf+xml"),
          )
          .flatMap(_.assert200),
      )
      val expectedAnswerRdfXml = testData("BookReiseInsHeiligeLandSimple.rdf")
      assert(RdfModel.fromRdfXml(responseAsString) == RdfModel.fromRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resourcespreview/$reiseInsHeiligeLandIri?schema=simple").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("BookReiseInsHeiligeLandSimplePreview.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$reiseInsHeiligeLandIri?schema=simple").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("BookReiseInsHeiligeLandSimple.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the first page of the book '[Das] Narrenschiff (lat.)' using the complex schema" in {
      val iri = "http://rdfh.ch/0803/7bbb8e59b703"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("NarrenschiffFirstPage.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a resource with a BCE date property" in {
      val iri = "http://rdfh.ch/0001/thing_with_BCE_date"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithBCEDate.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {
      val iri = "http://rdfh.ch/0001/thing_with_BCE_date2"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithBCEDate2.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a list value" in {
      val iri = "http://rdfh.ch/0001/thing_with_list_value"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithListValue.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a list value (in the simple schema)" in {
      val iri = "http://rdfh.ch/0001/thing_with_list_value"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri?schema=simple").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithListValueSimple.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a link (in the complex schema)" in {
      val iri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithLinkComplex.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a link (in the simple schema)" in {
      val iri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri?schema=simple").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithLinkSimple.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a Text language (in the complex schema)" in {
      val iri = "http://rdfh.ch/0001/a-thing-with-text-valuesLanguage"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithTextLangComplex.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with a Text language (in the simple schema)" in {
      val iri = "http://rdfh.ch/0001/a-thing-with-text-valuesLanguage"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri?schema=simple").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithTextLangSimple.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a resource with values of different types" in {
      val iri = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("Testding.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a Thing resource with a link to a ThingPicture resource" in {
      val iri = "http://rdfh.ch/0001/a-thing-with-picture"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithPicture.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request with a link to a resource that the user doesn't have permission to see" in {
      val iri = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithOneHiddenResource.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request with a link to a resource that is marked as deleted" in {
      val iri = "http://rdfh.ch/0001/l8f8FVEiSCeq9A1p8gBR-A"
      val responseAsString =
        UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/resources/$iri").flatMap(_.assert200))
      val expectedAnswerJSONLD = testData("ThingWithOneDeletedResource.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(responseAsString, "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
      )
    }

    "perform a full resource request for a past version of a resource, using a URL-encoded xsd:dateTimeStamp" in {
      val timestamp = "2019-02-12T08:05:10.351Z"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$aThingWithHistoryIri?version=$timestamp").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("ThingWithVersionHistory.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a past version of a resource, using a Knora ARK timestamp" in {
      val timestamp = "20190212T080510351Z"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$aThingWithHistoryIri?version=$timestamp").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("ThingWithVersionHistory.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the complete version history of a resource" in {
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri").flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("CompleteVersionHistory.jsonld")
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the version history of a resource within a date range" in {
      val startDate = Instant.parse("2019-02-08T15:05:11Z").toString
      val endDate   = Instant.parse("2019-02-13T09:05:10Z").toString
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri?startDate=$startDate&endDate=$endDate")
          .flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("PartialVersionHistory.jsonld")
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return each of the versions of a resource listed in its version history" in {
      val historyResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri").flatMap(_.assert200),
      )
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(historyResponseAsString)
      val entries: JsonLDArray = jsonLDDocument.body
        .getRequiredArray("@graph")
        .fold(e => throw BadRequestException(e), identity)

      for (entry: JsonLDValue <- entries.value) {
        entry match {
          case jsonLDObject: JsonLDObject =>
            val versionDate: Instant = jsonLDObject.requireDatatypeValueInObject(
              key = KnoraApiV2Complex.VersionDate,
              expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
            )

            val arkTimestamp = stringFormatter.formatArkTimestamp(versionDate)
            val versionResponseAsString = UnsafeZioRun.runOrThrow(
              TestApiClient
                .getJsonLd(uri"/v2/resources/$aThingWithHistoryIri?version=$arkTimestamp")
                .flatMap(_.assert200),
            )
            val expectedAnswerJSONLD = testData(s"ThingWithVersionHistory$arkTimestamp.jsonld")
            compareJSONLDForResourcesResponse(expectedAnswerJSONLD, versionResponseAsString)

          case other => throw AssertionException(s"Expected JsonLDObject, got $other")
        }
      }
    }

    "return all history events for a given resource" in {
      val resourceIri = "http://rdfh.ch/0001/a-thing-picture"
      UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(uri"/v2/resources/resourceHistoryEvents/$resourceIri", SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )
    }

    "return entire resource and value history events for a given project" in {
      val projectIri = "http://rdfh.ch/projects/0001"
      UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(uri"/v2/resources/projectHistoryEvents/$projectIri", SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )
    }

    "return a graph of resources reachable via links from/to a given resource" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=both").flatMap(_.assert200),
      )
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)
      val expectedAnswerJSONLD = testData("ThingGraphBoth.jsonld")
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=outbound").flatMap(_.assert200),
      )
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)
      val expectedAnswerJSONLD = testData("ThingGraphOutbound.jsonld")
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to a given resource" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=inbound").flatMap(_.assert200),
      )
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)
      val expectedAnswerJSONLD = testData("ThingGraphInbound.jsonld")
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to/from a given resource, excluding a specified property" in {
      val resourceIri     = "http://rdfh.ch/0001/start"
      val excludeProperty = "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(uri"/v2/graph/$resourceIri?direction=both&excludeProperty=$excludeProperty")
          .flatMap(_.assert200),
      )
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)
      val expectedAnswerJSONLD = testData("ThingGraphBothWithExcludedProp.jsonld")
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource, specifying search depth" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=both&depth=2").flatMap(_.assert200),
      )
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)
      val expectedAnswerJSONLD = testData("ThingGraphBothWithDepth.jsonld")
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "not accept a graph request with an invalid direction" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=foo").flatMap(_.assert400))
    }

    "not accept a graph request with an invalid depth (< 1)" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?depth=0").flatMap(_.assert400))
    }

    "not accept a graph request with an invalid depth (> max)" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      val maxDepth    = appConfig.v2.graphRoute.maxGraphBreadth + 1
      UnsafeZioRun.runOrThrow(TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?depth=$maxDepth").flatMap(_.assert400))
    }

    "not accept a graph request with an invalid property to exclude" in {
      val resourceIri = "http://rdfh.ch/0001/start"
      UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?excludeProperty=foo").flatMap(_.assert400),
      )
    }

    "return resources from a project" in {
      val resourceClass   = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"
      val orderByProperty = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title"
      val responseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(
            uri"/v2/resources?resourceClass=$resourceClass&orderByProperty=$orderByProperty&page=0",
            SharedTestDataADM.incunabulaProjectAdminUser,
            _.header("x-knora-accept-project", SharedTestDataADM.incunabulaProject.id.value),
          )
          .flatMap(_.assert200),
      )
      val expectedAnswerJSONLD = testData("BooksFromIncunabula.jsonld")
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "create a resource with values" in {
      val createResourceWithValues: String =
        """{
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:booleanValueAsBoolean" : true
          |  },
          |  "anything:hasColor" : {
          |    "@type" : "knora-api:ColorValue",
          |    "knora-api:colorValueAsColor" : "#ff3333"
          |  },
          |  "anything:hasDate" : {
          |    "@type" : "knora-api:DateValue",
          |    "knora-api:dateValueHasCalendar" : "GREGORIAN",
          |    "knora-api:dateValueHasEndEra" : "CE",
          |    "knora-api:dateValueHasEndYear" : 1489,
          |    "knora-api:dateValueHasStartEra" : "CE",
          |    "knora-api:dateValueHasStartYear" : 1489
          |  },
          |  "anything:hasDecimal" : {
          |    "@type" : "knora-api:DecimalValue",
          |    "knora-api:decimalValueAsDecimal" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "100000000000000.000000000000001"
          |    }
          |  },
          |  "anything:hasGeometry" : {
          |    "@type" : "knora-api:GeomValue",
          |    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"
          |  },
          |  "anything:hasGeoname" : {
          |    "@type" : "knora-api:GeonameValue",
          |    "knora-api:geonameValueAsGeonameCode" : "2661604"
          |  },
          |  "anything:hasInteger" : [ {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
          |    "knora-api:intValueAsInt" : 5,
          |    "knora-api:valueHasComment" : "this is the number five"
          |  }, {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:intValueAsInt" : 6
          |  } ],
          |  "anything:hasInterval" : {
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "3.4"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "1.2"
          |    }
          |  },
          |  "anything:hasTimeStamp" : {
          |    "@type" : "knora-api:TimeValue",
          |    "knora-api:timeValueAsTimeStamp" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2020-01-24T08:47:10.307068Z"
          |    }
          |  },
          |  "anything:hasListItem" : {
          |    "@type" : "knora-api:ListValue",
          |    "knora-api:listValueAsListNode" : {
          |      "@id" : "http://rdfh.ch/lists/0001/treeList03"
          |    }
          |  },
          |  "anything:hasOtherThingValue" : {
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:linkValueHasTargetIri" : {
          |      "@id" : "http://rdfh.ch/0001/a-thing"
          |    }
          |  },
          |  "anything:hasRichtext" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
          |    "knora-api:textValueHasMapping" : {
          |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    }
          |  },
          |  "anything:hasText" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "this is text without standoff"
          |  },
          |  "anything:hasUri" : {
          |    "@type" : "knora-api:UriValue",
          |    "knora-api:uriValueAsUri" : {
          |      "@type" : "xsd:anyURI",
          |      "@value" : "https://www.knora.org"
          |    }
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, anythingUser1)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )

      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceComplexGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        ),
      )

      // Request the newly created resource in the simple schema, and check that it matches the ontology.
      val resourceSimpleGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri?schema=simple", anythingUser1).flatMap(_.assert200),
      )

      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceSimpleGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        ),
      )

      // Check that the text value with standoff is correct in the simple schema.
      val resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
      val text: String =
        resourceSimpleAsJsonLD.body
          .getRequiredString("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasRichtext")
          .fold(msg => throw BadRequestException(msg), identity)
      assert(text == "this is text with standoff")
    }

    "create a resource with text value containing footnote with apostrophe - should encode apostrophe correctly" in {
      val textValueAsXml: String =
        """|<?xml version="1.0" encoding="UTF-8"?>
           |<text>Text <footnote content="Apostrophe start ' end"/> end text</text>
           |""".stripMargin

      val createResourceWithTextFootnote: String =
        s"""|{
            |  "@type" : "anything:Thing",
            |  "anything:hasRichtext" : {
            |    "@type" : "knora-api:TextValue",
            |    "knora-api:textValueAsXml" : ${JsString(textValueAsXml).compactPrint},
            |    "knora-api:textValueHasMapping" : {
            |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
            |    }
            |  },
            |  "knora-api:attachedToProject" : {
            |    "@id" : "http://rdfh.ch/projects/0001"
            |  },
            |  "rdfs:label" : "test thing with footnote apostrophe",
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithTextFootnote),
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, responseToString(response))
      val responseJsonDoc: JsonLDDocument               = responseToJsonLDDocument(response)
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource and check that the apostrophe is correctly encoded
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}",
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse = singleAwaitingRequest(resourceGetRequest)
      assert(resourceGetResponse.status == StatusCodes.OK, responseToString(resourceGetResponse))
      val resourceJsonDoc: JsonLDDocument = responseToJsonLDDocument(resourceGetResponse)

      val textValue: JsonLDObject = resourceJsonDoc.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .fold(msg => throw BadRequestException(msg), identity)

      val savedTextValueAsXml: String = textValue
        .getRequiredString(KnoraApiV2Complex.TextValueAsXml)
        .fold(msg => throw BadRequestException(msg), identity)

      // The apostrophe should be preserved as-is, not escaped as &apos;
      assert(
        savedTextValueAsXml.contains("Apostrophe start &apos; end"),
        s"Expected apostrophe to be preserved, but got: $savedTextValueAsXml",
      )
    }

    "create a resource and a property with references to an external ontology (FOAF)" in {
      val createResourceWithRefToFoaf: String =
        """{
          |  "@type" : "freetest:FreeTestSubClassOfFoafPerson",
          |  "freetest:hasFoafName" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "this is a foaf name"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "Test foaf Person",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
          |  }
          |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", createResourceWithRefToFoaf, anythingUser1)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )

      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceComplexGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
        ),
      )

      // Request the newly created resource in the simple schema, and check that it matches the ontology.
      val resourceSimpleGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(uri"/v2/resources/$resourceIri?schema=simple", anythingUser1)
          .flatMap(_.assert200),
      )

      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceSimpleGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
        ),
      )

      // Check that the value is correct in the simple schema.
      val resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
      val foafName: String =
        resourceSimpleAsJsonLD.body
          .getRequiredString("http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#hasFoafName")
          .fold(msg => throw BadRequestException(msg), identity)
      assert(foafName == "this is a foaf name")
    }

    "create a resource whose label contains a Unicode escape and quotation marks" in {
      val jsonLDEntity: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/ThingWithUnicodeEscape.jsonld"),
        )
      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    "create a resource with a custom creation date" in {
      val creationDate: Instant = SharedTestDataADM.customResourceCreationDate

      val jsonLDEntity: String =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$creationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
        key = KnoraApiV2Complex.CreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
      )

      assert(savedCreationDate == creationDate)
    }

    def createResourceReqPayload() =
      createResourceWithCustomIRI("http://rdfh.ch/0001/" + UuidUtil.makeRandomBase64EncodedUuid)

    def createResourceWithCustomIRI(iri: IRI): String =
      s"""{
         |  "@id" : "$iri",
         |  "@type" : "anything:Thing",
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "anything:hasBoolean" : {
         |    "@type" : "knora-api:BooleanValue",
         |    "knora-api:booleanValueAsBoolean" : true
         |  },
         |  "rdfs:label" : "test thing",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         |  }
         |}""".stripMargin

    "create a resource with a custom IRI" in {
      val customIRI: IRI = SharedTestDataADM.customResourceIRI
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri == customIRI)
    }

    "not create a resource with an invalid custom IRI" in {
      val customIRI: IRI = "http://rdfh.ch/invalid-resource-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert400),
      )
    }

    "not create a resource with a custom IRI containing the wrong project code" in {
      val customIRI: IRI = "http://rdfh.ch/0803/a-thing-with-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert400),
      )
    }

    "return a DuplicateValueException during resource creation when the supplied resource IRI is not unique" in {
      // duplicate resource IRI

      val params =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing with duplicate iri",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val errorMessage: String = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLd(uri"/v2/resources", params, anythingUser1).flatMap(_.assert400),
      )
      val invalidIri: Boolean =
        errorMessage.contains(s"IRI: 'http://rdfh.ch/0001/a-thing' already exists, try another one.")
      invalidIri should be(true)
    }

    def createResourceWithCustomValueIRI(valueIRI: IRI): String =
      s"""{
         |  "@type" : "anything:Thing",
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "anything:hasBoolean" : {
         |    "@id" : "$valueIRI",
         |    "@type" : "knora-api:BooleanValue",
         |    "knora-api:booleanValueAsBoolean" : true
         |  },
         |  "rdfs:label" : "test thing with value IRI",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         |  }
         |}""".stripMargin

    "create a resource with random IRI and a custom value IRI" in {
      val customValueIRI: IRI = SharedTestDataADM.customValueIRI
      val jsonLDEntity        = createResourceWithCustomValueIRI(customValueIRI)

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )
      val valueIri: IRI = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .fold(e => throw BadRequestException(e), identity)
        .requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(valueIri == customValueIRI)
    }

    "create a resource with random resource IRI and custom value UUIDs" in {
      val customValueUUID = SharedTestDataADM.customValueUUID

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$customValueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )
      val valueUUID = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .flatMap(_.getRequiredString(KnoraApiV2Complex.ValueHasUUID))
        .fold(msg => throw BadRequestException(msg), identity)
      assert(valueUUID == customValueUUID)

    }

    "create a resource with random resource IRI and custom value creation date" in {
      val creationDate: Instant = SharedTestDataADM.customValueCreationDate

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : false,
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$creationDate"
           |    }
           |  },
           |  "rdfs:label" : "test thing with value has creation date",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

      // Request the newly created resource.
      val resourceGetResponseAsJsonLD = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )
      val savedCreationDate: Instant = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .fold(e => throw BadRequestException(e), identity)
        .requireDatatypeValueInObject(
          key = KnoraApiV2Complex.ValueCreationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )
      assert(savedCreationDate == creationDate)

    }

    "create a resource with custom resource IRI, creation date, and a value with custom value IRI and UUID" in {
      val customResourceIRI: IRI      = SharedTestDataADM.customResourceIRI_resourceWithValues
      val customCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
      val customValueIRI: IRI         = SharedTestDataADM.customValueIRI_withResourceIriAndValueIRIAndValueUUID
      val customValueUUID             = SharedTestDataADM.customValueUUID

      val jsonLDEntity =
        s"""{
           |   "@id" : "$customResourceIRI",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@id": "$customValueIRI",
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$customValueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$customCreationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri == customResourceIRI)

      // Request the newly created resource.
      val resourceGetResponseAsJsonLD = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )
      val valueIri: IRI = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .fold(e => throw BadRequestException(e), identity)
        .requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(valueIri == customValueIRI)

      val valueUUID = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .flatMap(_.getRequiredString(KnoraApiV2Complex.ValueHasUUID))
        .fold(msg => throw BadRequestException(msg), identity)
      assert(valueUUID == customValueUUID)

      val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
        key = KnoraApiV2Complex.CreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
      )

      assert(savedCreationDate == customCreationDate)

      // when no custom creation date is given to the value, it should have the same creation date as the resource
      val savedValueCreationDate: Instant = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .fold(e => throw BadRequestException(e), identity)
        .requireDatatypeValueInObject(
          key = KnoraApiV2Complex.ValueCreationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )
      assert(savedValueCreationDate == customCreationDate)

    }

    "create a resource as another user" in {
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:attachedToUser" : {
           |    "@id" : "${anythingUser1.id}"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      val savedAttachedToUser: IRI =
        responseJsonDoc.body.requireIriInObject(
          KnoraApiV2Complex.AttachedToUser,
          validationFun,
        )
      assert(savedAttachedToUser == anythingUser1.id)
    }

    "not create a resource as another user if the requesting user is an ordinary user" in {
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:attachedToUser" : {
           |    "@id" : "${anythingUser1.id}"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity, SharedTestDataADM.anythingUser2).flatMap(_.assert400),
      )
    }

    "create a resource containing escaped text" in {
      val jsonLDEntity =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/CreateResourceWithEscape.jsonld"),
        )
      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    "update the metadata of a resource" in {
      val newLabel            = "test thing with modified label"
      val newPermissions      = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingIri",
            |  "@type" : "anything:Thing",
            |  "rdfs:label" : "$newLabel",
            |  "knora-api:hasPermissions" : "$newPermissions",
            |  "knora-api:newModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$newModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      val updateResponseAsString: String = UnsafeZioRun.runOrThrow(
        TestApiClient.putJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      assert(
        updateResponseAsString.fromJson[Json] == updateResourceMetadataResponse(
          resourceIri = aThingIri,
          maybeNewLabel = newLabel,
          newLastModificationDate = newModificationDate,
          maybeNewPermissions = newPermissions,
        ).fromJson[Json],
      )

      val previewJsonLD = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLdDocument(uri"/v2/resourcespreview/$aThingIri", anythingUser1).flatMap(_.assert200),
      )
      val updatedLabel: String = previewJsonLD.body
        .getRequiredString(OntologyConstants.Rdfs.Label)
        .fold(msg => throw BadRequestException(msg), identity)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String =
        previewJsonLD.body
          .getRequiredString(KnoraApiV2Complex.HasPermissions)
          .fold(msg => throw BadRequestException(msg), identity)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions),
      )

      val lastModificationDate: Instant = previewJsonLD.body.requireDatatypeValueInObject(
        key = KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "update the metadata of a resource that has a last modification date" in {
      val newLabel            = "test thing with modified label again"
      val newPermissions      = "CR knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingIri",
            |  "@type" : "anything:Thing",
            |  "rdfs:label" : "$newLabel",
            |  "knora-api:hasPermissions" : "$newPermissions",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$aThingLastModificationDate"
            |  },
            |  "knora-api:newModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$newModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      val updateResponseAsString: String = UnsafeZioRun.runOrThrow(
        TestApiClient.putJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      assert(
        updateResponseAsString.fromJson[Json] == updateResourceMetadataResponse(
          resourceIri = aThingIri,
          maybeNewLabel = newLabel,
          newLastModificationDate = newModificationDate,
          maybeNewPermissions = newPermissions,
        ).fromJson[Json],
      )

      val previewResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingIri", anythingUser1).flatMap(_.assert200),
      )

      val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val updatedLabel: String = previewJsonLD.body
        .getRequiredString(OntologyConstants.Rdfs.Label)
        .fold(msg => throw BadRequestException(msg), identity)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String =
        previewJsonLD.body
          .getRequiredString(KnoraApiV2Complex.HasPermissions)
          .fold(msg => throw BadRequestException(msg), identity)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions),
      )

      val lastModificationDate: Instant = previewJsonLD.body.requireDatatypeValueInObject(
        key = KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "mark a resource as deleted" in {
      // given a new resource
      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", createResourceReqPayload(), anythingUser1)
          .flatMap(_.assert200),
      )

      val resourceIri = responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID).getOrElse {
        throw BadRequestException("Resource IRI not found in response")
      }
      val lastModificationDate = UnsafeZioRun.runOrThrow(
        responseJsonDoc.body
          .getDataTypeValueInObject(
            KnoraApiV2Complex.CreationDate,
            OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          )
          .someOrFail("Last modification date not found"),
      )

      // when deleting it
      val jsonLDEntity =
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$lastModificationDate"
            |  },
            |  "knora-api:deleteComment" : "Comment on why it was deleted",
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin
      val updateResponseAsString: String = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLd(uri"/v2/resources/delete", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )

      // then it should be marked as deleted
      assert(updateResponseAsString.fromJson[Json] == successResponse("Resource marked as deleted").fromJson[Json])

      val previewResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resourcespreview/$resourceIri", anythingUser1).flatMap(_.assert200),
      )
      val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val responseIsDeleted = previewJsonLD.body
        .getRequiredBoolean(KnoraApiV2Complex.IsDeleted)
        .fold(e => throw BadRequestException(e), identity)
      responseIsDeleted should equal(true)
      val responseType =
        previewJsonLD.body.getRequiredString("@type").fold(msg => throw BadRequestException(msg), identity)
      responseType should equal(KnoraApiV2Complex.DeletedResource)
    }

    "mark a resource as deleted, supplying a custom delete date" in {
      val resourceIri = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
      val deleteDate  = Instant.now

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:deleteComment" : "This resource is too boring.",
            |  "knora-api:deleteDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$deleteDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      val updateResponseAsString: String = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLd(uri"/v2/resources/delete", jsonLDEntity, SharedTestDataADM.superUser)
          .flatMap(_.assert200),
      )
      assert(updateResponseAsString.fromJson[Json] == successResponse("Resource marked as deleted").fromJson[Json])

      val previewResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resourcespreview/$resourceIri", anythingUser1).flatMap(_.assert200),
      )

      val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val responseIsDeleted = previewJsonLD.body
        .getRequiredBoolean(KnoraApiV2Complex.IsDeleted)
        .fold(e => throw BadRequestException(e), identity)
      responseIsDeleted should equal(true)
      val responseType =
        previewJsonLD.body.getRequiredString("@type").fold(msg => throw BadRequestException(msg), identity)
      responseType should equal(KnoraApiV2Complex.DeletedResource)
      val responseDeleteDate = previewJsonLD.body
        .getRequiredObject(KnoraApiV2Complex.DeleteDate)
        .flatMap(_.getRequiredString("@value"))
        .fold(e => throw BadRequestException(e), identity)
      responseDeleteDate should equal(deleteDate.toString)
    }

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.

      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasRichtext" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${hamletXml.toJson},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val resourceCreateResponseAsJsonLD: JsonLDDocument = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "read the large text and its markup as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))

      // Request the newly created resource.
      val resourceGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/${hamletResourceIri.get}", anythingUser1).flatMap(_.assert200),
      )

      // Check that the response matches the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        ),
      )

      // Get the XML from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val xmlFromResponse: String = resourceGetResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .flatMap(_.getRequiredString(KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "read the large text without its markup, and get the markup separately as pages of standoff" ignore { // depends on previous test
      // Get the resource without markup.
      val resourceGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLd(
            uri"/v2/resources/${hamletResourceIri.get}",
            anythingUser1,
            _.header("x-knora-accept-markup", "standoff"),
          )
          .flatMap(_.assert200),
      )

      // Check that the response matches the ontology.
      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        ),
      )

      // Get the standoff markup separately.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val textValue: JsonLDObject =
        resourceGetResponseAsJsonLD.body
          .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
          .fold(e => throw BadRequestException(e), identity)
      val maybeTextValueAsXml: Option[String] = textValue
        .getString(KnoraApiV2Complex.TextValueAsXml)
        .fold(msg => throw BadRequestException(msg), identity)
      assert(maybeTextValueAsXml.isEmpty)
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val textValueIri: IRI                             = textValue.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

      val standoffBuffer: ArrayBuffer[JsonLDObject] = ArrayBuffer.empty
      var offset: Int                               = 0
      var hasMoreStandoff: Boolean                  = true

      while (hasMoreStandoff) {
        // Get a page of standoff.

        val standoffGetResponseAsJsonLD: JsonLDObject = UnsafeZioRun
          .runOrThrow(
            TestApiClient
              .getJsonLdDocument(uri"/v2/standoff/${hamletResourceIri.get}/$textValueIri/$offset", anythingUser1)
              .flatMap(_.assert200),
          )
          .body

        val standoff: Seq[JsonLDValue] =
          standoffGetResponseAsJsonLD.getArray(JsonLDKeywords.GRAPH).map(_.value).getOrElse(Seq.empty)

        val standoffAsJsonLDObjects: Seq[JsonLDObject] = standoff.map {
          case jsonLDObject: JsonLDObject => jsonLDObject
          case other                      => throw AssertionException(s"Expected JsonLDObject, got $other")
        }

        standoffBuffer.appendAll(standoffAsJsonLDObjects)

        standoffGetResponseAsJsonLD
          .getInt(KnoraApiV2Complex.NextStandoffStartIndex)
          .fold(e => throw BadRequestException(e), identity) match {
          case Some(nextOffset) => offset = nextOffset
          case None             => hasMoreStandoff = false
        }
      }

      assert(standoffBuffer.length == 6738)

      // Check the standoff tags to make sure they match the ontology.

      for (jsonLDObject <- standoffBuffer) {
        val docForValidation = JsonLDDocument(body = jsonLDObject).toCompactString()

        UnsafeZioRun.runOrThrow(
          instanceChecker.check(
            docForValidation,
            jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr),
          ),
        )
      }
    }

    "erase a resource" in {
      val resourceLastModificationDate = Instant.parse("2019-02-13T09:05:10Z")

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingWithHistoryIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$resourceLastModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLd(uri"/v2/resources/erase", jsonLDEntity, SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200) *>
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingWithHistoryIri", anythingUser1).flatMap(_.assert404),
      )
    }

    "create a resource containing a text value with a standoff link" in {
      val jsonLDEntity =
        """{
          |  "@type": "anything:Thing",
          |  "anything:hasText": {
          |    "@type": "knora-api:TextValue",
          |    "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">resource</a>.\n</text>",
          |    "knora-api:textValueHasMapping": {
          |      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    }
          |  },
          |  "knora-api:attachedToProject": {
          |    "@id": "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label": "obj_inst1",
          |  "@context": {
          |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
          |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
          |  }
          |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )

      UnsafeZioRun.runOrThrow(
        instanceChecker.check(
          resourceComplexGetResponseAsString,
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        ),
      )

      // Check that it has the property knora-api:hasStandoffLinkToValue.
      val resourceJsonLDDoc = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
      assert(resourceJsonLDDoc.body.value.contains(KnoraApiV2Complex.HasStandoffLinkToValue))
    }

    "create a resource containing a text value with multiple standoff links" in {
      val jsonLDEntity =
        """{
          |  "@type": "anything:Thing",
          |  "anything:hasText": {
          |  "@type": "knora-api:TextValue",
          |  "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">thing</a> and a <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/a-blue-thing\">blue thing</a>.\n</text>",
          |   "knora-api:textValueHasMapping": {
          |                  "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
          |            	}
          |            },
          |   "knora-api:attachedToProject": {
          |                  "@id": "http://rdfh.ch/projects/0001"
          |                  },
          |   "rdfs:label": "thing_with_mutiple_standoffLinks",
          |   "@context": {
          |                "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
          |                "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |                "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          |                "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
          |                }
          | }""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI                              = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetResponseAsString = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200),
      )

      // Check that it has multiple property knora-api:hasStandoffLinkToValue.
      val resourceJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
      val numberOfStandofHasLinkValue = resourceJsonLDDoc.body
        .value(KnoraApiV2Complex.HasStandoffLinkToValue)
        .asInstanceOf[JsonLDArray]
        .value
        .size
      assert(numberOfStandofHasLinkValue == 2)
    }

    "return a IIIF manifest for the pages of a book" in {
      val resourceIri = "http://rdfh.ch/0001/thing-with-pages"
      val responseStr = UnsafeZioRun.runOrThrow(
        TestApiClient.getJsonLd(uri"/v2/resources/iiifmanifest/$resourceIri").flatMap(_.assert200),
      )
      val responseJson: Json = responseStr.fromJson[Json].getOrElse(Json.Null)
      val expectedJson: Json = testData("IIIFManifest.jsonld").fromJson[Json].getOrElse(Json.Null)
      assert(responseJson == expectedJson)
    }

    "correctly update the ontology cache when adding a resource, so that the resource can afterwards be found by gravsearch" in {
      val freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")

      // create a new resource class and add a property with cardinality to it
      val createResourceClass =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$freetestLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "freetest:NewClass",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "New resource class"
           |    },
           |    "rdfs:subClassOf" : [
           |            {
           |               "@id": "knora-api:Resource"
           |            },
           |      {
           |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
           |        "owl:maxCardinality": 1,
           |        "owl:onProperty": {
           |          "@id": "freetest:hasName"
           |        },
           |        "salsah-gui:guiOrder": 1
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
           |  }
           |}""".stripMargin

      UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLd(uri"/v2/ontologies/classes", createResourceClass, SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )

      // create an instance of the class
      val createResourceWithValues: String =
        """{
          |  "@type" : "freetest:NewClass",
          |  "freetest:hasName" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "The new text value"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "test resource instance",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
          |  }
          |}""".stripMargin

      val responseJsonDoc = UnsafeZioRun.runOrThrow(
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )
      val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

      // get resource back
      val resourceComplexGetResponse = UnsafeZioRun.runOrThrow(
        TestApiClient
          .getJsonLdDocument(uri"/v2/resources/$resourceIri", SharedTestDataADM.anythingAdminUser)
          .flatMap(_.assert200),
      )

      val valueObject = resourceComplexGetResponse.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/freetest/v2#hasName")
        .fold(e => throw BadRequestException(e), identity)
      val valueIri: IRI = valueObject.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
      val valueAsString: IRI = valueObject
        .getRequiredString("http://api.knora.org/ontology/knora-api/v2#valueAsString")
        .fold(msg => throw BadRequestException(msg), identity)

      assert(valueAsString == "The new text value")

      // try to edit the value which requires the class to be properly cached
      val editValue =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "freetest:NewClass",
           |  "freetest:hasName" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "changed value"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
           |  }
           |}""".stripMargin

      UnsafeZioRun.runOrThrow(
        TestApiClient.putJsonLd(uri"/v2/values", editValue, SharedTestDataADM.anythingAdminUser).flatMap(_.assert200),
      )
    }
  }
}
