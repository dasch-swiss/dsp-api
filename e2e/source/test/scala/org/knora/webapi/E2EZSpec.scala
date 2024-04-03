/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._
import zio.test.*

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  private val testLayers =
    util.Logger.text() >>> core.LayersTest.integrationTestsWithFusekiTestcontainers()

  def rdfDataObjects: List[RdfDataObject] = List.empty[RdfDataObject]

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
    @@ TestAspect.withLiveEnvironment

  def sendGetRequest(url: String, token: Option[String] = None): URIO[env, Response] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).orDie
      _        <- ZIO.logDebug(s"GET   ${urlFull.encode}")
      bearer    = token.map(Header.Authorization.Bearer(_)).toList
      response <- client.url(urlFull).addHeaders(Headers(bearer)).get("").orDie
    } yield response

  def sendGetRequestStringOrFail(url: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendGetRequest(url, token)
      data     <- response.body.asString.orDie
      _        <- ZIO.fail(s"Failed request: Status ${response.status} - $data").when(response.status != Status.Ok)
    } yield data

  def sendGetRequestAsOrFail[B](url: String, token: Option[String] = None)(implicit
    dec: JsonDecoder[B],
  ): ZIO[env, String, B] =
    for {
      response <- sendGetRequestStringOrFail(url, token)
      result   <- ZIO.fromEither(response.fromJson[B])
    } yield result

  def sendPostRequest(url: String, data: String, token: Option[String] = None): ZIO[env, String, Response] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).mapError(_.getMessage)
      _        <- ZIO.logDebug(s"POST  ${urlFull.encode}")
      body      = Body.fromString(data)
      bearer    = token.map(Header.Authorization.Bearer(_))
      headers   = Headers(List(Header.ContentType(MediaType.application.json)) ++ bearer.toList)
      response <- client.url(urlFull).addHeaders(headers).post("")(body).mapError(_.getMessage)
    } yield response

  def sendPostRequestStringOrFail(url: String, data: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendPostRequest(url, data, token)
      data     <- response.body.asString.mapError(_.getMessage)
      _        <- ZIO.fail(s"Failed request: Status ${response.status} - $data").when(response.status != Status.Ok)
    } yield data

  def sendPostRequestString(url: String, data: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendPostRequest(url, data, token)
      data     <- response.body.asString.mapError(_.getMessage)
    } yield data

  def getToken(email: String, password: String): ZIO[env, String, String] =
    for {
      response <-
        sendPostRequestStringOrFail(
          "/v2/authentication",
          s"""|{
              |  "email": "$email",
              |  "password": "$password"
              |}""".stripMargin,
        )
      result <- ZIO.fromEither(response.fromJson[Map[String, String]])
      token  <- ZIO.fromOption(result.get("token")).orElseFail("No token in response")
    } yield token

  def getRootToken: ZIO[env, String, String] =
    getToken("root@example.com", "test")

  def urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

  def getOntologyLastModificationDate(ontlogyIri: String): ZIO[env, String, String] = {
    val cursor = JsonCursor.field("knora-api:lastModificationDate").isObject.field("@value").isString
    for {
      responseStr <- sendGetRequestStringOrFail(s"/v2/ontologies/allentities/${urlEncode(ontlogyIri)}")
      responseAst <- ZIO.fromEither(responseStr.fromJson[Json])
      lmd         <- ZIO.fromEither(responseAst.get(cursor))
    } yield lmd.value
  }

}
