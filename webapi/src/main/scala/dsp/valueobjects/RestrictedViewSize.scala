/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import zio.prelude.Validation

case class RestrictedViewSize(value: String) extends ProjectsADMJsonProtocol

object RestrictedViewSize {
  def make(value: String): Validation[Throwable, RestrictedViewSize] =
    if (value.toString.isEmpty) Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeMissing))
    else Validation.succeed(new RestrictedViewSize(s"!$value,$value") {})
//      value match {
//        case v if v == 0           => Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeInvalid))
//        case v if v > 1 && v < 100 => Validation.succeed(new RestrictedViewSize(s"pct:$v") {})
//        case v if v > 100          => Validation.succeed(new RestrictedViewSize(s"!$v,$v") {})
//      }
}

object ErrorMessages {
  val RestrictedViewSizeMissing = "RestrictedViewSize cannot be empty."
  val RestrictedViewSizeInvalid = "RestrictedViewSize is invalid."
}
