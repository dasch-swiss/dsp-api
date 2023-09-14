/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.BadRequestException

case class RestrictedViewSize(value: String)

object RestrictedViewSize {
  def make(value: String): Validation[Throwable, RestrictedViewSize] = {
    val trimmed = value.trim
    // matches strings "pct:1-100"
    val percentagePattern: Regex = "pct:[1-9][0-9]?0?$".r
    // matches strings "!x,x" where x is a number of pixels
    val dimensionsPattern: Regex = "!\\d+,\\d+$".r
    def isSquare(value: String): Boolean = {
      val substr = trimmed.substring(1).split(",").toSeq
      substr.head == substr.last
    }
    println(111, value, percentagePattern.matches(value), dimensionsPattern.matches(value), isSquare(value))

    if (value.isEmpty) Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeMissing))
    else if (percentagePattern.matches(trimmed)) Validation.succeed(new RestrictedViewSize(trimmed) {})
    else if (dimensionsPattern.matches(trimmed) && isSquare(value))
      Validation.succeed(new RestrictedViewSize(trimmed) {})
    else Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeInvalid))
  }
}

object ErrorMessages {
  val RestrictedViewSizeMissing = "RestrictedViewSize cannot be empty."
  val RestrictedViewSizeInvalid = "RestrictedViewSize is invalid."
}
