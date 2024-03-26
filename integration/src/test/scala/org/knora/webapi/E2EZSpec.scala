package org.knora.webapi

import zio.*
import zio.http.*
import zio.json.*
import zio.test.*

import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTest
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  private lazy val testLayers     = util.Logger.text() >>> core.LayersTest.integrationTestsWithFusekiTestcontainers()
  private lazy val rdfDataObjects = List.empty[RdfDataObject]

  type env = LayersTest.DefaultTestEnvironmentWithoutSipi with Client with Scope

  private def prepare: ZIO[AppServer.AppServerEnvironment, Throwable, AppServer] = for {
    appServer <- AppServer.init()
    _         <- appServer.start(requiresAdditionalRepositoryChecks = false, requiresIIIFService = false).orDie
    _         <- prepareRepository(rdfDataObjects)
  } yield appServer

  def withResettedTriplestore =
    TestAspect.before(prepareRepository(rdfDataObjects))

  def e2eSpec: Spec[env, Any]

  override def spec = (
    e2eSpec
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
  ).provideShared(testLayers, Client.default, Scope.default)

  def sendGetRequestString(url: String): ZIO[env, String, String] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).mapError(_.getMessage)
      response <- client.url(urlFull).get("/").mapError(_.getMessage)
      data     <- response.body.asString.mapError(_.getMessage)
    } yield data

  def sendGetRequest[B](url: String)(implicit dec: JsonDecoder[B]): ZIO[env, String, B] =
    for {
      response <- sendGetRequestString(url)
      result   <- ZIO.fromEither(response.fromJson[B])
    } yield result

  def sendPostRequestString(url: String, data: String): ZIO[env, String, String] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).mapError(_.getMessage)
      body      = Body.fromString(data)
      header    = Header.ContentType(MediaType.application.json)
      response <- client.url(urlFull).addHeader(header).post("")(body).mapError(_.getMessage)
      data     <- response.body.asString.mapError(_.getMessage)
    } yield data

  def getToken(email: String, password: String): ZIO[env, String, String] =
    for {
      response <-
        sendPostRequestString(
          "/v2/authentication",
          s"""|{
              |  "email": "$email",
              |  "password": "$password"
              |}""".stripMargin,
        )
      result <- ZIO.fromEither(response.fromJson[Map[String, String]])
      token  <- ZIO.fromOption(result.get("token")).orElseFail("No token in response")
    } yield token

}
