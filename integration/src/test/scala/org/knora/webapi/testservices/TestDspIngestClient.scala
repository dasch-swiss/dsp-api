/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.capabilities.zio.ZioStreams
import sttp.client3
import sttp.client3.*
import sttp.client3.SttpBackend
import zio.*
import zio.json.*
import zio.nio.file.Files
import zio.nio.file.Path

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.testservices.TestDspIngestClient.*

final case class TestDspIngestClient(
  config: DspIngestConfig,
  sttp: SttpBackend[Task, ZioStreams],
  jwtService: JwtService,
) {

  private val ingestUrl = uri"${config.baseUrl}"

  def uploadFile(file: java.nio.file.Path, shortcode: Shortcode = Shortcode.unsafeFrom("0001")): Task[UploadedFile] =
    for {
      contents   <- Files.readAllBytes(Path.fromJava(file))
      filename    = file.getFileName.toString
      loginToken <- jwtService.createJwtForDspIngest().map(_.jwtString)
      url         = ingestUrl.addPath("projects", shortcode.value, "assets", "ingest", file.getFileName.toString)
      request     = quickRequest.post(url).header("Authorization", s"Bearer $loginToken").body(contents.toArray)
      responseBody <-
        sttp
          .send(request)
          .filterOrElseWith(_.is200)(response =>
            ZIO.fail(Exception(s"Upload failed: $filename, ${response.code.code}: ${response.body}")),
          )
          .map(_.body)
      json <- ZIO.fromEither(responseBody.fromJson[UploadedFile]).mapError(Throwable(_))
    } yield json
}

object TestDspIngestClient {
  final case class UploadedFile(originalFilename: String, internalFilename: String)
  object UploadedFile {
    implicit val decoder: JsonDecoder[UploadedFile] = DeriveJsonDecoder.gen[UploadedFile]
  }
  val layer = ZLayer.derive[TestDspIngestClient]
}
