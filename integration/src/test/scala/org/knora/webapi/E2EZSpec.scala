/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*
import zio.http.*
import zio.json.*
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.json.ast.JsonCursor
import zio.test.*

import org.knora.webapi.core.Db
import org.knora.webapi.core.LayersTest
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.KnoraIri

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  // test data
  val rootUser: User = org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser

  private val testLayers = util.Logger.text() >>> LayersTest.layer

  def rdfDataObjects: List[RdfDataObject] = List.empty[RdfDataObject]

  type env = LayersTest.Environment with Client with Scope

  private def prepare = Db.initWithTestData(rdfDataObjects)

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

  def sendDeleteRequest(url: String, token: Option[String]): ZIO[env, String, Response] =
    (for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr))
      _        <- ZIO.logDebug(s"POST  ${urlFull.encode}")
      bearer    = token.map(Header.Authorization.Bearer.apply)
      headers   = Headers(List(Header.ContentType(MediaType.application.json)) ++ bearer.toList)
      request   = Request.delete(urlFull).addHeaders(headers)
      response <- client.request(request)
    } yield response).mapError(_.getMessage)

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
