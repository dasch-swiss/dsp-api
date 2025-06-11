package org.knora.webapi.testservices
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ziojson.*
import sttp.model.MediaType
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
  private val backend: StreamBackend[Task, ZioStreams],
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) {

  private val baseUrl = uri"${apiConfig.externalKnoraApiBaseUrl}"

  private def jwtFor(user: User): UIO[String] =
    scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _)).map(_.jwtString)

  def deleteJson[A: JsonDecoder](wholePath: String, user: User): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => deleteJson(wholePath, _.auth.bearer(jwt)))

  def deleteJson[A: JsonDecoder](
    wholePath: String,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): Task[Response[Either[String, A]]] = {
    val request: Request[Either[String, A]] = basicRequest
      .delete(baseUrl.withWholePath(wholePath))
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
    f(request).send(backend)
  }

  def getJson[A: JsonDecoder](
    wholePath: String,
    f: Request[Either[String, A]] => Request[Either[String, A]],
    params: Map[String, String],
  ): Task[Response[Either[String, A]]] = {
    val request: Request[Either[String, A]] = basicRequest
      .get(baseUrl.withWholePath(wholePath).withParams(params))
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
    f(request).send(backend)
  }

  def getJson[A: JsonDecoder](
    wholePath: String,
    user: User,
    params: Map[String, String] = Map.empty,
  ): Task[Response[Either[String, A]]] =
    jwtFor(user).flatMap(jwt => getJson(wholePath, _.auth.bearer(jwt), params))

  def postJson[A: JsonDecoder, B: JsonEncoder](wholePath: String, body: B): Task[Response[Either[String, A]]] =
    basicRequest
      .post(baseUrl.withWholePath(wholePath))
      .body(body.toJson)
      .contentType(MediaType.ApplicationJson)
      .response(asJsonAlways[A].mapLeft((e: DeserializationException) => e.getMessage))
      .send(backend)

}

object TestApiClient {
  val getRootToken: ZIO[TestApiClient, Throwable, String] =
    TestApiClient
      .postJson[TokenResponse, LoginPayload](
        "/v2/authentication",
        LoginPayload.EmailPassword(SharedTestDataADM.rootUser.getEmail, "test"),
      )
      .flatMap(_.assert200)
      .map(_.token)

  def deleteJson[A: JsonDecoder](
    wholePath: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJson(wholePath, user))

  def deleteJson[A: JsonDecoder](
    wholePath: String,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.deleteJson(wholePath, f))

  def getJson[A: JsonDecoder](
    wholePath: String,
    f: Request[Either[String, A]] => Request[Either[String, A]],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson(wholePath, f, Map.empty))

  def getJson[A: JsonDecoder](wholePath: String): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    getJson[A](wholePath, Map.empty[String, String])

  def getJson[A: JsonDecoder](
    wholePath: String,
    params: Map[String, String],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson(wholePath, (r: Request[Either[String, A]]) => r, params))

  def getJson[A: JsonDecoder](
    wholePath: String,
    user: User,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] = getJson[A](wholePath, user, Map.empty)

  def getJson[A: JsonDecoder](
    wholePath: String,
    user: User,
    params: Map[String, String],
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.getJson(wholePath, user, params))

  def postJson[A: JsonDecoder, B: JsonEncoder](
    wholePath: String,
    body: B,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, A]]] =
    ZIO.serviceWithZIO[TestApiClient](_.postJson(wholePath, body))

  val layer = ZLayer.derive[TestApiClient]
}
