/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import org.apache.http
import org.apache.http.entity.ContentType
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.*
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponse
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol.*
import org.knora.webapi.messages.store.sipimessages.SipiUploadWithoutProcessingResponse
import org.knora.webapi.messages.store.sipimessages.SipiUploadWithoutProcessingResponseJsonProtocol.*
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.iiif.errors.SipiException

import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.unmarshalling.Unmarshal

/**
 * Represents a file to be uploaded to the IIF Service.
 *
 * @param path     the path of the file.
 * @param mimeType the MIME type of the file.
 */
final case class FileToUpload(path: Path, mimeType: ContentType)

/**
 * Represents an image file to be uploaded to the IIF Service.
 *
 * @param fileToUpload the file to be uploaded.
 * @param width        the image's width in pixels.
 * @param height       the image's height in pixels.
 */
final case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

final case class TestClientService(
  config: AppConfig,
  sttp: SttpBackend[Task, ZioStreams],
)(implicit system: ActorSystem)
    extends TriplestoreJsonProtocol
    with RequestBuilding {

  private val targetHostUri = uri"${config.sipi.internalBaseUrl}"

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
   *                     It is unsafe because the the response body can only be unmarshalled (i.e. printed) to a string once.
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
   * Performs a http request and dosn't return the string (only error channel).
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

  /**
   * Uploads a file to the IIIF Service's "upload" route and returns the information in Sipi's response.
   * The upload creates a multipart/form-data request which can contain multiple files.
   *
   * @param loginToken    the login token to be included in the request to Sipi.
   * @param files the files to be uploaded.
   * @return a [[SipiUploadResponse]] representing Sipi's response.
   */
  def uploadToSipi(loginToken: String, files: Seq[FileToUpload]): Task[SipiUploadResponse] =
    for {
      url <- ZIO.succeed(targetHostUri.addPath("upload").addParam("token", loginToken))
      multiparts = files.map { file =>
                     multipartFile("file", file.path.toFile)
                       .fileName(file.path.getFileName.toString)
                       .contentType(file.mimeType.toString)
                   }
      response <- doSipiRequest(quickRequest.post(url).multipartBody(multiparts))
      json     <- ZIO.fromEither(response.fromJson[SipiUploadResponse]).mapError(Throwable(_))
    } yield json

  /**
   * Uploads a file to the IIIF Service's "upload_without_processing" route and returns the information in Sipi's response.
   * The upload creates a multipart/form-data request which can contain multiple files.
   *
   * @param loginToken    the login token to be included in the request to Sipi.
   * @param files the files to be uploaded.
   * @return a [[SipiUploadWithoutProcessingResponse]] representing Sipi's response.
   */
  def uploadWithoutProcessingToSipi(
    loginToken: String,
    files: Seq[FileToUpload],
  ): Task[SipiUploadWithoutProcessingResponse] =
    for {
      url <- ZIO.succeed(targetHostUri.addPath("upload_without_processing").addParam("token", loginToken))
      multiparts = files.map { file =>
                     multipartFile("file", file.path.toFile)
                       .fileName(file.path.getFileName.toString)
                       .contentType(file.mimeType.toString)
                   }
      response <- doSipiRequest(quickRequest.post(url).multipartBody(multiparts))
      json     <- ZIO.fromEither(response.fromJson[SipiUploadWithoutProcessingResponse]).mapError(Throwable(_))
    } yield json

  private def doSipiRequest[T](request: Request[String, Any]): Task[String] =
    sttp.send(request).flatMap { response =>
      if (response.isSuccess) {
        ZIO.succeed(response.body)
      } else {
        if (response.code.code == 404) {
          ZIO.fail(NotFoundException(response.body))
        } else if (response.isClientError) {
          ZIO.fail(BadRequestException(s"Sipi responded with HTTP status code ${response.code.code}: ${response.body}"))
        } else {
          ZIO.fail(SipiException(s"Sipi responded with HTTP status code ${response.code.code}: ${response.body}"))
        }
      }
    }
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
