/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.Response
import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

object SearchEndpointE2ESpecHelper {
  val rdfDataObjects: List[RdfDataObject] = anythingRdfTestdata ++ incunabulaRdfTestdata ++ booksRdfTestdata ++
    List(
      RdfDataObject(path = "test_data/project_data/beol-data.ttl", name = "http://www.knora.org/data/0801/beol"),
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

  def loadFile(filename: String): ZIO[TestDataFileUtil, Nothing, String] =
    TestDataFileUtil.readTestData("searchR2RV2", filename)

  def verifyQueryResult(
    query: String,
    expectedFile: String,
    f: RequestUpdate[String] = identity,
  ): ZIO[TestDataFileUtil & TestApiClient, Throwable, TestResult] =
    postGravsearchQuery(query, f = f).flatMap(compare(_, expectedFile))

  def verifyQueryResult(
    query: String,
    expectedFile: String,
    user: User,
  ): ZIO[TestDataFileUtil & TestApiClient, Throwable, TestResult] =
    postGravsearchQuery(query, Some(user)).flatMap(compare(_, expectedFile))

  def postGravsearchQuery(
    query: String,
    user: Option[User] = None,
    f: RequestUpdate[String] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    TestApiClient.postSparql(uri"/v2/searchextended", query, user, f)

  private def compare(response: Response[Either[String, String]], expectedFile: String) =
    for {
      resultJsonLd <- response.assert200
      actual       <- ZIO.attempt(RdfModel.fromJsonLD(resultJsonLd))
      expected     <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
    } yield assertTrue(actual == expected)
}
