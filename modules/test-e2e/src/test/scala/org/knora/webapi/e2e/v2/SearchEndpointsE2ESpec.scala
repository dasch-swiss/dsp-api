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
import zio.ZIO

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
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

import pekko.http.javadsl.model.StatusCodes
import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.Multipart
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

/**
 * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class SearchEndpointsE2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: ExecutionContextExecutor             = system.dispatcher

  private val anythingUser       = SharedTestDataADM.anythingUser1
  private val anythingUserEmail  = anythingUser.email
  private val anythingProjectIri = SharedTestDataADM.anythingProjectIri

  private val incunabulaUser      = SharedTestDataADM.incunabulaMemberUser
  private val incunabulaUserEmail = incunabulaUser.email

  private val password = SharedTestDataADM.testPass

  private val hamletResourceIri  = new MutableTestIri
  private val timeTagResourceIri = new MutableTestIri

  override lazy val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  private def testData(filename: String): String = readTestData("searchR2RV2", filename)

  "The Search v2 Endpoint" should {
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries with type inference

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries that submit the complex schema

    "return a page of anything:Thing resources" in {
      val gravsearchQuery =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected: String = testData("PageOfThings.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an anything:Thing that has a Boolean value that is true (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     ?boolean knora-api:booleanValueAsBoolean true .
          |
          |}
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBoolean.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that may have a Boolean value that is true (submitting the complex schema)" in {
      // set OFFSET to 1 to get "Testding for extended search"
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     OPTIONAL {
          |         ?thing anything:hasBoolean ?boolean .
          |         ?boolean knora-api:booleanValueAsBoolean true .
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         ?intVal knora-api:intValueAsInt 123454321 .
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         ?intVal knora-api:intValueAsInt 999999999 .
          |     }
          |
          |} OFFSET 1""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOptionalOffset1.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the second page of results
      checkSearchResponseNumberOfResults(actual, 19)
    }

    "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both) (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     {
          |         ?thing anything:hasBoolean ?boolean .
          |
          |         ?boolean knora-api:booleanValueAsBoolean ?booleanBool .
          |
          |         FILTER(?booleanBool = true)
          |     } UNION {
          |         ?thing anything:hasDecimal ?decimal .
          |
          |         ?decimal knora-api:decimalValueAsDecimal "2.1"^^xsd:decimal .
          |     }
          |
          |} OFFSET 0
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOrDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeit' using the regex function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString ?titleStr .
          |
          |        FILTER regex(?titleStr, "Zeit", "i")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeit.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' using the match function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        FILTER knora-api:matchText(?title, "Zeitglöcklein")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        FILTER knora-api:matchText(?title, "Zeitglöcklein AND Lebens")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing with a list value (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing a anything:Thing .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithListValue.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "search for a text in a particular language (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     ?text knora-api:textValueHasLanguage "fr" .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for a specific text using the lang function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     ?text knora-api:valueAsString "Bonjour" .
          |
          |     ?text knora-api:textValueHasLanguage "fr" .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for link objects that link to an incunabula book (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |
          |CONSTRUCT {
          |     ?linkObj knora-api:isMainResource true .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |} WHERE {
          |     ?linkObj a knora-api:LinkObj .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |     ?book a incunabula:book .
          |
          |     ?book incunabula:title ?title .
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LinkObjectsToBooks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "do a Gravsearch query for a letter that links to a specific person via two possible properties (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithAuthor.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to a person with a specified name (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        ?name knora-api:valueAsString "Meier" .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithPersonWithName.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to another person with a specified name (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        ?name knora-api:valueAsString "Muster" .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a single resource specified by its IRI (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true ;
          |         anything:hasText ?text ;
          |         anything:hasInteger ?integer .
          |
          |} WHERE {
          |     BIND(<http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw> AS ?thing)
          |
          |     ?thing a anything:Thing .
          |     ?thing anything:hasText ?text .
          |     ?thing anything:hasInteger ?integer .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingByIriWithRequestedValues.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for a letter and get information about the persons associated with it (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithAuthorWithInformation.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |        FILTER(?seqnumInt <= 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("incomingPagesForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |        FILTER(?seqnumInt <= 10)
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi' (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("regionsOfZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query in the complex schema that uses knora-api:isMainResource in the simple schema" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api-simple:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query in the complex schema that uses a Knora property in the simple schema" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX incunabula-simple: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula-simple:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query that uses a string literal in the CONSTRUCT clause" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
                """.stripMargin

      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query in the complex schema with a variable in the CONSTRUCT clause referring to a non-property entity that isn't a resource or value" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |    FILTER(?titleStr = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "search for a list value that refers to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingReferringToSpecificListNode.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a list value that does not refer to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        FILTER NOT EXISTS {
          |
          |         ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |        }
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingNotReferringToSpecificListNode.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a list value that refers to a particular list node that has subnodes (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingReferringToSpecificListNodeWithSubnodes.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a beol:letter with list value that refers to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:hasSubject ?subject .
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:hasSubject ?subject .
          |
          |        ?subject knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("letterWithSubject.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, ?otherThing)
          |    ?otherThing a anything:Thing .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function, referring to the target resource in the function call only (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, ?otherThing)
          |
          |    # Note that ?otherThing is only used as a argument in the function, not in any other statement
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, <http://rdfh.ch/0001/a-thing>)
          |    <http://rdfh.ch/0001/a-thing> a anything:Thing .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinksToSpecificThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource, referring to the target resource in the function call only (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, <http://rdfh.ch/0001/a-thing>)
          |
          |    # Note that <http://rdfh.ch/0001/a-thing> is only used as a argument in the function, not in any other statement
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinksToSpecificThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for matching words in a particular type of standoff tag (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasRichtext ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasRichtext ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a standoff:StandoffItalicTag .
          |    FILTER knora-api:matchTextInStandoff(?text, ?standoffTag, "interesting text")
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ThingWithRichtextWithTermTextInParagraph.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff date tag indicating a date in a particular range (submitting the complex schema)" in {
      // First, create a standoff-to-XML mapping that can handle standoff date tags.
      val mappingFileToSend = Paths.get("test_data/test_route/texts/mappingForHTML.xml")
      val paramsCreateHTMLMappingFromXML =
        s"""
           |{
           |    "knora-api:mappingHasName": "HTMLMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "mapping for HTML",
           |    "@context": {
           |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
           |    }
           |}
                """.stripMargin

      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, paramsCreateHTMLMappingFromXML),
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, mappingFileToSend),
          Map("filename" -> mappingFileToSend.getFileName.toString),
        ),
      )

      // send mapping xml to route
      val _ = getResponseAsString(
        Post(s"$baseApiUrl/v2/mapping", formDataMapping) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password),
        ),
      )

      // Next, create a resource with a text value containing a standoff date tag.
      val xmlForJson = JsString(
        FileUtil.readTextFile(Paths.get("test_data/test_route/texts/HTML.xml")),
      ).compactPrint
      val requestBody =
        s"""{
           |  "@id" : "http://rdfh.ch/0001/a-thing",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : $xmlForJson,
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$anythingProjectIri/mappings/HTMLMapping"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val _ = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/values",
          HttpEntity(RdfMediaTypes.`application/ld+json`, requestBody),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )

      // Finally, do a Gravsearch query that finds the date tag.

      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffEventTag .
          |    ?standoffEventTag a anything:StandoffEventTag .
          |    FILTER(knora-api:toSimpleDate(?standoffEventTag) = "GREGORIAN:2016-12 CE"^^knora-api-simple:Date)
          |}
                """.stripMargin

      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      assert(actual.contains("we will have a party"))
    }

    "search for a standoff tag using knora-api:standoffTagHasStartAncestor (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffDateTag .
          |    ?standoffDateTag a knora-api:StandoffDateTag .
          |    FILTER(knora-api:toSimpleDate(?standoffDateTag) = "GREGORIAN:2016-12-24 CE"^^knora-api-simple:Date)
          |    ?standoffDateTag knora-api:standoffTagHasStartAncestor ?standoffParagraphTag .
          |    ?standoffParagraphTag a standoff:StandoffParagraphTag .
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      assert(actual.contains("we will have a party"))
    }

    "reject a link value property in a query in the simple schema" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |    ?page incunabula:partOfValue ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:title ?title .
          |    ?page a incunabula:page .
          |    ?page incunabula:partOfValue ?book .
          |}
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.NOT_FOUND)
      assert(responseToString(actual).contains("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOfValue"))
    }

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

    "find a resource with two different incoming links" in {
      // Create the target resource.
      val targetResource: String =
        """{
          |  "@type" : "anything:BlueThing",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "blue thing with incoming links",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}""".stripMargin
      val targetResourceIri = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TestClientService](
          _.getResponseJsonLD(
            Post(
              s"$baseApiUrl/v2/resources",
              HttpEntity(RdfMediaTypes.`application/ld+json`, targetResource),
            ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
          ).map(_.body.getRequiredString(JsonLDKeywords.ID).getOrElse(throw AssertionError("No IRI returned"))),
        ),
      )

      val sourceResource1: String =
        s"""{
           |  "@type" : "anything:BlueThing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |    "anything:hasBlueThingValue" : {
           |    "@type" : "knora-api:LinkValue",
           |        "knora-api:linkValueHasTargetIri" : {
           |        "@id" : "$targetResourceIri"
           |    }
           |  },
           |  "rdfs:label" : "blue thing with link to other blue thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val _ = checkResponseOK(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, sourceResource1),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )

      val sourceResource2: String =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |    "anything:hasOtherThingValue" : {
           |    "@type" : "knora-api:LinkValue",
           |        "knora-api:linkValueHasTargetIri" : {
           |        "@id" : "$targetResourceIri"
           |    }
           |  },
           |  "rdfs:label" : "thing with link to blue thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val _ = checkResponseOK(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, sourceResource2),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )

      val gravsearchQuery =
        s"""
           |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?targetThing knora-api:isMainResource true .
           |    ?firstIncoming anything:hasBlueThing ?targetThing .
           |    ?secondIncoming anything:hasOtherThing ?targetThing .
           |} WHERE {
           |    ?targetThing a anything:BlueThing .
           |    ?firstIncoming anything:hasBlueThing ?targetThing .
           |    ?secondIncoming anything:hasOtherThing ?targetThing .
           |}
                """.stripMargin

      val responseJsonDoc = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val searchResultIri = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(searchResultIri == targetResourceIri)
    }

    "search for an anything:Thing with a time value (using the simple schema)" in {
      val gravsearchQuery =
        s"""
           |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasTimeStamp ?timeStamp .
           |} WHERE {
           |    ?thing a anything:Thing .
           |    ?thing anything:hasTimeStamp ?timeStamp .
           |    FILTER(?timeStamp > "2019-08-30T10:45:26.365863Z"^^xsd:dateTimeStamp)
           |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithTimeStamp.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get a resource with a link to another resource that the user doesn't have permission to see" in {
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?mainThing knora-api:isMainResource true .
           |    ?mainThing anything:hasOtherThing ?hiddenThing .
           |    ?hiddenThing anything:hasInteger ?intValInHiddenThing .
           |    ?mainThing anything:hasOtherThing ?visibleThing .
           |    ?visibleThing anything:hasInteger ?intValInVisibleThing .
           |} WHERE {
           |    ?mainThing a anything:Thing .
           |    ?mainThing anything:hasOtherThing ?hiddenThing .
           |    ?hiddenThing anything:hasInteger ?intValInHiddenThing .
           |    ?intValInHiddenThing knora-api:intValueAsInt 123454321 .
           |    ?mainThing anything:hasOtherThing ?visibleThing .
           |    ?visibleThing anything:hasInteger ?intValInVisibleThing .
           |    ?intValInVisibleThing knora-api:intValueAsInt 543212345 .
           |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ThingWithHiddenThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "not return duplicate results when there are UNION branches with different variables" in {
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasInteger ?int .
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasText ?text .
           |} WHERE {
           |    ?thing a knora-api:Resource .
           |    ?thing a anything:Thing .
           |    ?thing anything:hasInteger ?int .
           |    ?int knora-api:intValueAsInt 1 .
           |
           |    {
           |        ?thing anything:hasRichtext ?richtext .
           |        FILTER knora-api:matchText(?richtext, "test")
           |    }
           |    UNION
           |    {
           |        ?thing anything:hasText ?text .
           |        FILTER knora-api:matchText(?text, "test")
           |    }
           |}
           |ORDER BY (?int)""".stripMargin

      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkSearchResponseNumberOfResults(actual, 1)
      val expected = testData("ThingFromQueryWithUnion.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject an ORDER by containing a variable that's not bound at the top level of the WHERE clause" in {
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasInteger ?int .
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasText ?text .
           |} WHERE {
           |    ?thing a knora-api:Resource .
           |    ?thing a anything:Thing .
           |
           |    {
           |        ?thing anything:hasRichtext ?richtext .
           |        FILTER knora-api:matchText(?richtext, "test")
           |
           |		?thing anything:hasInteger ?int .
           |		?int knora-api:intValueAsInt 1 .
           |    }
           |    UNION
           |    {
           |        ?thing anything:hasText ?text .
           |        FILTER knora-api:matchText(?text, "test")
           |
           |		?thing anything:hasInteger ?int .
           |		?int knora-api:intValueAsInt 1 .
           |    }
           |}
           |ORDER BY (?int)""".stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
      assert(
        responseToString(actual).contains(
          "Variable ?int is used in ORDER by, but is not bound at the top level of the WHERE clause",
        ),
      )
    }

    "reject a FILTER in a UNION that uses a variable that's out of scope" in {
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasInteger ?int .
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasText ?text .
           |} WHERE {
           |    ?thing a knora-api:Resource .
           |    ?thing a anything:Thing .
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasInteger ?int .
           |    ?int knora-api:intValueAsInt 1 .
           |
           |    {
           |        FILTER knora-api:matchText(?richtext, "test")
           |    }
           |    UNION
           |    {
           |        ?thing anything:hasText ?text .
           |        FILTER knora-api:matchText(?text, "test")
           |    }
           |}
           |ORDER BY (?int)""".stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
      assert(
        responseToString(actual).contains(
          "One or more variables used in a filter have not been bound in the same UNION block: ?richtext",
        ),
      )
    }

    "search for a resource containing a time value tag" in {
      // Create a resource containing a time value.

      val xmlStr =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text documentType="html">
          |    <p>The timestamp for this test is <span class="timestamp" data-timestamp="2020-01-27T08:31:51.503187Z">27 January 2020</span>.</p>
          |</text>
          |""".stripMargin

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${JsString(xmlStr).compactPrint},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "$anythingProjectIri/mappings/HTMLMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "thing with timestamp in markup",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val resourceIri = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TestClientService](
          _.getResponseJsonLD(
            Post(
              s"$baseApiUrl/v2/resources",
              HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity),
            ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
          ).map(_.body.getRequiredString(JsonLDKeywords.ID).getOrElse(throw AssertionError("No IRI returned"))),
        ),
      )
      timeTagResourceIri.set(resourceIri)

      // Search for the resource.
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffTimeTag .
          |    ?standoffTag knora-api:timeValueAsTimeStamp ?timeStamp .
          |    FILTER(?timeStamp > "2020-01-27T08:31:51Z"^^xsd:dateTimeStamp && ?timeStamp < "2020-01-27T08:31:52Z"^^xsd:dateTimeStamp)
          |}
                """.stripMargin

      val actual = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val actualResourceIri: IRI = actual.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(actualResourceIri == timeTagResourceIri.get)

      val xmlFromResponse: String = actual.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
        .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(xmlStr)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "search for an rdfs:label using a literal in the simple schema" in {
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
          |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using a literal in the complex schema" in {
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
          |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using a variable in the simple schema" in {
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
          |    ?book rdfs:label ?label .
          |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
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
