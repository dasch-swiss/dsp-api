/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.ProjectShortcode
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }

sealed trait ApiProblem

case class ProjectNotFound(shortcode: String) extends ApiProblem

object ProjectNotFound {
  def make(shortcode: ProjectShortcode): ProjectNotFound = ProjectNotFound(shortcode.toString)
}

case class IllegalArguments(errors: Map[String, String]) extends ApiProblem

case class InternalProblem(errorMessage: String) extends ApiProblem

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
    ): IllegalArguments = IllegalArguments(Map(s"Invalid path var: $key" -> s"'$value' is invalid: $reason"))

  def projectNotFound(shortcode: ProjectShortcode): ProjectNotFound = ProjectNotFound.make(shortcode)
}
