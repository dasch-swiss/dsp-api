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
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.e2e.InstanceChecker
import org.knora.webapi.e2e.v2.ResponseCheckerV2.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.ResponseOps.assert404
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.*

object ResourcesRouteV2E2ESpec extends E2EZSpec {

  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  private var aThingLastModificationDate  = Instant.now
  private val hamletResourceIri           = new MutableTestIri
  private val aThingIri                   = "http://rdfh.ch/0001/a-thing"
  val aThingWithHistoryIri: ResourceIri   = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history".toSmartIri)
  val reiseInsHeiligeLandIri: ResourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/2a6221216701".toSmartIri)

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

  private def readFile(fileName: String): ZIO[TestDataFileUtil, Nothing, String] =
    TestDataFileUtil.readTestData("resourcesR2RV2", fileName)

  private def createResourceReqPayload() =
    createResourceWithCustomIRI("http://rdfh.ch/0001/" + UuidUtil.makeRandomBase64EncodedUuid)

  private def createResourceWithCustomIRI(iri: IRI): String =
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

  private def createResourceWithCustomValueIRI(valueIRI: IRI): String =
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

  override val e2eSpec = suite("The resources v2 endpoint")(
    test("perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema") {
      for {
        responseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$reiseInsHeiligeLandIri").flatMap(_.assert200)
        expectedAnswerJSONLD <- readFile("BookReiseInsHeiligeLandPreview.jsonld")
        _                    <- ZIO.attempt(compareJSONLDForResourcesResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test("perform a resource preview request for a Thing resource using the complex schema") {
      for {
        responseAsString     <- TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingIri").flatMap(_.assert200)
        expectedAnswerJSONLD <- readFile("AThing.jsonld")
        _                    <- ZIO.attempt(compareJSONLDForResourcesResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test(
      "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)",
    ) {
      for {
        responseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$reiseInsHeiligeLandIri?schema=simple").flatMap(_.assert200)
        expectedAnswerJSONLD <-
          readFile("BookReiseInsHeiligeLandSimplePreview.jsonld")
        _ <- ZIO.attempt(compareJSONLDForResourcesResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test("return the complete version history of a resource") {
      for {
        responseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri").flatMap(_.assert200)
        expectedAnswerJSONLD <- readFile("CompleteVersionHistory.jsonld")
        _                    <- ZIO.attempt(compareJSONLDForResourceHistoryResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test("return the version history of a resource within a date range") {
      val startDate = Instant.parse("2019-02-08T15:05:11Z").toString
      val endDate   = Instant.parse("2019-02-13T09:05:10Z").toString
      for {
        responseAsString <-
          TestApiClient
            .getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri?startDate=$startDate&endDate=$endDate")
            .flatMap(_.assert200)
        expectedAnswerJSONLD <- readFile("PartialVersionHistory.jsonld")
        _                    <- ZIO.attempt(compareJSONLDForResourceHistoryResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test("return each of the versions of a resource listed in its version history") {
      for {
        historyResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/history/$aThingWithHistoryIri").flatMap(_.assert200)
        jsonLDDocument = JsonLDUtil.parseJsonLD(historyResponseAsString)
        entries = jsonLDDocument.body
                    .getRequiredArray("@graph")
                    .fold(e => throw BadRequestException(e), identity)
        _ <- ZIO.foreachDiscard(entries.value) {
               case jsonLDObject: JsonLDObject =>
                 for {
                   versionDate <- ZIO.succeed(
                                    jsonLDObject.requireDatatypeValueInObject(
                                      key = KnoraApiV2Complex.VersionDate,
                                      expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                      validationFun = (s, errorFun) =>
                                        ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                    ),
                                  )
                   arkTimestamp <- ZIO.succeed(sf.formatArkTimestamp(versionDate))
                   versionResponseAsString <-
                     TestApiClient
                       .getJsonLd(uri"/v2/resources/$aThingWithHistoryIri?version=$arkTimestamp")
                       .flatMap(_.assert200)
                   expectedAnswerJSONLD <-
                     readFile(s"ThingWithVersionHistory$arkTimestamp.jsonld")
                   _ <- ZIO.attempt(compareJSONLDForResourcesResponse(expectedAnswerJSONLD, versionResponseAsString))
                 } yield ()
               case other => ZIO.fail(AssertionException(s"Expected JsonLDObject, got $other"))
             }
      } yield assertCompletes
    },
    test("return all history events for a given resource") {
      val resourceIri = "http://rdfh.ch/0001/a-thing-picture"
      for {
        _ <- TestApiClient
               .getJsonLd(uri"/v2/resources/resourceHistoryEvents/$resourceIri", SharedTestDataADM.anythingAdminUser)
               .flatMap(_.assert200)
      } yield assertCompletes
    },
    test("return entire resource and value history events for a given project") {
      val projectIri = "http://rdfh.ch/projects/0001"
      for {
        _ <- TestApiClient
               .getJsonLd(uri"/v2/resources/projectHistoryEvents/$projectIri", SharedTestDataADM.anythingAdminUser)
               .flatMap(_.assert200)
      } yield assertCompletes
    },
    test("return a graph of resources reachable via links from/to a given resource") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        responseAsString     <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=both").flatMap(_.assert200)
        parsedReceivedJsonLD  = JsonLDUtil.parseJsonLD(responseAsString)
        expectedAnswerJSONLD <- readFile("ThingGraphBoth.jsonld")
        parsedExpectedJsonLD  = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      } yield assertTrue(parsedReceivedJsonLD == parsedExpectedJsonLD)
    },
    test("return a graph of resources reachable via links from a given resource") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        responseAsString     <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=outbound").flatMap(_.assert200)
        parsedReceivedJsonLD  = JsonLDUtil.parseJsonLD(responseAsString)
        expectedAnswerJSONLD <- readFile("ThingGraphOutbound.jsonld")
        parsedExpectedJsonLD  = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      } yield assertTrue(parsedReceivedJsonLD == parsedExpectedJsonLD)
    },
    test("return a graph of resources reachable via links to a given resource") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        responseAsString     <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=inbound").flatMap(_.assert200)
        parsedReceivedJsonLD  = JsonLDUtil.parseJsonLD(responseAsString)
        expectedAnswerJSONLD <- readFile("ThingGraphInbound.jsonld")
        parsedExpectedJsonLD  = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      } yield assertTrue(parsedReceivedJsonLD == parsedExpectedJsonLD)
    },
    test("return a graph of resources reachable via links to/from a given resource, excluding a specified property") {
      val resourceIri     = "http://rdfh.ch/0001/start"
      val excludeProperty = "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing"
      for {
        responseAsString <- TestApiClient
                              .getJsonLd(uri"/v2/graph/$resourceIri?direction=both&excludeProperty=$excludeProperty")
                              .flatMap(_.assert200)
        parsedReceivedJsonLD  = JsonLDUtil.parseJsonLD(responseAsString)
        expectedAnswerJSONLD <- readFile("ThingGraphBothWithExcludedProp.jsonld")
        parsedExpectedJsonLD  = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      } yield assertTrue(parsedReceivedJsonLD == parsedExpectedJsonLD)
    },
    test("return a graph of resources reachable via links from a given resource, specifying search depth") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        responseAsString <-
          TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=both&depth=2").flatMap(_.assert200)
        parsedReceivedJsonLD  = JsonLDUtil.parseJsonLD(responseAsString)
        expectedAnswerJSONLD <- readFile("ThingGraphBothWithDepth.jsonld")
        parsedExpectedJsonLD  = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)
      } yield assertTrue(parsedReceivedJsonLD == parsedExpectedJsonLD)
    },
    test("not accept a graph request with an invalid direction") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        _ <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?direction=foo").flatMap(_.assert400)
      } yield assertCompletes
    },
    test("not accept a graph request with an invalid depth (< 1)") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        _ <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?depth=0").flatMap(_.assert400)
      } yield assertCompletes
    },
    test("not accept a graph request with an invalid depth (> max)") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        maxDepth <- ZIO.serviceWith[AppConfig](_.v2.graphRoute.maxGraphBreadth + 1)
        _        <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?depth=$maxDepth").flatMap(_.assert400)
      } yield assertCompletes
    },
    test("not accept a graph request with an invalid property to exclude") {
      val resourceIri = "http://rdfh.ch/0001/start"
      for {
        _ <- TestApiClient.getJsonLd(uri"/v2/graph/$resourceIri?excludeProperty=foo").flatMap(_.assert400)
      } yield assertCompletes
    },
    test("return resources from a project") {
      val resourceClass   = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"
      val orderByProperty = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title"
      for {
        responseAsString <- TestApiClient
                              .getJsonLd(
                                uri"/v2/resources?resourceClass=$resourceClass&orderByProperty=$orderByProperty&page=0",
                                SharedTestDataADM.incunabulaProjectAdminUser,
                                _.header("x-knora-accept-project", SharedTestDataADM.incunabulaProject.id.value),
                              )
                              .flatMap(_.assert200)
        expectedAnswerJSONLD <- readFile("BooksFromIncunabula.jsonld")
        _                    <- ZIO.attempt(compareJSONLDForResourcesResponse(expectedAnswerJSONLD, responseAsString))
      } yield assertCompletes
    },
    test("create a resource with values") {
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

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, anythingUser1)
                             .flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

        // Request the newly created resource in the complex schema, and check that it matches the ontology.
        resourceComplexGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceComplexGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
             )
        // Request the newly created resource in the simple schema, and check that it matches the ontology.
        resourceSimpleGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri?schema=simple", anythingUser1).flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceSimpleGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
             )
        // Check that the text value with standoff is correct in the simple schema.
        resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
        text: String =
          resourceSimpleAsJsonLD.body
            .getRequiredString("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasRichtext")
            .fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        text == "this is text with standoff",
      )
    },
    test("create a resource and a property with references to an external ontology (FOAF)") {
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

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", createResourceWithRefToFoaf, anythingUser1)
                             .flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        // Request the newly created resource in the complex schema, and check that it matches the ontology.
        resourceComplexGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceComplexGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
             )
        // Request the newly created resource in the simple schema, and check that it matches the ontology.
        resourceSimpleGetResponseAsString <- TestApiClient
                                               .getJsonLd(uri"/v2/resources/$resourceIri?schema=simple", anythingUser1)
                                               .flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceSimpleGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
             )
        // Check that the value is correct in the simple schema.
        resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
        foafName: String =
          resourceSimpleAsJsonLD.body
            .getRequiredString("http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#hasFoafName")
            .fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        foafName == "this is a foaf name",
      )
    },
    test("create a resource whose label contains a Unicode escape and quotation marks") {
      for {
        jsonLdEntity <- readFile("ThingWithUnicodeEscape.jsonld")
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLdEntity, anythingUser1).flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      } yield assertTrue(resourceIri.toSmartIri.isKnoraDataIri)
    },
    test("create a resource with a custom creation date") {
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

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1)
                             .flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                                       key = KnoraApiV2Complex.CreationDate,
                                       expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                       validationFun = (s, errorFun) =>
                                         ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                     )
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        savedCreationDate == creationDate,
      )
    },
    test("create a resource with a custom IRI") {
      val customIRI: IRI = SharedTestDataADM.customResourceIRI
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1)
                             .flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      } yield assertTrue(resourceIri == customIRI)
    },
    test("not create a resource with an invalid custom IRI") {
      val customIRI: IRI = "http://rdfh.ch/invalid-resource-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      for {
        _ <- TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert400)
      } yield assertCompletes
    },
    test("not create a resource with a custom IRI containing the wrong project code") {
      val customIRI: IRI = "http://rdfh.ch/0803/a-thing-with-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      for {
        _ <- TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert400)
      } yield assertCompletes
    },
    test("return a DuplicateValueException during resource creation when the supplied resource IRI is not unique") {
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

      for {
        errorMessage <- TestApiClient.postJsonLd(uri"/v2/resources", params, anythingUser1).flatMap(_.assert400)
        invalidIri: Boolean =
          errorMessage.contains(s"IRI: 'http://rdfh.ch/0001/a-thing' already exists, try another one.")
      } yield assertTrue(invalidIri)
    },
    test("create a resource with random IRI and a custom value IRI") {
      val customValueIRI: IRI = SharedTestDataADM.customValueIRI
      val jsonLDEntity        = createResourceWithCustomValueIRI(customValueIRI)

      for {
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        // Get the value from the response.
        resourceGetResponseAsJsonLD <-
          TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        valueIri: IRI = resourceGetResponseAsJsonLD.body
                          .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
                          .fold(e => throw BadRequestException(e), identity)
                          .requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      } yield assertTrue(valueIri == customValueIRI)
    },
    test("create a resource with random resource IRI and custom value UUIDs") {
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

      for {
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        // Get the value from the response.
        resourceGetResponseAsJsonLD <-
          TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        valueUUID = resourceGetResponseAsJsonLD.body
                      .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
                      .flatMap(_.getRequiredString(KnoraApiV2Complex.ValueHasUUID))
                      .fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(valueUUID == customValueUUID)
    },
    test("create a resource with random resource IRI and custom value creation date") {
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

      for {
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        // Request the newly created resource.
        resourceGetResponseAsJsonLD <-
          TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        savedCreationDate: Instant = resourceGetResponseAsJsonLD.body
                                       .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
                                       .fold(e => throw BadRequestException(e), identity)
                                       .requireDatatypeValueInObject(
                                         key = KnoraApiV2Complex.ValueCreationDate,
                                         expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                         validationFun = (s, errorFun) =>
                                           ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                       )
      } yield assertTrue(savedCreationDate == creationDate)
    },
    test("create a resource with custom resource IRI, creation date, and a value with custom value IRI and UUID") {
      for {
        customResourceIRI  <- ZIO.succeed(SharedTestDataADM.customResourceIRI_resourceWithValues)
        customCreationDate <- ZIO.succeed(Instant.parse("2019-01-09T15:45:54.502951Z"))
        customValueIRI     <- ZIO.succeed(SharedTestDataADM.customValueIRI_withResourceIriAndValueIRIAndValueUUID)
        customValueUUID    <- ZIO.succeed(SharedTestDataADM.customValueUUID)
        jsonLDEntity = s"""{
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
                          |  }
                          |}""".stripMargin
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        resourceGetResponseAsJsonLD <-
          TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        valueIri: IRI = resourceGetResponseAsJsonLD.body
                          .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
                          .fold(e => throw BadRequestException(e), identity)
                          .requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        valueUUID = resourceGetResponseAsJsonLD.body
                      .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
                      .flatMap(_.getRequiredString(KnoraApiV2Complex.ValueHasUUID))
                      .fold(msg => throw BadRequestException(msg), identity)
        savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                                       key = KnoraApiV2Complex.CreationDate,
                                       expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                       validationFun = (s, errorFun) =>
                                         ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                     )
        savedValueCreationDate: Instant =
          resourceGetResponseAsJsonLD.body
            .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
            .fold(e => throw BadRequestException(e), identity)
            .requireDatatypeValueInObject(
              key = KnoraApiV2Complex.ValueCreationDate,
              expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
            )
      } yield assertTrue(
        resourceIri == customResourceIRI,
        valueIri == customValueIRI,
        valueUUID == customValueUUID,
        savedCreationDate == customCreationDate,
        savedValueCreationDate == customCreationDate,
      )
    },
    test("create a resource as another user") {
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

      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", jsonLDEntity, SharedTestDataADM.anythingAdminUser)
                             .flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        savedAttachedToUser: IRI =
          responseJsonDoc.body.requireIriInObject(
            KnoraApiV2Complex.AttachedToUser,
            validationFun,
          )
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        savedAttachedToUser == anythingUser1.id,
      )
    },
    test("not create a resource as another user if the requesting user is an ordinary user") {
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

      for {
        _ <- TestApiClient
               .postJsonLd(uri"/v2/resources", jsonLDEntity, SharedTestDataADM.anythingUser2)
               .flatMap(_.assert400)
      } yield assertCompletes
    },
    test("create a resource containing escaped text") {
      for {
        jsonLd <- readFile("CreateResourceWithEscape.jsonld")
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLd, anythingUser1).flatMap(_.assert200)
        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      } yield assertTrue(resourceIri.toSmartIri.isKnoraDataIri)
    },
    test("update the metadata of a resource") {
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

      for {
        updateResponseAsString <-
          TestApiClient.putJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)
        expectedResponse = updateResourceMetadataResponse(
                             resourceIri = aThingIri,
                             maybeNewLabel = newLabel,
                             newLastModificationDate = newModificationDate,
                             maybeNewPermissions = newPermissions,
                           )
        previewJsonLD <-
          TestApiClient.getJsonLdDocument(uri"/v2/resourcespreview/$aThingIri", anythingUser1).flatMap(_.assert200)
        updatedLabel: String = previewJsonLD.body
                                 .getRequiredString(OntologyConstants.Rdfs.Label)
                                 .fold(msg => throw BadRequestException(msg), identity)
        updatedPermissions: String =
          previewJsonLD.body
            .getRequiredString(KnoraApiV2Complex.HasPermissions)
            .fold(msg => throw BadRequestException(msg), identity)
        lastModificationDate: Instant = previewJsonLD.body.requireDatatypeValueInObject(
                                          key = KnoraApiV2Complex.LastModificationDate,
                                          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                          validationFun = (s, errorFun) =>
                                            ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                        )
        _ <- ZIO.attempt { aThingLastModificationDate = newModificationDate }
      } yield assertTrue(
        updateResponseAsString.fromJson[Json] == expectedResponse.fromJson[Json],
        updatedLabel == newLabel,
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions),
        lastModificationDate == newModificationDate,
      )
    },
    test("update the metadata of a resource that has a last modification date") {
      for {
        newLabel            <- ZIO.succeed("test thing with modified label again")
        newPermissions      <- ZIO.succeed("CR knora-admin:ProjectMember|V knora-admin:ProjectMember")
        newModificationDate <- ZIO.succeed(Instant.now.plus(java.time.Duration.ofDays(1)))
        jsonLDEntity = s"""|{
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
        updateResponseAsString <-
          TestApiClient.putJsonLd(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)
        expectedResponse = updateResourceMetadataResponse(
                             resourceIri = aThingIri,
                             maybeNewLabel = newLabel,
                             newLastModificationDate = newModificationDate,
                             maybeNewPermissions = newPermissions,
                           )
        previewResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingIri", anythingUser1).flatMap(_.assert200)
        previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
        updatedLabel: String = previewJsonLD.body
                                 .getRequiredString(OntologyConstants.Rdfs.Label)
                                 .fold(msg => throw BadRequestException(msg), identity)
        updatedPermissions: String = previewJsonLD.body
                                       .getRequiredString(KnoraApiV2Complex.HasPermissions)
                                       .fold(msg => throw BadRequestException(msg), identity)
        lastModificationDate: Instant = previewJsonLD.body.requireDatatypeValueInObject(
                                          key = KnoraApiV2Complex.LastModificationDate,
                                          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                          validationFun = (s, errorFun) =>
                                            ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
                                        )
        _ <- ZIO.attempt { aThingLastModificationDate = newModificationDate }
      } yield assertTrue(
        updateResponseAsString.fromJson[Json] == expectedResponse.fromJson[Json],
        updatedLabel == newLabel,
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions),
        lastModificationDate == newModificationDate,
      )
    },
    test("mark a resource as deleted") {
      for {
        responseJsonDoc <- TestApiClient
                             .postJsonLdDocument(uri"/v2/resources", createResourceReqPayload(), anythingUser1)
                             .flatMap(_.assert200)
        resourceIri <- ZIO.succeed(responseJsonDoc.body.getRequiredString(JsonLDKeywords.ID).getOrElse {
                         throw BadRequestException("Resource IRI not found in response")
                       })
        lastModificationDate <- responseJsonDoc.body
                                  .getDataTypeValueInObject(
                                    KnoraApiV2Complex.CreationDate,
                                    OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                                  )
                                  .someOrFail("Last modification date not found")
        jsonLDEntity = s"""|{
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
        updateResponseAsString <-
          TestApiClient.postJsonLd(uri"/v2/resources/delete", jsonLDEntity, anythingUser1).flatMap(_.assert200)
        previewResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$resourceIri", anythingUser1).flatMap(_.assert200)
        previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
        responseIsDeleted = previewJsonLD.body
                              .getRequiredBoolean(KnoraApiV2Complex.IsDeleted)
                              .fold(e => throw BadRequestException(e), identity)
        responseType =
          previewJsonLD.body.getRequiredString("@type").fold(msg => throw BadRequestException(msg), identity)
      } yield assertTrue(
        updateResponseAsString.fromJson[Json] == successResponse("Resource marked as deleted").fromJson[Json],
        responseIsDeleted == true,
        responseType == KnoraApiV2Complex.DeletedResource,
      )
    },
    test("mark a resource as deleted, supplying a custom delete date") {
      for {
        resourceIri <- ZIO.succeed("http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA")
        deleteDate  <- ZIO.succeed(Instant.now)
        jsonLDEntity = s"""|{
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
        updateResponseAsString <- TestApiClient
                                    .postJsonLd(uri"/v2/resources/delete", jsonLDEntity, SharedTestDataADM.superUser)
                                    .flatMap(_.assert200)
        previewResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$resourceIri", anythingUser1).flatMap(_.assert200)
        previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
        responseIsDeleted = previewJsonLD.body
                              .getRequiredBoolean(KnoraApiV2Complex.IsDeleted)
                              .fold(e => throw BadRequestException(e), identity)
        responseType =
          previewJsonLD.body.getRequiredString("@type").fold(msg => throw BadRequestException(msg), identity)
        responseDeleteDate = previewJsonLD.body
                               .getRequiredObject(KnoraApiV2Complex.DeleteDate)
                               .flatMap(_.getRequiredString("@value"))
                               .fold(e => throw BadRequestException(e), identity)
      } yield assertTrue(
        updateResponseAsString.fromJson[Json] == successResponse("Resource marked as deleted").fromJson[Json],
        responseIsDeleted == true,
        responseType == KnoraApiV2Complex.DeletedResource,
        responseDeleteDate == deleteDate.toString,
      )
    },
    test("create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)") {
      for {
        hamletXml <- readFile("hamlet.xml")
        jsonLDEntity = s"""{
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
        resourceCreateResponseAsJsonLD <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)
        resourceIri: IRI =
          resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        _ <- ZIO.attempt(hamletResourceIri.set(resourceIri))
      } yield assertTrue(resourceIri.toSmartIri.isKnoraDataIri)
    } @@ TestAspect.ignore, // uses too much memory for GitHub CI

    test("read the large text and its markup as XML, and check that it matches the original XML") {
      for {
        hamletXml <- readFile("hamlet.xml")
        resourceGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/${hamletResourceIri.get}", anythingUser1).flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
             )
        resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
        xmlFromResponse: String = resourceGetResponseAsJsonLD.body
                                    .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
                                    .flatMap(_.getRequiredString(KnoraApiV2Complex.TextValueAsXml))
                                    .fold(e => throw BadRequestException(e), identity)
        xmlDiff: Diff =
          DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      } yield assertTrue(xmlDiff.hasDifferences == false)
    } @@ TestAspect.ignore, // depends on previous test
    test("read the large text without its markup, and get the markup separately as pages of standoff") {
      for {
        resourceGetResponseAsString <- TestApiClient
                                         .getJsonLd(
                                           uri"/v2/resources/${hamletResourceIri.get}",
                                           anythingUser1,
                                           _.header("x-knora-accept-markup", "standoff"),
                                         )
                                         .flatMap(_.assert200)
        // Check that the response matches the ontology.
        _ <- instanceChecker.check(
               resourceGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
             )
        resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
        textValue = resourceGetResponseAsJsonLD.body
                      .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
                      .fold(e => throw BadRequestException(e), identity)
        maybeTextValueAsXml = textValue
                                .getString(KnoraApiV2Complex.TextValueAsXml)
                                .fold(msg => throw BadRequestException(msg), identity)
        textValueIri = textValue.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        standoffBuffer <- {
          def getStandoffResponse(offset: Int): ZIO[TestApiClient, Throwable, JsonLDDocument] = TestApiClient
            .getJsonLdDocument(uri"/v2/standoff/${hamletResourceIri.get}/$textValueIri/$offset", anythingUser1)
            .flatMap(_.assert200)

          def extractStandoff(response: JsonLDDocument): Seq[JsonLDObject] =
            response.body.getArray(JsonLDKeywords.GRAPH).map(_.value).getOrElse(Seq.empty).map {
              case jsonLDObject: JsonLDObject => jsonLDObject
              case other                      => throw AssertionException(s"Expected JsonLDObject, got $other")
            }

          def nextOffset(response: JsonLDDocument): Option[Int] =
            response.body
              .getInt(KnoraApiV2Complex.NextStandoffStartIndex)
              .fold(e => throw BadRequestException(e), identity)

          def standoffAndOffset(offset: Int) = for {
            response       <- getStandoffResponse(offset)
            standoffObjects = extractStandoff(response)
            nextOffsetValue = nextOffset(response)
          } yield (standoffObjects, nextOffsetValue)

          def collect(offset: Int, acc: Seq[JsonLDObject]): ZIO[TestApiClient, Throwable, Seq[JsonLDObject]] =
            standoffAndOffset(offset).flatMap {
              case (objs, Some(nextOffset)) => collect(nextOffset, acc ++ objs)
              case (objs, None)             => ZIO.succeed(acc ++ objs)
            }
          collect(0, Seq.empty)
        }
        _ <- ZIO.foreachDiscard(standoffBuffer) { jsonLDObject =>
               val docForValidation = JsonLDDocument(body = jsonLDObject).toCompactString()
               instanceChecker.check(
                 docForValidation,
                 jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, sf.toSmartIriWithErr),
               )
             }
      } yield assertTrue(
        maybeTextValueAsXml.isEmpty,
        standoffBuffer.length == 6738,
      )
    } @@ TestAspect.ignore, // depends on previous test
    test("erase a resource") {
      for {
        resourceLastModificationDate <- ZIO.succeed(Instant.parse("2019-02-13T09:05:10Z"))
        jsonLDEntity = s"""|{
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
        _ <- TestApiClient
               .postJsonLd(uri"/v2/resources/erase", jsonLDEntity, SharedTestDataADM.anythingAdminUser)
               .flatMap(_.assert200)
        _ <-
          TestApiClient.getJsonLd(uri"/v2/resourcespreview/$aThingWithHistoryIri", anythingUser1).flatMap(_.assert404)
      } yield assertCompletes
    },
    test("create a resource containing a text value with a standoff link") {
      for {
        jsonLDEntity <-
          ZIO.succeed(
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
              |}""".stripMargin,
          )
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        resourceComplexGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        _ <- instanceChecker.check(
               resourceComplexGetResponseAsString,
               "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
             )
        resourceJsonLDDoc = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        resourceJsonLDDoc.body.value.contains(KnoraApiV2Complex.HasStandoffLinkToValue),
      )
    },
    test("create a resource containing a text value with multiple standoff links") {
      for {
        jsonLDEntity <-
          ZIO.succeed(
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
              | }""".stripMargin,
          )
        responseJsonDoc <-
          TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLDEntity, anythingUser1).flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        resourceComplexGetResponseAsString <-
          TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
        resourceJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
        numberOfStandofHasLinkValue = resourceJsonLDDoc.body
                                        .value(KnoraApiV2Complex.HasStandoffLinkToValue)
                                        .asInstanceOf[JsonLDArray]
                                        .value
                                        .size
      } yield assertTrue(
        resourceIri.toSmartIri.isKnoraDataIri,
        numberOfStandofHasLinkValue == 2,
      )
    },
    test("return a IIIF manifest for the pages of a book") {
      for {
        resourceIri          <- ZIO.succeed("http://rdfh.ch/0001/thing-with-pages")
        responseStr          <- TestApiClient.getJsonLd(uri"/v2/resources/iiifmanifest/$resourceIri").flatMap(_.assert200)
        responseJson          = responseStr.fromJson[Json].getOrElse(Json.Null)
        expectedAnswerJSONLD <- readFile("IIIFManifest.jsonld")
        expectedJson          = expectedAnswerJSONLD.fromJson[Json].getOrElse(Json.Null)
      } yield assertTrue(responseJson == expectedJson)
    },
    test(
      "correctly update the ontology cache when adding a resource, so that the resource can afterwards be found by gravsearch",
    ) {
      for {
        freetestLastModDate <- ZIO.succeed(Instant.parse("2012-12-12T12:12:12.12Z"))
        createResourceClass = s"""{
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
        _ <- TestApiClient
               .postJsonLd(uri"/v2/ontologies/classes", createResourceClass, SharedTestDataADM.anythingAdminUser)
               .flatMap(_.assert200)
        createResourceWithValues = """{
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
        responseJsonDoc <-
          TestApiClient
            .postJsonLdDocument(uri"/v2/resources", createResourceWithValues, SharedTestDataADM.anythingAdminUser)
            .flatMap(_.assert200)

        resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        resourceComplexGetResponse <-
          TestApiClient
            .getJsonLdDocument(uri"/v2/resources/$resourceIri", SharedTestDataADM.anythingAdminUser)
            .flatMap(_.assert200)
        valueObject = resourceComplexGetResponse.body
                        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/freetest/v2#hasName")
                        .fold(e => throw BadRequestException(e), identity)
        valueIri: IRI = valueObject.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        valueAsString: IRI = valueObject
                               .getRequiredString("http://api.knora.org/ontology/knora-api/v2#valueAsString")
                               .fold(msg => throw BadRequestException(msg), identity)
        editValue = s"""{
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
        _ <-
          TestApiClient.putJsonLd(uri"/v2/values", editValue, SharedTestDataADM.anythingAdminUser).flatMap(_.assert200)
      } yield assertTrue(valueAsString == "The new text value")
    },
  )
}
