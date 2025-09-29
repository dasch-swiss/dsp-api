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
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.ResponseOps
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

object SearchEndpointE2ESpecHelper {
  val rdfDataObjects: List[RdfDataObject] = List(
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

  def loadFile(filename: String): ZIO[TestDataFileUtil, Throwable, String] =
    TestDataFileUtil.readTestData("searchR2RV2", filename)

  private def fileExists(filename: String): ZIO[TestDataFileUtil, Throwable, Boolean] =
    TestDataFileUtil.readTestData("searchR2RV2", filename).as(true).orElseSucceed(false)

  def verifyQueryResult(
    query: String,
    expectedFile: String,
    f: RequestUpdate[String] = identity,
    limitToProject: Option[String] = None,
  ): ZIO[TestDataFileUtil & TestApiClient, Throwable, TestResult] =
    postGravsearchQuery(query, f = f).flatMap(compare(_, expectedFile))

  def verifyQueryResultWithUser(
    query: String,
    expectedFile: String,
    user: User,
    limitToProject: Option[String] = None,
  ): ZIO[TestDataFileUtil & TestApiClient, Throwable, TestResult] =
    postGravsearchQuery(query, Some(user), limitToProject = limitToProject).flatMap(compare(_, expectedFile))

  // use this variant to create the expected result file if it doesn't exist yet
  def verifyQueryResultOrWrite(
    query: String,
    expectedFile: String,
    user: User,
    limitToProject: Option[String] = None,
  ): ZIO[TestDataFileUtil & TestApiClient, Throwable, TestResult] =
    postGravsearchQuery(query, Some(user), limitToProject = limitToProject).flatMap(compareOrWrite(_, expectedFile))

  def postGravsearchQuery(
    query: String,
    user: Option[User] = None,
    f: RequestUpdate[String] = identity,
    limitToProject: Option[String] = None,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    TestApiClient.postSparql(uri"/v2/searchextended".addParam("limitToProject", limitToProject), query, user, f)

  private def compareOrWrite(response: Response[Either[String, String]], expectedFile: String) =
    for {
      resultJsonLd <- response.assert200
      fileExists   <- fileExists(expectedFile)
      result <- if (fileExists) compare(response, expectedFile)
                else
                  TestDataFileUtil
                    .writeTestData("searchR2RV2", expectedFile, resultJsonLd)
                    .as(assertTrue(false).label(s"Expected result file $expectedFile did not exist, created it."))

    } yield result

  private def compare(response: Response[Either[String, String]], expectedFile: String) =
    for {
      resultJsonLd <- response.assert200
      actual       <- ZIO.attempt(RdfModel.fromJsonLD(resultJsonLd))
      expected     <- loadFile(expectedFile).map(RdfModel.fromJsonLD)
    } yield assertTrue(actual == expected)
}
