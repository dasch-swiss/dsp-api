/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.BadRequest.Argument
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.infrastructure.Status.DOWN
import swiss.dasch.infrastructure.{HealthResponse, Status}
import zio.http.Header.ContentType
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}

sealed trait ApiProblem

object ApiProblem {

  case class Conflict(reason: String) extends ApiProblem

  object Conflict {
    given codec: JsonCodec[Conflict] = DeriveJsonCodec.gen[Conflict]
    given schema: Schema[Conflict]   = DeriveSchema.gen[Conflict]
  }

  case class NotFound(id: String, `type`: String) extends ApiProblem

  object NotFound {
    given codec: JsonCodec[NotFound]                 = DeriveJsonCodec.gen[NotFound]
    given schema: Schema[NotFound]                   = DeriveSchema.gen[NotFound]
    def apply(shortcode: ProjectShortcode): NotFound = NotFound(shortcode.toString, "project")
  }

  case class BadRequest(errors: List[Argument]) extends ApiProblem

  object BadRequest {
    given codec: JsonCodec[BadRequest] = DeriveJsonCodec.gen[BadRequest]
    given schema: Schema[BadRequest]   = DeriveSchema.gen[BadRequest]

    def apply(error: Argument): BadRequest                  = BadRequest(List(error))
    def apply(argument: String, reason: String): BadRequest = BadRequest(Argument(argument, reason))

    case class Argument(argument: String, reason: String)
    object Argument {
      given codec: JsonCodec[Argument] = DeriveJsonCodec.gen[Argument]
      given schema: Schema[Argument]   = DeriveSchema.gen[Argument]
    }

    def invalidBody(reason: String): BadRequest = BadRequest.apply("Body", reason)

    def invalidHeaderContentType(actual: ContentType, expected: ContentType): BadRequest =
      invalidHeader("Content-Type", actual.toString, s"expected '$expected'")

    def invalidHeader(
      key: String,
      value: String,
      reason: String,
    ): BadRequest = BadRequest(s"Header: '$key''", s"'$value' is invalid: $reason")

    def invalidPathVariable(
      key: String,
      value: String,
      reason: String,
    ): BadRequest = BadRequest(s"Path variable: '$key''", s"'$value' is invalid: $reason")
  }

  case class InternalServerError(errorMessage: String) extends ApiProblem
  object InternalServerError {
    given codec: JsonCodec[InternalServerError] = DeriveJsonCodec.gen[InternalServerError]

    given schema: Schema[InternalServerError] = DeriveSchema.gen[InternalServerError]

    def apply(t: Throwable): InternalServerError = InternalServerError(t.getMessage)

    def apply(msg: String, t: Throwable): InternalServerError = InternalServerError(s"$msg: ${t.getMessage}")
  }

  case class Unhealthy(status: Status = DOWN) extends ApiProblem with HealthResponse
  object Unhealthy {
    given codec: JsonCodec[Unhealthy] = DeriveJsonCodec.gen[Unhealthy]
  }

  case class Unauthorized(reason: String) extends ApiProblem
  object Unauthorized {
    given codec: JsonCodec[Unauthorized] = DeriveJsonCodec.gen[Unauthorized]
  }

  case class Forbidden(reason: String) extends ApiProblem
  object Forbidden {
    given codec: JsonCodec[Forbidden] = DeriveJsonCodec.gen[Forbidden]
  }
}
