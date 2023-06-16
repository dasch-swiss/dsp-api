/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ AssetService, ProjectShortcode }
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.HttpCodec.*
import zio.http.codec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.http.{ Header, * }
import zio.nio.file
import zio.schema.Schema
import zio.stream.ZStream
import zio.{ Chunk, Exit, Scope, URIO, ZIO, ZNothing }

import java.io.{ File, IOException }

object ExportEndpoint {
  private val shortcodePathVarName = "shortcode"
  private type ContentDispositionStream = (ContentDisposition, ContentType, ZStream[Any, Nothing, Byte])
  private val contentTypeApplicationZip = ContentType.parse("application/zip").toOption.get
  private val downloadCodec             =
    HeaderCodec.contentDisposition ++ HeaderCodec.contentType ++ ContentCodec.contentStream[Byte] ++ StatusCodec.status(
      Status.Ok
    )

  private val exportEndpoint: Endpoint[String, ApiProblem, ContentDispositionStream, None] = Endpoint
    .post("export" / string(shortcodePathVarName))
    .outCodec(downloadCodec)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val postExportShortCodeHandler: String => ZIO[AssetService, ApiProblem, ContentDispositionStream] =
    (shortcode: String) =>
      ZIO
        .fromEither(ProjectShortcode.make(shortcode))
        .mapError(ApiProblem.invalidPathVariable(shortcodePathVarName, shortcode, _))
        .flatMap { code =>
          AssetService
            .zipProject(code)
            .some
            .mapBoth(
              {
                case Some(err) => ApiProblem.internalError(err)
                case _         => ApiProblem.projectNotFound(code)
              },
              path =>
                (
                  ContentDisposition.Attachment(Some(s"export-$shortcode.zip")),
                  contentTypeApplicationZip,
                  ZStream.fromFile(path.toFile).orDie,
                ),
            )
        }

  val app: App[AssetService] = exportEndpoint.implement(postExportShortCodeHandler).toApp
}
