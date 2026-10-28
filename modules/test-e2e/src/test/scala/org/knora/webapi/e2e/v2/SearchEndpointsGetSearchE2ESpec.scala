/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.ResponseCheckerV2.checkSearchResponseNumberOfResults
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.IriNode
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.RequestsUpdates.addQueryParam
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

@RunWith(classOf[DspZTestJUnitRunner])
class SearchEndpointsGetSearchE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  private def loadFile(name: String) = TestDataFileUtil.readTestData("searchR2RV2", name)

  private def verifySearchResult(
    searchTerm: String,
    expectedFile: String,
    user: Option[User] = None,
    f: RequestUpdate[String] = identity,
  ) = for {
    response <- TestApiClient.getJsonLd(uri"/v2/search/$searchTerm", user, f)
    actual   <- response.assert200.mapAttempt(RdfModel.fromJsonLD)
    expected <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
  } yield assertTrue(actual == expected)

  private def verifySearchCount(
    searchTerm: String,
    expectedCount: Int,
    user: Option[User] = None,
    f: RequestUpdate[JsonLDDocument] = identity,
  ) = for {
    response    <- TestApiClient.getJsonLdDocument(uri"/v2/search/count/$searchTerm", user, f)
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield assertTrue(actualCount == expectedCount)

  private def verifySearchByLabel(
    label: String,
    expectedFile: String,
    f: RequestUpdate[String],
  ) = for {
    actual   <- searchByLabel(label, f)
    expected <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
  } yield assertTrue(actual == expected)

  private def searchByLabel(label: String, f: RequestUpdate[String]) = for {
    response <- TestApiClient.getJsonLd(uri"/v2/searchbylabel/$label", None, f)
    model    <- response.assert200.mapAttempt(RdfModel.fromJsonLD)
  } yield model

  private def searchByLabelCount(label: String, f: RequestUpdate[JsonLDDocument] = identity) = for {
    response <- TestApiClient.getJsonLdDocument(uri"/v2/searchbylabel/count/$label", None, f)
    jsonLd   <- response.assert200
    count    <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield count

  private def iriSubjects(model: RdfModel): Set[String] = model.getSubjects.collect { case iri: IriNode => iri.iri }

  override def e2eSpec =
    suite("SearchEndpoints")(
      suite("GET /v2/search")(
        test("perform a fulltext search for 'Narr'") {
          verifySearchResult("Narr", "NarrFulltextSearch.jsonld")
        },
        test("perform a fulltext search for 'Ding'") {
          verifySearchResult("Ding", "searchResponseWithHiddenResource.jsonld")
        },
        test("perform a fulltext search for 'Dinge' (in the complex schema)") {
          verifySearchResult("Dinge", "DingeFulltextSearch.jsonld", Some(anythingUser1))
        },
        test("perform a fulltext search for 'Dinge' (in the simple schema)") {
          verifySearchResult("Dinge", "DingeFulltextSearchSimple.jsonld", Some(anythingUser1), addSimpleSchemaHeader)
        },
        test("perform a fulltext search for 'Bonjour'") {
          verifySearchResult("Bonjour", "LanguageFulltextSearch.jsonld", Some(anythingUser1))
        },
        test("do a fulltext search for the term 'text' marked up as a paragraph") {
          verifySearchResult(
            "text",
            "ThingWithRichtextWithTermTextInParagraph.jsonld",
            f = addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag"),
          )
        },
        test("do a fulltext search count query for the term 'text' marked up as italic") {
          verifySearchResult(
            "text",
            "ThingWithRichtextWithTermTextInParagraph.jsonld",
            f = addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffItalicTag"),
          )
        },
        test("do a fulltext search for the terms 'interesting' and 'text' marked up as italic") {
          verifySearchResult(
            "interesting text",
            "ThingWithRichtextWithTermTextInParagraph.jsonld",
            f = addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffItalicTag"),
          )
        },
        test("do a fulltext search for the terms 'interesting' and 'boring' marked up as italic") {
          val searchTerm = "interesting boring"
          TestApiClient
            .getJsonLd(
              uri"/v2/search/$searchTerm",
              addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffItalicTag"),
            )
            .flatMap(_.assert200)
            // there is no single italic element that contains both 'interesting' and 'boring':
            .mapAttempt(checkSearchResponseNumberOfResults(_, 0))
            .as(assertCompletes)
        },
        test("perform a fulltext query for a search value containing a single character wildcard") {
          verifySearchResult("Unif?rm", "ThingUniform.jsonld", Some(anythingUser1))
        },
        test("perform a fulltext query for a search value containing a multiple character wildcard") {
          verifySearchResult("Unif*m", "ThingUniform.jsonld", Some(anythingUser1))
        },
        test("return files attached to full-text search results") {
          verifySearchResult("p7v", "FulltextSearchWithImage.jsonld", f = addQueryParam("returnFiles", "true"))
        },
        test("not accept a fulltext query containing http://api.knora.org") {
          val invalidSearchString = "PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>"
          TestApiClient
            .getJsonLd(uri"/v2/search/$invalidSearchString")
            .flatMap(_.assert400)
            .map(actual =>
              assertTrue(
                actual.contains("It looks like you are submitting a Gravsearch request to a full-text search route"),
              ),
            )
        },
      ),
      suite("GET /v2/search/count/:term")(
        test("perform a count query for a fulltext search for 'Narr'") {
          verifySearchCount("Narr", 136)
        },
        test("perform a count query for a fulltext search for 'Dinge'") {
          verifySearchCount("Dinge", 1, Some(anythingUser1))
        },
        test("do a fulltext search count query for the term 'text' marked up as a paragraph") {
          verifySearchCount(
            "text",
            1,
            f = addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag"),
          )
        },
        test("do a fulltext search count query for the term 'text' marked up as italic") {
          verifySearchCount(
            "text",
            1,
            f = addQueryParam("limitToStandoffClass", "http://api.knora.org/ontology/standoff/v2#StandoffItalicTag"),
          )
        },
      ),
      suite("GET /v2/searchbylabel/:label")(
        test("perform a searchbylabel search for the label 'Treasure Island' with search string 'Treasure Island'") {
          verifySearchByLabel(
            "Treasure Island",
            "SearchbylabelSimple.jsonld",
            addQueryParam("limitToResourceClass", "http://0.0.0.0:3333/ontology/0001/books/v2#Book") andThen
              addQueryParam("offset", "0"),
          )
        },
        test("perform a searchbylabel search for the label 'Treasure Island' with search string 'Treasure'") {
          verifySearchByLabel(
            "Treasure",
            "SearchbylabelSimple.jsonld",
            addQueryParam("limitToResourceClass", "http://0.0.0.0:3333/ontology/0001/books/v2#Book") andThen
              addQueryParam("offset", "0"),
          )
        },
        test("perform a searchbylabel search for a label with special characters") {
          verifySearchByLabel(
            """this .,:; is + a - test & with special ( characters ) in [] {} | the || label?!""",
            "SearchbylabelSpecialCharacters.jsonld",
            addQueryParam("limitToResourceClass", "http://0.0.0.0:3333/ontology/0001/books/v2#Book") andThen
              addQueryParam("offset", "0"),
          )
        },
        test("perform a searchbylabel search for a label that starts with a slash `/`") {
          verifySearchByLabel(
            """/slashes""",
            "SearchbylabelSlashes.jsonld",
            addQueryParam("limitToResourceClass", "http://0.0.0.0:3333/ontology/0001/books/v2#Book") andThen
              addQueryParam("offset", "0"),
          )
        },
        test("perform a searchbylabel search for the label 'Treasure Island' but providing the wrong class") {
          for {
            actual <- searchByLabel(
                        "Treasure",
                        addQueryParam("limitToResourceClass", "http://0.0.0.0:3333/ontology/0001/books/v2#Page") andThen
                          addQueryParam("offset", "0"),
                      )
          } yield assertTrue(actual == RdfModel.fromJsonLD("{}"))
        },
        // A project restriction scopes the label lookup to the project's data graph, so a fixture only counts if it is
        // loaded into that graph. books-data is not (it sits in .../0001/books, not .../0001/anything), so these cases
        // use incunabula and anything. "Narrenschiff" has label hits only in incunabula.
        test("searchbylabel restricted to a project") {
          for {
            inIncunabula <- searchByLabel("Narrenschiff", addQueryParam("limitToProject", incunabulaProjectIri.value))
            inAnything   <- searchByLabel("Narrenschiff", addQueryParam("limitToProject", anythingProjectIri.value))
          } yield assertTrue(
            iriSubjects(inIncunabula).nonEmpty,
            iriSubjects(inIncunabula).forall(_.startsWith("http://rdfh.ch/0803/")),
            inAnything == RdfModel.fromJsonLD("{}"),
          )
        },
        test("searchbylabel count restricted to a project") {
          for {
            unrestricted <- searchByLabelCount("Narrenschiff")
            inIncunabula <-
              searchByLabelCount("Narrenschiff", addQueryParam("limitToProject", incunabulaProjectIri.value))
            inAnything <- searchByLabelCount("Narrenschiff", addQueryParam("limitToProject", anythingProjectIri.value))
          } yield assertTrue(unrestricted > 0, inIncunabula == unrestricted, inAnything == 0)
        },
        test("searchbylabel restricted to an unknown project") {
          val unknownProject = "http://rdfh.ch/projects/9999"
          for {
            page  <- searchByLabel("Narrenschiff", addQueryParam("limitToProject", unknownProject))
            count <- searchByLabelCount("Narrenschiff", addQueryParam("limitToProject", unknownProject))
          } yield assertTrue(page == RdfModel.fromJsonLD("{}"), count == 0)
        },
      ),
    )
}
