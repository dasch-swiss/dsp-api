/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.*
import zio.test.*
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.SearchEndpointsGetSearchE2ESpec.suite
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.RequestsUpdates.addQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

object SearchEndpointsGetSearchE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/beol-data.ttl", name = "http://www.knora.org/data/0801/beol"),
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
    RdfDataObject(path = "test_data/project_data/books-data.ttl", name = "http://www.knora.org/data/0001/books"),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-admin.ttl",
      name = "http://www.knora.org/data/admin",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-onto.ttl",
      name = "http://www.knora.org/ontology/0666/gravsearchtest1",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-data.ttl",
      name = "http://www.knora.org/data/0666/gravsearchtest1",
    ),
  )

  private def loadFile(name: String) = TestDataFileUtil.readTestData("searchR2RV2", name)

  private def verifySearchResult(
    searchTerm: String,
    expectedFile: String,
    user: Option[User] = None,
    f: RequestUpdate[String] = identity,
  ) = for {
    response <- user match {
                  case None    => TestApiClient.getJsonLd(uri"/v2/search/$searchTerm", f)
                  case Some(u) => TestApiClient.getJsonLd(uri"/v2/search/$searchTerm", u, f)
                }
    actual   <- response.assert200.mapAttempt(RdfModel.fromJsonLD)
    expected <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
  } yield assertTrue(actual == expected)

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
      suite("GET /v2/search/count")(
        test("perform a count query for a fulltext search for 'Narr'") {
          TestApiClient
            .getJsonLdDocument(uri"/v2/search/count/Narr")
            .flatMap(assert200)
            .flatMap(getCount)
            .map(actual => assertTrue(actual == 136))
        },
        test("perform a count query for a fulltext search for 'Dinge'") {
          TestApiClient
            .getJsonLdDocument(uri"/v2/search/count/Dinge", anythingUser1)
            .flatMap(assert200)
            .flatMap(getCount)
            .map(actual => assertTrue(actual == 1))
        },
      ),
    )

  private def getCount(jsonLd: JsonLDDocument) =
    ZIO.fromEither(jsonLd.body.getRequiredInt("http://schema.org/numberOfItems"))
}
