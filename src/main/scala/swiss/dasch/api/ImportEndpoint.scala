/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import eu.timepit.refined.auto.autoUnwrap
import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ApiStringConverters.fromPathVarToProjectShortcode
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.{ AssetService, ProjectShortcode }
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.http.{ Header, * }
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.nio.file
import zio.nio.file.Files
import zio.schema.codec.JsonCodec.JsonEncoder
import zio.schema.{ DeriveSchema, Schema }
import zio.stream.{ ZSink, ZStream }
import zio.*

import java.io.IOException
import java.util.zip.ZipFile

object ImportEndpoint {
  case class UploadResponse(status: String = "okey")

  private object UploadResponse {
    implicit val schema: Schema[UploadResponse]       = DeriveSchema.gen[UploadResponse]
    implicit val encoder: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
  }

  private val importEndpoint =
    Endpoint
      .post(projects / shortcodePathVar / "import")
      // Files must be uploaded as zip files with the header 'Content-Type' 'application/zip' and the file in the body.
      // For now we check the ContentType in the implementation as zio-http doesn't support it yet to specify it
      // in the endpoint definition.
      .inCodec(ContentCodec.contentStream[Byte] ++ HeaderCodec.contentType)
      .out[UploadResponse]
      .outErrors(
        HttpCodec.error[IllegalArguments](Status.BadRequest),
        HttpCodec.error[InternalProblem](Status.InternalServerError),
      )

  val app: App[StorageConfig with AssetService] = importEndpoint
    .implement(
      (
          shortcode: String,
          stream: ZStream[Any, Nothing, Byte],
          actual: ContentType,
        ) =>
        for {
          pShortcode        <- ApiStringConverters.fromPathVarToProjectShortcode(shortcode)
          _                 <- verifyContentType(actual, ContentType(MediaType.application.zip))
          tempFile          <- ZIO.serviceWith[StorageConfig](_.importPath / s"import-$pShortcode.zip")
          writeFileErrorMsg  = s"Error while writing file $tempFile for project $shortcode"
          _                 <- stream
                                 .run(ZSink.fromFile(tempFile.toFile))
                                 .logError(writeFileErrorMsg)
                                 .mapError(e => ApiProblem.internalError(writeFileErrorMsg + ": " + e.getMessage))
          _                 <- validateInputFile(tempFile)
          importFileErrorMsg = s"Error while importing project $shortcode"
          _                 <- AssetService
                                 .importProject(pShortcode, tempFile)
                                 .logError(importFileErrorMsg)
                                 .mapError(e => ApiProblem.internalError(importFileErrorMsg + ": " + e.getMessage))
        } yield UploadResponse()
    )
    .toApp

  private def verifyContentType(actual: ContentType, expected: ContentType): IO[IllegalArguments, Unit] =
    ZIO.fail(ApiProblem.invalidHeaderContentType(actual, expected)).when(actual != expected).unit

  private def validateInputFile(tempFile: file.Path): ZIO[Any, ApiProblem, Unit] =
    (for {
      _ <- ZIO
             .fail(ApiProblem.bodyIsEmpty)
             .whenZIO(Files.size(tempFile).mapBoth(e => ApiProblem.internalError(e), _ == 0))
      _ <-
        ZIO.scoped {
          val acquire = ZIO.attemptBlockingIO(new ZipFile(tempFile.toFile))

          def release(zipFile: ZipFile) = ZIO.succeed(zipFile.close())

          ZIO.acquireRelease(acquire)(release).orElseFail(IllegalArguments("body", "body is not a zip file"))
        }
    } yield ()).tapError(_ => Files.deleteIfExists(tempFile).mapError(ApiProblem.internalError))

}
