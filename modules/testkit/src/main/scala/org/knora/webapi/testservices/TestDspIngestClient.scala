/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import zio.*
import zio.json.*
import zio.nio.file.Files
import zio.nio.file.Path

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.testservices.TestDspIngestClient.*

final case class TestDspIngestClient(
  config: DspIngestConfig,
  jwtService: JwtService,
  backend: StreamBackend[Task, ZioStreams],
) {

  private val ingestUrl = uri"${config.baseUrl}"

  def uploadFile(file: java.nio.file.Path, shortcode: Shortcode = Shortcode.unsafeFrom("0001")): Task[UploadedFile] =
    uploadFile(Path.fromJava(file), shortcode)

  def uploadFile(path: zio.nio.file.Path, shortcode: Shortcode): Task[UploadedFile] =
    for {
      contents     <- Files.readAllBytes(path)
      filename      = path.filename.toString
      loginToken   <- jwtService.createJwtForDspIngest().map(_.jwtString)
      url           = ingestUrl.addPath("projects", shortcode.value, "assets", "ingest", filename)
      request       = quickRequest.post(url).header("Authorization", s"Bearer $loginToken").body(contents.toArray)
      responseBody <-
        request
          .send(backend)
          .filterOrElseWith(_.is200)(response =>
            ZIO.fail(Exception(s"Upload failed: $filename, ${response.code.code}: ${response.body}")),
          )
          .map(_.body)
      json <- ZIO.fromEither(responseBody.fromJson[UploadedFile]).mapError(Throwable(_))
    } yield json

  def createImageAsset(shortcode: Shortcode): Task[UploadedFile] = for {
    i      <- zio.Random.nextInt
    path   <- createImageFile(s"test$i.jpg")
    upload <- uploadFile(path, shortcode)
  } yield upload

  private def createImageFile(filename: String) =
    for {
      dir <- zio.nio.file.Files.createTempDirectory(None, Seq.empty)
      path = dir / filename
      _   <- zio.nio.file.Files.createFile(path)
      img  = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
      _   <- ZIO.attemptBlocking(ImageIO.write(img, "jpeg", path.toFile))
    } yield path

}

object TestDspIngestClient {
  final case class UploadedFile(originalFilename: String, internalFilename: String)
  object UploadedFile {
    implicit val decoder: JsonDecoder[UploadedFile] = DeriveJsonDecoder.gen[UploadedFile]
  }

  def uploadFile(file: java.nio.file.Path, shortcode: Shortcode): ZIO[TestDspIngestClient, Throwable, UploadedFile] =
    ZIO.serviceWithZIO[TestDspIngestClient](_.uploadFile(file, shortcode))

  def createImageAsset(shortcode: Shortcode): ZIO[TestDspIngestClient, Throwable, UploadedFile] =
    ZIO.serviceWithZIO[TestDspIngestClient](_.createImageAsset(shortcode))

  val layer = ZLayer.derive[TestDspIngestClient]
}
