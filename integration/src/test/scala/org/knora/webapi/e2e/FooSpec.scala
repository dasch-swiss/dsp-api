package org.knora.webapi.e2e

// import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.IntegrationSpecZio

object FooSpec extends IntegrationSpecZio {

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

  override def e2eSpec = suite("FooSpec")(
    // test("sendRequest") {
    //   for {
    //     response      <- sendRequest[TestRequest, TestResponse](TestRequest(1, "test"))
    //     expectedResult = TestResponse(1, "test")
    //   } yield assertTrue(response == expectedResult)
    // },
    versionTest,
    healthTest,
    fooTest,
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

  val fooTest = test("foo") {
    for {
      _ <- getToken("root@example.com", "test").debug
    } yield assertTrue(true)
  }
}
