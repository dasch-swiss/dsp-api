/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.{ AssetService, ProjectShortcode }
import zio.http.*
import zio.http.HttpError.*
import zio.http.codec.HttpCodec
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, EndpointMiddleware, Routes }
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.nio.*
import zio.schema.DeriveSchema.gen
import zio.schema.{ DeriveSchema, Schema }
import zio.{ ZIO, ZNothing, http }

object ExportEndpoint {
  final case class ExportResponse(path: String)
  private object ExportResponse {
    def make(path: file.Path): ExportResponse = ExportResponse(path.toString)

    implicit val encoder: JsonEncoder[ExportResponse] = DeriveJsonEncoder.gen[ExportResponse]
    implicit val schema: Schema[ExportResponse]       = DeriveSchema.gen[ExportResponse]
  }

  private val shortcodePathVar = "shortcode"

  private val exportEndpoint: Endpoint[String, ApiProblem, ExportResponse, None] = Endpoint
    .post("export" / string(shortcodePathVar))
    .out[ExportResponse](Status.Ok)
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val exportRoute: Routes[AssetService, ApiProblem, None] = exportEndpoint.implement { (shortcode: String) =>
    ZIO
      .fromEither(ProjectShortcode.make(shortcode))
      .mapError(ApiProblem.invalidPathVariable(shortcodePathVar, shortcode, _))
      .flatMap(AssetService.zipProject(_).some.mapError {
        case Some(e) => ApiProblem.internalError(e)
        case _       => ProjectNotFound(shortcode)
      })
      .map(ExportResponse.make)
  }

  val app = exportRoute.toApp

  sealed trait ApiProblem
  case class ProjectNotFound(shortcode: String)            extends ApiProblem
  case class IllegalArguments(errors: Map[String, String]) extends ApiProblem
  case class InternalProblem(errorMessage: String)         extends ApiProblem

  object ApiProblem {
    implicit val projectNotFoundEncoder: JsonEncoder[ProjectNotFound]   = DeriveJsonEncoder.gen[ProjectNotFound]
    implicit val projectNotFoundSchema: Schema[ProjectNotFound]         = DeriveSchema.gen[ProjectNotFound]
    implicit val illegalArgumentsEncoder: JsonEncoder[IllegalArguments] = DeriveJsonEncoder.gen[IllegalArguments]
    implicit val illegalArgumentsSchema: Schema[IllegalArguments]       = DeriveSchema.gen[IllegalArguments]
    implicit val internalErrorEncoder: JsonEncoder[InternalProblem]     = DeriveJsonEncoder.gen[InternalProblem]
    implicit val internalErrorSchema: Schema[InternalProblem]           = DeriveSchema.gen[InternalProblem]

    def internalError(t: Throwable): InternalProblem =
      InternalProblem(t.getMessage)

    def invalidPathVariable(
        key: String,
        value: String,
        reason: String,
      ): IllegalArguments =
      IllegalArguments(Map(s"Invalid path var: $key" -> s"'$value' is invalid: $reason"))
  }
}
