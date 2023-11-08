/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import sttp.capabilities.zio.ZioStreams
import sttp.client3.UriContext
import sttp.client3.asStreamAlways
import sttp.client3.basicRequest
import sttp.client3.httpclient.zio.HttpClientZioBackend
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
import zio.macros.accessible
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink

import scala.concurrent.duration.DurationInt

import dsp.valueobjects.Project.Shortcode
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.routing.JwtService

@accessible
trait DspIngestClient {
  def exportProject(shortcode: Shortcode): ZIO[Scope, Throwable, Path]

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path]
}
final case class DspIngestClientLive(
  jwtService: JwtService,
  dspIngestConfig: DspIngestConfig
) extends DspIngestClient {

  private def projectsPath(shortcode: Shortcode) = s"${dspIngestConfig.baseUrl}/projects/${shortcode.value}"

  def exportProject(shortcode: Shortcode): ZIO[Scope, Throwable, Path] =
    for {
      token     <- jwtService.createJwtForDspIngest()
      tempdir   <- Files.createTempDirectoryScoped(Some("export"), List.empty)
      exportFile = tempdir / "export.zip"
      response <- {
        val request = basicRequest.auth
          .bearer(token.jwtString)
          .post(uri"${projectsPath(shortcode)}/export")
          .readTimeout(30.minutes)
          .response(asStreamAlways(ZioStreams)(_.run(ZSink.fromFile(exportFile.toFile))))
        HttpClientZioBackend.scoped().flatMap(request.send(_))
      }
      _ <- ZIO.logInfo(s"Response from ingest :${response.code}")
    } yield exportFile

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path] = ZIO.scoped {
    for {
      importUrl <- ZIO.fromEither(URL.decode(s"${projectsPath(shortcode)}/import"))
      token     <- jwtService.createJwtForDspIngest()
      request = Request
                  .post(importUrl, Body.fromFile(fileToImport.toFile))
                  .addHeaders(
                    Headers(
                      Header.Authorization.Bearer(token.jwtString),
                      Header.ContentType(MediaType.application.zip)
                    )
                  )
      response     <- Client.request(request).provideSomeLayer[Scope](Client.default)
      bodyAsString <- response.body.asString
      _            <- ZIO.logInfo(s"Response code: ${response.status} body $bodyAsString")
    } yield fileToImport
  }
}

object DspIngestClientLive {
  val layer = ZLayer.fromFunction(DspIngestClientLive.apply _)
}
