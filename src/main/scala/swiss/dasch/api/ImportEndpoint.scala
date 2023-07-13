/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import eu.timepit.refined.auto.autoUnwrap
import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ApiStringConverters.fromPathVarToProjectShortcode
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.*
import zio.*
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

  val app: App[StorageService with ImportService] = importEndpoint
    .implement(
      (
          shortcodeStr: String,
          stream: ZStream[Any, Nothing, Byte],
          actual: ContentType,
        ) =>
        for {
          shortcode <- ApiStringConverters.fromPathVarToProjectShortcode(shortcodeStr)
          _         <- verifyContentType(actual, ContentType(MediaType.application.zip))
          _         <- ImportService
                         .importZipStream(shortcode, stream)
                         .mapError {
                           case IoError(e)       => ApiProblem.internalError(s"Import of project $shortcodeStr failed", e)
                           case EmptyFile        => ApiProblem.invalidBody("The uploaded file is empty")
                           case NoZipFile        => ApiProblem.invalidBody("The uploaded file is not a zip file")
                           case InvalidChecksums => ApiProblem.invalidBody("The uploaded file contains invalid checksums")
                         }
        } yield UploadResponse()
    )
    .toApp

  private def verifyContentType(actual: ContentType, expected: ContentType) =
    ZIO.when(actual != expected)(ZIO.fail(ApiProblem.invalidHeaderContentType(actual, expected)))
}
