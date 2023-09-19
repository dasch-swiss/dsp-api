/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ApiProblem.{ BadRequest, InternalServerError }
import swiss.dasch.api.ApiStringConverters.fromPathVarToProjectShortcode
import swiss.dasch.domain.*
import zio.*
import zio.http.Header.ContentType
import zio.http.codec.*
import zio.http.endpoint.Endpoint
import zio.http.{ Header, * }
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }
import zio.stream.ZStream

object ImportEndpoint {
  case class UploadResponse(status: String = "okey")

  private object UploadResponse {
    given schema: Schema[UploadResponse]   = DeriveSchema.gen[UploadResponse]
    given codec: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]
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
        HttpCodec.error[BadRequest](Status.BadRequest),
        HttpCodec.error[InternalServerError](Status.InternalServerError),
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
                           case IoError(e)       => InternalServerError(s"Import of project $shortcodeStr failed", e)
                           case EmptyFile        => BadRequest.invalidBody("The uploaded file is empty")
                           case NoZipFile        => BadRequest.invalidBody("The uploaded file is not a zip file")
                           case InvalidChecksums => BadRequest.invalidBody("The uploaded file contains invalid checksums")
                         }
        } yield UploadResponse()
    )
    .toApp

  private def verifyContentType(actual: ContentType, expected: ContentType) =
    ZIO.when(actual != expected)(ZIO.fail(BadRequest.invalidHeaderContentType(actual, expected)))
}
