/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import zio.json._
import zio.test._

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object SampleE2ESpec extends E2EZSpec {

  // add something to this list, if particular test data should be loaded
  override def rdfDataObjects: List[RdfDataObject] = List.empty

  case class VersionResponse(
    buildCommit: String,
    buildTime: String,
    fuseki: String,
    name: String,
    pekkoHttp: String,
    scala: String,
    sipi: String,
    webapi: String,
  )
  object VersionResponse {
    implicit val decoder: JsonDecoder[VersionResponse] = DeriveJsonDecoder.gen[VersionResponse]
  }

  case class HealthResponse(name: String, severity: String, status: Boolean, message: String)
  object HealthResponse {
    implicit val decoder: JsonDecoder[HealthResponse] = DeriveJsonDecoder.gen[HealthResponse]
  }

  override def e2eSpec = suite("SampleE2ESpec")(
    versionTest,
    healthTest,
    getTokenTest @@ withResettedTriplestore, // this is not actually needed, it just shows how to use it
  )

  val versionTest = test("check version endpoint") {
    for {
      response <- sendGetRequest[VersionResponse]("/version")
    } yield assertTrue(
      response.name == "version",
      response.webapi.startsWith("v"),
      response.scala.startsWith("2.13."),
    )
  }

  val healthTest = test("check health endpoint") {
    for {
      response <- sendGetRequest[HealthResponse]("/health")
      expected  = HealthResponse("AppState", "non fatal", true, "Application is healthy")
    } yield assertTrue(response == expected)
  }

  val getTokenTest = test("foo") {
    for {
      token <- getToken("root@example.com", "test")
    } yield assertTrue(token.nonEmpty)
  }
}
