/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.client4.ziojson.*
import sttp.model.MediaType
import sttp.model.Uri
import zio.*
import zio.json.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.testservices.ResponseOps.assert200

final case class TestApiClient(
  private val apiConfig: KnoraApi,
  private val be: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) {

  private val baseUrl = uri"${apiConfig.externalKnoraApiBaseUrl}"
  private val backend = ResolveRelativeUrisBackend(be, baseUrl)

  private def jwtFor(user: User): UIO[String] =
    scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _)).map(_.jwtString)

  def deleteJson[A: JsonDecoder](relativeUri: Uri, user: User): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => deleteJson(relativeUri, _.auth.bearer(jwt)))

  def deleteJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): Task[Response[Either[String, A]]] = {
    val request: Request[Either[String, A]] = basicRequest
      .delete(relativeUri)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
    f(request).send(backend)
  }

  def getJson[A: JsonDecoder](relativeUri: Uri): Task[Response[Either[String, A]]] =
    getJson(relativeUri, (r: Request[Either[String, A]]) => r)

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): Task[Response[Either[String, A]]] = {
    val request: Request[Either[String, A]] = basicRequest
      .get(relativeUri)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
    f(request).send(backend)
  }

  def getJson[A: JsonDecoder](relativeUri: Uri, user: User): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => getJson(relativeUri, _.auth.bearer(jwt)))

  def getJsonLd(relativeUri: Uri): Task[Response[Either[String, String]]] =
    basicRequest
      .get(relativeUri)
      .contentType(MediaType.unsafeApply("application", "ld+json"))
      .response(asString)
      .send(backend)

  def getJsonLd(
    relativeUri: Uri,
    user: User,
  ): Task[Response[Either[String, String]]] =
    jwtFor(user).flatMap(jwt =>
      basicRequest
        .get(relativeUri)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .auth
        .bearer(jwt)
        .response(asString)
        .send(backend),
    )

  def postJson[A: JsonDecoder, B: JsonEncoder](
    relativeUri: Uri,
    body: B,
    user: User,
  ): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest
        .post(relativeUri)
        .body(body.toJson)
        .contentType(MediaType.ApplicationJson)
        .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
        .auth
        .bearer(jwt)
        .send(backend)
    }

  def postJson[A: JsonDecoder, B: JsonEncoder](relativeUri: Uri, body: B): Task[Response[Either[String, A]]] =
    basicRequest
      .post(relativeUri)
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
      .send(backend)

  def postJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[Any, Throwable, Response[Either[String, String]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest
        .post(relativeUri)
        .body(jsonLdBody)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .auth
        .bearer(jwt)
        .response(asString)
        .send(backend)
    }

  def putJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[Any, Throwable, Response[Either[String, String]]] =
    jwtFor(user).flatMap { jwt =>
      basicRequest
        .put(relativeUri)
        .body(jsonLdBody)
        .contentType(MediaType.unsafeApply("application", "ld+json"))
        .auth
        .bearer(jwt)
        .response(asString)
        .send(backend)
    }
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

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson(relativeUri, f))

  def getJson[A: JsonDecoder](relativeUri: Uri): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    getJson[A](relativeUri, (r: Request[Either[String, A]]) => r)

  def getJson[A: JsonDecoder](
    relativeUri: Uri,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson[A](relativeUri, user))

  def getJsonLd(relativeUri: Uri): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJsonLd(relativeUri))

  def getJsonLd(relativeUri: Uri, user: User): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJsonLd(relativeUri, user))

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

  def putJsonLd(
    relativeUri: Uri,
    jsonLdBody: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestApiClient](_.putJsonLd(relativeUri, jsonLdBody, user))

  val layer = ZLayer.derive[TestApiClient]
}
