/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._
import zio.http.Body
import zio.http.Client
import zio.http.Request
import zio.http.URL
import zio.http.model.Headers
import zio.macros.accessible
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink

import dsp.valueobjects.Project.ShortCode
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.routing.JwtService

@accessible
trait DspIngestClient {
  def exportProject(shortCode: ShortCode): ZIO[Scope, Throwable, Path]

  def importProject(shortCode: ShortCode, fileToImport: Path): Task[Path]
}

final case class DspIngestClientLive(
  jwtService: JwtService,
  dspIngestConfig: DspIngestConfig
) extends DspIngestClient {

  def exportProject(shortCode: ShortCode): ZIO[Scope, Throwable, Path] =
    for {
      exportUrl <- ZIO.fromEither(URL.fromString(s"${dspIngestConfig.baseUrl}/project/${shortCode.value}/export"))
      token     <- jwtService.createJwtForDspIngest()
      request = Request
                  .post(Body.empty, exportUrl)
                  .updateHeaders(_.addHeaders(Headers.bearerAuthorizationHeader(token.jwtString)))
      response  <- Client.request(request).provideSomeLayer[Scope](Client.default)
      tempdir   <- Files.createTempDirectoryScoped(Some("export"), List.empty)
      exportFile = tempdir / "export.zip"
      _         <- response.body.asStream.run(ZSink.fromFile(exportFile.toFile))
    } yield exportFile

  def importProject(shortCode: ShortCode, fileToImport: Path): Task[Path] = ZIO.scoped {
    for {
      importUrl <- ZIO.fromEither(URL.fromString(s"${dspIngestConfig.baseUrl}/project/${shortCode.value}/import"))
      token     <- jwtService.createJwtForDspIngest()
      request = Request
                  .post(Body.fromFile(fileToImport.toFile), importUrl)
                  .updateHeaders(_.addHeaders(Headers.bearerAuthorizationHeader(token.jwtString)))
      _ <- Client.request(request).provideSomeLayer[Scope](Client.default)
    } yield fileToImport
  }
}

object DspIngestClientLive {
  val layer = ZLayer.fromFunction(DspIngestClientLive.apply _)
}
