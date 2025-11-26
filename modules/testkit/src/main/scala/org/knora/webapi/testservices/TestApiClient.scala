/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ziojson.*
import sttp.model.MediaType
import sttp.model.Part
import sttp.model.Uri
import zio.*
import zio.json.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.ResponseOps.assert200

final case class TestApiClient(
  private val apiConfig: KnoraApi,
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) extends BaseApiClient(be, jwtService, scopeResolver) {

  protected override val baseUrl = uri"${apiConfig.externalKnoraApiBaseUrl}"

  def deleteJson[A: JsonDecoder](relativeUri: Uri, user: User): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => deleteJson(relativeUri, _.auth.bearer(jwt)))

  def deleteJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): Task[Response[Either[String, A]]] = {
    val request: Request[Either[String, A]] = basicRequest
      .delete(relativeUri)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.body))
    sendRequest(f(request))
  }

  private def sendRequest[A](
    request: Request[Either[String, A]],
    user: Option[User] = None,
  ): Task[Response[Either[String, A]]] =
    (user match {
      case Some(u) => jwtFor(u).map(jwt => request.auth.bearer(jwt))
      case None    => ZIO.succeed(request)
    }).flatMap(_.send(backend))

  def deleteJsonLd(
    relativeUri: Uri,
    user: Option[User],
    update: RequestUpdate[String],
  ): Task[Response[Either[String, String]]] = {
    val request = update(basicRequest.delete(relativeUri))
      .contentType(MediaType.unsafeApply("application", "ld+json"))
      .response(asString)
    sendRequest(request, user)
  }

  def deleteJsonLdDocument(
    relativeUri: Uri,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ): Task[Response[Either[String, JsonLDDocument]]] = {
    val request: Request[Either[String, JsonLDDocument]] = update(
      basicRequest
        .delete(relativeUri)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asJsonLdDocument),
    )
    sendRequest(request, user)
  }

  def getAsString(
    relativeUri: Uri,
    f: Request[Either[String, String]] => Request[Either[String, String]],
  ): Task[Response[Either[String, String]]] =
    sendRequest(f(basicRequest.get(relativeUri).response(asString)))

  def getJson[A: JsonDecoder](relativeUri: Uri): Task[Response[Either[String, A]]] =
    getJson(relativeUri, (r: Request[Either[String, A]]) => r)

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): Task[Response[Either[String, A]]] = sendRequest(
    f(basicRequest.get(relativeUri).response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))),
  )

  def getJson[A: JsonDecoder](relativeUri: Uri, user: User): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => getJson(relativeUri, _.auth.bearer(jwt)))

  def getJsonLd(
    relativeUri: Uri,
    user: Option[User] = None,
    update: RequestUpdate[String] = identity,
  ): Task[Response[Either[String, String]]] = {
    val request = update(
      basicRequest
        .get(relativeUri)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asString),
    )
    sendRequest(request, user)
  }

  def getJsonLdDocument(
    relativeUri: Uri,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ): Task[Response[Either[String, JsonLDDocument]]] = {
    val request: Request[Either[String, JsonLDDocument]] = update(
      basicRequest
        .get(relativeUri)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asJsonLdDocument),
    )
    sendRequest(request, user)
  }

  def postJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): Task[Response[Either[String, A]]] = {
    val request = basicRequest
      .post(relativeUri)
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.body))
    sendRequest(request, Some(user))
  }

  def postJson[A: JsonDecoder, B: JsonEncoder](relativeUri: Uri, body: B): Task[Response[Either[String, A]]] = {
    val request = basicRequest
      .post(relativeUri)
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
    sendRequest(request)
  }

  def postJsonReceiveString[B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): Task[Response[Either[String, String]]] = {
    val request = basicRequest
      .post(relativeUri)
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
    sendRequest(request, Some(user))
  }

  def postJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): Task[Response[Either[String, String]]] = {
    val request = basicRequest
      .post(relativeUri)
      .body(jsonLdBody)
      .contentType(MediaType.unsafeApply("application", "ld+json"))
      .response(asString)
    sendRequest(request, Some(user))
  }

  def postJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ): Task[Response[Either[String, JsonLDDocument]]] = {
    val request: Request[Either[String, JsonLDDocument]] = update(
      basicRequest
        .post(relativeUri)
        .body(jsonLdBody)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asJsonLdDocument),
    )
    sendRequest(request, user)
  }

  def postMultiPart[A: JsonDecoder](
    relativeUri: Uri,
    body: Seq[Part[BasicBodyPart]],
    user: User,
  ): Task[Response[Either[String, A]]] = {
    val request = basicRequest
      .post(relativeUri)
      .multipartBody(body)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.body))
    sendRequest(request, Some(user))
  }

  def patchJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ): Task[Response[Either[String, JsonLDDocument]]] = {
    val request: Request[Either[String, JsonLDDocument]] = update(
      basicRequest
        .patch(relativeUri)
        .body(jsonLdBody)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asJsonLdDocument),
    )
    sendRequest(request, user)
  }

  def putJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): Task[Response[Either[String, A]]] = {
    val request = basicRequest
      .put(relativeUri)
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.body))
    sendRequest(request, Some(user))
  }

  def putJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[Any, Throwable, Response[Either[String, String]]] = {
    val request = basicRequest
      .put(relativeUri)
      .body(jsonLdBody)
      .contentType(MediaType.unsafeApply("application", "ld+json"))
      .response(asString)
    sendRequest(request, Some(user))
  }

  def putJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ): ZIO[Any, Throwable, Response[Either[String, JsonLDDocument]]] = {
    val request = update(
      basicRequest
        .put(relativeUri)
        .body(jsonLdBody)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .response(asJsonLdDocument),
    )
    sendRequest(request, user)
  }

  def postSparql(
    relativeUri: Uri,
    sparqlQuery: String,
    f: RequestUpdate[String],
  ): Task[Response[Either[String, String]]] = {
    val request = f(basicRequest.post(relativeUri).body(sparqlQuery))
      .contentType(MediaType.unsafeApply("application", "sparql-query"))
      .response(asString)
    sendRequest(request)
  }

  def postSparql(
    relativeUri: Uri,
    sparqlQuery: String,
    user: User,
    f: RequestUpdate[String],
  ): Task[Response[Either[String, String]]] =
    jwtFor(user).flatMap(jwt => postSparql(relativeUri, sparqlQuery, f.andThen(_.auth.bearer(jwt))))
}

object TestApiClient {

  val getRootToken: ZIO[TestApiClient, Throwable, String] =
    TestApiClient
      .postJson[TokenResponse, LoginPayload](
        uri"/v2/authentication",
        LoginPayload.EmailPassword(SharedTestDataADM.rootUser.getEmail, "test"),
      )
      .flatMap(_.assert200)
      .map(_.token)

  def getAsString(
    relativeUri: Uri,
    f: Request[Either[String, String]] => Request[Either[String, String]] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getAsString(relativeUri, f))

  def deleteJson[A: JsonDecoder](
    relativeUri: Uri,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJson(relativeUri, user))

  def deleteJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJson(relativeUri, f))

  def deleteJsonLd(
    relativeUri: Uri,
    user: User,
    update: RequestUpdate[String] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJsonLd(relativeUri, Some(user), update))

  def deleteJsonLdDocument(
    relativeUri: Uri,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJsonLdDocument(relativeUri, Some(user), r => r))

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson(relativeUri, f))

  def getJson[A: JsonDecoder](relativeUri: Uri): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    getJson[A](relativeUri, (r: Request[Either[String, A]]) => r)

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson[A](relativeUri, user))

  def getJsonLdDocument(relativeUri: Uri): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    getJsonLdDocument(relativeUri, None, r => r)

  def getJsonLdDocument(
    relativeUri: Uri,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    getJsonLdDocument(relativeUri, Some(user), r => r)

  def getJsonLdDocument(
    relativeUri: Uri,
    user: User,
    update: RequestUpdate[JsonLDDocument],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    getJsonLdDocument(relativeUri, Some(user), update)

  def getJsonLdDocument(
    relativeUri: Uri,
    user: Option[User],
    update: RequestUpdate[JsonLDDocument],
  ) = ZIO.serviceWithZIO[TestApiClient](_.getJsonLdDocument(relativeUri, user, update))

  def getJsonLd(
    relativeUri: Uri,
    f: Request[Either[String, String]] => Request[Either[String, String]] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    getJsonLd(relativeUri, None, f)

  def getJsonLd(relativeUri: Uri, user: User): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    getJsonLd(relativeUri, Some(user), identity)

  def getJsonLd(
    relativeUri: Uri,
    user: User,
    f: Request[Either[String, String]] => Request[Either[String, String]],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    getJsonLd(relativeUri, Some(user), f)

  def getJsonLd(
    relativeUri: Uri,
    user: Option[User],
    f: RequestUpdate[String],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJsonLd(relativeUri, user, f))

  def postJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postJson(relativeUri, body, user))

  def postJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postJson(relativeUri, body))

  def postJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postJsonLd(relativeUri, jsonLdBody, user))

  def postJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postJsonLdDocument(relativeUri, jsonLdBody, Some(user), r => r))

  def postJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: Option[User] = None,
    update: RequestUpdate[JsonLDDocument] = identity,
  ) = ZIO.serviceWithZIO[TestApiClient](_.postJsonLdDocument(relativeUri, jsonLdBody, user, update))

  def patchJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestApiClient](_.patchJsonLdDocument(relativeUri, jsonLdBody, Some(user), identity))

  def putJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.putJson(relativeUri, body, user))

  def putJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.putJsonLd(relativeUri, jsonLdBody, user))

  def putJsonLdDocument(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestApiClient](_.putJsonLdDocument(relativeUri, jsonLdBody, Some(user), r => r))

  def postMultiPart[A: JsonDecoder](
    relativeUri: Uri,
    body: Seq[Part[BasicBodyPart]],
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postMultiPart[A](relativeUri, body, user))

  def postSparql(
    relativeUri: Uri,
    sparqlQuery: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    postSparql(relativeUri, sparqlQuery, Some(user), identity)

  def postSparql(
    relativeUri: Uri,
    sparqlQuery: String,
    user: Option[User] = None,
    f: RequestUpdate[String] = identity,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](http =>
      user match {
        case Some(u) => http.postSparql(relativeUri, sparqlQuery, u, f)
        case None    => http.postSparql(relativeUri, sparqlQuery, f)
      },
    )

  val layer = ZLayer.derive[TestApiClient]
}
