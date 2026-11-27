/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.Schema.annotations.encodedName
import swiss.dasch.api.ApiProblem.BadRequest.Argument
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.infrastructure.AggregatedHealth
import swiss.dasch.infrastructure.Health
import swiss.dasch.infrastructure.Health.Status
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.jsonField
import zio.schema.DeriveSchema
import zio.schema.Schema

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

  /** A well-formed request whose file content cannot be ingested. */
  case class UnprocessableContent(
    @jsonField("correlation_id") @encodedName("correlation_id") correlationId: String,
    @jsonField("error_code") @encodedName("error_code") errorCode: String,
    message: String,
  ) extends ApiProblem
  object UnprocessableContent {
    given codec: JsonCodec[UnprocessableContent] = DeriveJsonCodec.gen[UnprocessableContent]
    given schema: Schema[UnprocessableContent]   = DeriveSchema.gen[UnprocessableContent]
  }

  /** The message is fixed text; it must never come from a `Throwable`, which can hold paths and process output. */
  case class InternalServerError(
    @jsonField("correlation_id") @encodedName("correlation_id") correlationId: String,
    @jsonField("error_code") @encodedName("error_code") errorCode: String,
    message: String,
  ) extends ApiProblem
  object InternalServerError {
    given codec: JsonCodec[InternalServerError] = DeriveJsonCodec.gen[InternalServerError]
    given schema: Schema[InternalServerError]   = DeriveSchema.gen[InternalServerError]
  }

  case class Unhealthy(status: Status, components: Option[Map[String, Health]]) extends ApiProblem
  object Unhealthy {
    def from(ag: AggregatedHealth): Unhealthy = Unhealthy(ag.status, ag.components)
    given codec: JsonCodec[Unhealthy]         = DeriveJsonCodec.gen[Unhealthy]
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
