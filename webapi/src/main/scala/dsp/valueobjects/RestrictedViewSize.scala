/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol

case class RestrictedViewSize(value: String)

object RestrictedViewSize {
  def make(value: String): Validation[Throwable, RestrictedViewSize] = {
    // matches strings "pct:1-100"
    val pctPattern: Regex = "pct:[1-9][0-9]?0?$".r
    // matches strings "!x,x" where x is a number of pixels
    val pixelPattern: Regex = "!\\d+,\\d+$".r
    def isSquare(value: String): Boolean = {
      val substr = value.substring(1).split(",").toSeq
      substr.head == substr.last
    }

    if (value.isEmpty) Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeMissing))
    else if (!pctPattern.matches(value) || !pixelPattern.matches(value))
      Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeInvalid))
    else if (pixelPattern.matches(value) && !isSquare(value))
      Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeInvalid))
    else Validation.succeed(new RestrictedViewSize(value) {})
  }
}

object ErrorMessages {
  val RestrictedViewSizeMissing = "RestrictedViewSize cannot be empty."
  val RestrictedViewSizeInvalid = "RestrictedViewSize is invalid."
}
