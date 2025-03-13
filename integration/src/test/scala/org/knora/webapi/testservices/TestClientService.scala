/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.*
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import dsp.errors.AssertionException
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.settings.KnoraDispatchers

import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.unmarshalling.Unmarshal

final case class TestClientService(
  config: AppConfig,
  sttp: SttpBackend[Task, ZioStreams],
)(implicit system: ActorSystem)
    extends TriplestoreJsonProtocol
    with RequestBuilding {

  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

  case class TestClientTimeoutException(msg: String) extends Exception

  /**
   * Performs a http request.
   *
   * @param request the request to be performed.
   * @param timeout the timeout for the request. Default timeout is 5 seconds.
   * @param printFailure If true, the response body will be printed if the request fails.
   *                     This flag is intended to be used for debugging purposes only.
   *                     Since this is unsafe, it is false by default.
   *                     It is unsafe because the response body can only be unmarshalled (i.e. printed) to a string once.
   *                     It will fail if the test code is also unmarshalling the response.
   * @return the response.
   */
  def singleAwaitingRequest(
    request: pekko.http.scaladsl.model.HttpRequest,
    timeout: Option[zio.Duration] = None,
    printFailure: Boolean = false,
  ): Task[pekko.http.scaladsl.model.HttpResponse] =
    ZIO
      .fromFuture[pekko.http.scaladsl.model.HttpResponse](_ =>
        pekko.http.scaladsl
          .Http()
          .singleRequest(request)
          .map { resp =>
            if (printFailure && resp.status.isFailure()) {
              val _ = Unmarshal(resp.entity).to[String].map { body =>
                println(s"Request failed with status ${resp.status} and body $body")
              }
            }
            resp
          },
      )
      .timeout(timeout.getOrElse(10.seconds))
      .some
      .mapError {
        case None            => throw AssertionException("Request timed out.")
        case Some(throwable) => throw throwable
      }

  /**
   * Performs a http request and returns the body of the response.
   */
  def getResponseString(request: pekko.http.scaladsl.model.HttpRequest): Task[String] =
    for {
      response <- singleAwaitingRequest(request)
      body <-
        ZIO
          .attemptBlocking(
            Await.result(
              response.entity.toStrict(FiniteDuration(1, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8")),
              FiniteDuration(1, TimeUnit.SECONDS),
            ),
          )
          .mapError(error =>
            throw AssertionException(s"Got HTTP ${response.status.intValue}\n REQUEST: $request, \n RESPONSE: $error"),
          )
      _ <- ZIO
             .fail(AssertionException(s"Got HTTP ${response.status.intValue}\n REQUEST: $request, \n RESPONSE: $body"))
             .when(response.status.isFailure())
    } yield body

  /**
   * Performs a http request and does not return the string (only error channel).
   */
  def checkResponseOK(request: pekko.http.scaladsl.model.HttpRequest): Task[Unit] = getResponseString(request).unit

  /**
   * Performs a http request and tries to parse the response body as Json.
   */
  def getResponseJson(request: pekko.http.scaladsl.model.HttpRequest): Task[Json.Obj] =
    for {
      body <- getResponseString(request)
      json <- ZIO.fromEither(body.fromJson[Json.Obj]).mapError(Throwable(_))
    } yield json

  /**
   * Performs a http request and tries to parse the response body as JsonLD.
   */
  def getResponseJsonLD(request: pekko.http.scaladsl.model.HttpRequest): Task[JsonLDDocument] =
    for {
      body <- getResponseString(request)
      json <- ZIO.succeed(JsonLDUtil.parseJsonLD(body))
    } yield json

  def getJsonLd(url: String, credentials: HttpCredentials): Task[JsonLDDocument] =
    getResponseJsonLD(Get(url) ~> addCredentials(credentials))

  def getJsonLd(url: String): Task[JsonLDDocument] = getResponseJsonLD(Get(url))

  def patchJsonLd(url: String, jsonLd: String, credentials: HttpCredentials): Task[JsonLDDocument] =
    getResponseJsonLD(
      Patch(url, HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLd)) ~> addCredentials(credentials),
    )

  def deleteJsonLd(url: String, credentials: HttpCredentials): Task[JsonLDDocument] =
    getResponseJsonLD(Delete(url) ~> addCredentials(credentials))

  def putJsonLd(url: String, jsonLd: String, credentials: HttpCredentials): Task[JsonLDDocument] =
    getResponseJsonLD(Put(url, HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLd)) ~> addCredentials(credentials))

  def postJsonLd(url: String, jsonLd: String, credentials: HttpCredentials): Task[JsonLDDocument] =
    getResponseJsonLD(Post(url, HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLd)) ~> addCredentials(credentials))
}

object TestClientService {
  def layer: ZLayer[ActorSystem & AppConfig, Nothing, TestClientService] =
    HttpClientZioBackend.layer().orDie >+>
      ZLayer.scoped {
        for {
          sys    <- ZIO.service[ActorSystem]
          config <- ZIO.service[AppConfig]
          sttp   <- ZIO.service[SttpBackend[Task, ZioStreams]]
        } yield TestClientService(config, sttp)(sys)
      }
}
