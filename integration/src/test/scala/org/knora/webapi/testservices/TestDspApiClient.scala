package org.knora.webapi.testservices

import dsp.errors.AssertionException
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.ziojson.*
import sttp.model.*
import zio.*
import zio.json.JsonDecoder
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver

final case class TestDspApiClient(
  private val backend: StreamBackend[Task, ZioStreams],
  private val config: KnoraApi,
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
) { self =>

  private val baseUri: Uri = uri"${config.externalKnoraApiHostPort}"

  private def fullUri(wholePath: String): Uri                   = baseUri.withPath(wholePath)
  private def getRequest(wholePath: String)                     = basicRequest.get(fullUri(wholePath))
  private def getStringRequest(wholePath: String)               = basicRequest.get(fullUri(wholePath)).response(asString)
  private def getJsonRequest[A: JsonDecoder](wholePath: String) = getRequest(wholePath).response(asJson[A])

  private def createJwt(user: User): UIO[String] =
    scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _).map(_.jwtString))

  def get[A: JsonDecoder](wholePath: String, user: User): Task[Response[Either[ResponseException[String], A]]] =
    createJwt(user).flatMap(getJsonRequest(wholePath).auth.bearer(_).send(backend))

  def get[A: JsonDecoder](wholePath: String): Task[Response[Either[ResponseException[String], A]]] =
    getJsonRequest(wholePath).send(backend)

  def getAsString(wholePath: String, user: User): Task[Response[Either[String, String]]] =
    createJwt(user).flatMap(getStringRequest(wholePath).auth.bearer(_).send(backend))

  def getAsString(wholePath: String): Task[Response[Either[String, String]]] =
    getStringRequest(wholePath).send(backend)

  def getAsJsonLd(wholePath: String, user: User): ZIO[Any, Throwable, Response[Either[String, JsonLDDocument]]] =
    getAsString(wholePath, user).flatMap(parseJsonLd)

  private def parseJsonLd(response: Response[Either[String, String]]) =
    response.body match
      case Left(error) => ZIO.succeed(response.copy(body = Left(error)))
      case Right(jsonString) =>
        ZIO.attempt(JsonLDUtil.parseJsonLD(jsonString)).map(jsonLd => response.copy(body = Right(jsonLd)))

  def getAsJsonLd(wholePath: String): ZIO[Any, Throwable, Response[Either[String, JsonLDDocument]]] =
    getAsString(wholePath).flatMap(parseJsonLd)
}

object TestDspApiClient {

  def get[A: JsonDecoder](
    wholePath: String,
    user: User,
  ): ZIO[TestDspApiClient, Throwable, Response[Either[ResponseException[String], A]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.get[A](wholePath, user))

  def get[A: JsonDecoder](
    wholePath: String,
  ): ZIO[TestDspApiClient, Throwable, Response[Either[ResponseException[String], A]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.get[A](wholePath))

  def getAsString(wholePath: String, user: User): ZIO[TestDspApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.getAsString(wholePath, user))

  def getAsString(wholePath: String): ZIO[TestDspApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.getAsString(wholePath))

  def getAsJsonLd(
    wholePath: String,
    user: User,
  ): ZIO[TestDspApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.getAsJsonLd(wholePath, user))

  def getAsJsonLd(wholePath: String): ZIO[TestDspApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestDspApiClient](_.getAsJsonLd(wholePath))

  val layer = ZLayer.derive[TestDspApiClient]
}

object ResponseOps {
  extension [A](response: Response[Either[String, A]]) {
    def `200`: IO[AssertionException, A] =
      (if response.code == StatusCode.Ok
       then ZIO.fromEither(response.body)
       else ZIO.fail(s"Expected 200 OK, got ${response.code} with body: ${response.body}"))
        .mapError(AssertionException(_))
  }
}
