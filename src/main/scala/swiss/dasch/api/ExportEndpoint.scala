/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.domain.ProjectService
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.Endpoint
import zio.http.{ Header, * }
import zio.schema.Schema
import zio.stream.ZStream

object ExportEndpoint {

  private val downloadCodec =
    HeaderCodec.contentDisposition ++
      HeaderCodec.contentType ++
      ContentCodec.contentStream[Byte] ++
      StatusCodec.status(Status.Ok)

  private val exportEndpoint = Endpoint
    .post(projects / shortcodePathVar / "export")
    .outCodec(downloadCodec)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val app: App[ProjectService] = exportEndpoint
    .implement((shortcodeStr: String) =>
      for {
        shortcode <- ApiStringConverters.fromPathVarToProjectShortcode(shortcodeStr)
        response  <- ProjectService
                       .zipProject(shortcode)
                       .some
                       .mapBoth(
                         {
                           case Some(err) => ApiProblem.internalError(err)
                           case _         => ApiProblem.projectNotFound(shortcode)
                         },
                         path =>
                           (
                             ContentDisposition.Attachment(Some(s"export-$shortcode.zip")),
                             ContentType(MediaType.application.zip),
                             ZStream.fromFile(path.toFile).orDie,
                           ),
                       )
      } yield response
    )
    .toApp
}
