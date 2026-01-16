/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.http.Body
import zio.http.Client
import zio.http.Header
import zio.http.Headers
import zio.http.MediaType
import zio.http.Request
import zio.http.URL
import zio.json.DecoderOps
import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink

import java.io.IOException
import scala.concurrent.duration.DurationInt

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.TracingHttpClient

final case class AssetInfoResponse(
  internalFilename: String,
  originalInternalFilename: String,
  originalFilename: String,
  checksumOriginal: String,
  checksumDerivative: String,
  width: Option[Int] = None,
  height: Option[Int] = None,
  duration: Option[Double] = None,
  fps: Option[Double] = None,
  internalMimeType: Option[String] = None,
  originalMimeType: Option[String] = None,
)
object AssetInfoResponse {
  implicit val decoder: JsonDecoder[AssetInfoResponse] = DeriveJsonDecoder.gen[AssetInfoResponse]
}

final case class DspIngestClient(
  jwtService: JwtService,
  dspIngestConfig: DspIngestConfig,
  backend: StreamBackend[Task, ZioStreams],
) {

  private def projectsPath(shortcode: Shortcode) = s"${dspIngestConfig.baseUrl}/projects/${shortcode.value}"

  private val authenticatedRequest: ZIO[Any, Nothing, PartialRequest[Either[String, String]]] =
    jwtService
      .createJwtForDspIngest()
      .map(_.jwtString)
      .map(basicRequest.auth.bearer(_))

  def getAssetInfo(shortcode: Shortcode, assetId: AssetId): Task[AssetInfoResponse] =
    for {
      request  <- authenticatedRequest.map(_.get(uri"${projectsPath(shortcode)}/assets/$assetId"))
      response <- request.send(backend)
      _        <- ZIO.logInfo(s"asset info for $shortcode/$assetId")
      _        <- ZIO.logDebug(s"Response from ingest: ${response.code}")
      _        <- ZIO.logDebug(s"Response from ingest body: ${response.body.fold(identity, identity)}")
      result   <- ZIO
                  .fromEither(response.body.flatMap(str => str.fromJson[AssetInfoResponse]))
                  .mapError(err => new IOException(s"Error parsing response: $err"))
    } yield result

  def exportProject(shortcode: Shortcode): ZIO[Scope, Throwable, Path] =
    for {
      tempDir   <- Files.createTempDirectoryScoped(Some("export"), List.empty)
      exportFile = tempDir / "export.zip"
      request   <- authenticatedRequest.map {
                   _.post(uri"${projectsPath(shortcode)}/export")
                     .readTimeout(30.minutes)
                     .response(asStreamAlways(ZioStreams)(_.run(ZSink.fromFile(exportFile.toFile))))
                 }
      response <- request.send(backend)
      _        <- ZIO.logInfo(s"Response from ingest :${response.code}")
    } yield exportFile

  def eraseProject(shortcode: Shortcode): Task[Unit] = for {
    request  <- authenticatedRequest.map(_.delete(uri"${projectsPath(shortcode)}/erase"))
    response <- request.send(backend)
    _        <- ZIO.logInfo(s"Response from ingest :${response.body}")
  } yield ()

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path] = ZIO.scoped {
    for {
      importUrl <- ZIO.fromEither(URL.decode(s"${projectsPath(shortcode)}/import"))
      token     <- jwtService.createJwtForDspIngest()
      body      <- Body.fromFile(fileToImport.toFile)
      request    = Request
                  .post(importUrl, body)
                  .addHeaders(
                    Headers(
                      Header.Authorization.Bearer(token.jwtString),
                      Header.ContentType(MediaType.application.zip),
                    ),
                  )
      response     <- Client.batched(request).provideSomeLayer[Scope](Client.default)
      bodyAsString <- response.body.asString
      _            <- ZIO.logInfo(s"Response code: ${response.status} body $bodyAsString")
    } yield fileToImport
  }
}

object DspIngestClient {
  val layer = TracingHttpClient.layer >>> ZLayer.derive[DspIngestClient]
}
