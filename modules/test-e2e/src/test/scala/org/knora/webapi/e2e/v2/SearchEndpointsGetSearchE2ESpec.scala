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
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
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

  override def e2eSpec =
    suite("SearchEndpoints")(
      suite("GET /v2/search")(
        test("perform a fulltext search for 'Narr'") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/Narr").flatMap(_.assert200)
            expected <- loadFile("NarrFulltextSearch.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("perform a fulltext search for 'Ding'") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/Ding").flatMap(_.assert200)
            expected <- loadFile("searchResponseWithHiddenResource.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("perform a fulltext search for 'Dinge' (in the complex schema)") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/Dinge", anythingUser1).flatMap(_.assert200)
            expected <- loadFile("DingeFulltextSearch.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("perform a fulltext search for 'Dinge' (in the simple schema)") {
          for {
            actual <- TestApiClient
                        .getJsonLd(uri"/v2/search/Dinge", anythingUser1, addSimpleSchemaHeader)
                        .flatMap(_.assert200)
            expected <- loadFile("DingeFulltextSearchSimple.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("perform a fulltext query for a search value containing a single character wildcard") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/Unif%3Frm", anythingUser1).flatMap(_.assert200)
            expected <- loadFile("ThingUniform.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("perform a fulltext query for a search value containing a multiple character wildcard") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/Unif*m", anythingUser1).flatMap(_.assert200)
            expected <- loadFile("ThingUniform.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
        },
        test("return files attached to full-text search results") {
          for {
            actual   <- TestApiClient.getJsonLd(uri"/v2/search/p7v?returnFiles=true").flatMap(_.assert200)
            expected <- loadFile("FulltextSearchWithImage.jsonld")
          } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
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
