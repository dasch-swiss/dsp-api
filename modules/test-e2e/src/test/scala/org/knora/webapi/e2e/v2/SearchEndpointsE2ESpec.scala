/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import spray.json.*

import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ResponseCheckerV2.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

/**
 * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class SearchEndpointsE2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: ExecutionContextExecutor             = system.dispatcher

  private val anythingUser      = SharedTestDataADM.anythingUser1
  private val anythingUserEmail = anythingUser.email

  private val password = SharedTestDataADM.testPass

  private val hamletResourceIri = new MutableTestIri

  override lazy val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  private def testData(filename: String): String = readTestData("searchR2RV2", filename)

  "The Search v2 Endpoint" should {
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries with type inference

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries that submit the complex schema

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasRichtext" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${JsString(hamletXml).compactPrint},
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
      val resourceCreateResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "search for the large text and its markup and receive it as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasRichtext ?text .
           |} WHERE {
           |    BIND(<${hamletResourceIri.get}> AS ?thing)
           |    ?thing a anything:Thing .
           |    ?thing anything:hasRichtext ?text .
           |}""".stripMargin
      val searchResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val xmlFromResponse: String = searchResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "search for an rdfs:label using a variable in the complex schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?label .
          |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using knora-api:matchLabel in the simple schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    FILTER knora-api:matchLabel(?book, "Zeitglöcklein")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using knora-api:matchLabel in the complex schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    FILTER knora-api:matchLabel(?book, "Zeitglöcklein")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using the regex function in the simple schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using the regex function in the complex schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares two variables representing resources (in the simple schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != ?person2) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares two variables representing resources (in the complex schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != ?person2) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares a variable with a resource IRI (in the simple schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares a variable with a resource IRI (in the complex schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for anything:Thing that doesn't have a boolean property (FILTER NOT EXISTS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  FILTER NOT EXISTS {
          |    ?thing anything:hasBoolean ?bool .
          |  }
          |}
          |
            """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkSearchResponseNumberOfResults(actual, 24)
    }

    "search for anything:Thing that doesn't have a link property (FILTER NOT EXISTS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  FILTER NOT EXISTS {
          |    ?thing anything:hasOtherThing ?otherThing .
          |  }
          |}
          |
            """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkSearchResponseNumberOfResults(actual, 24)
    }
  }
}
