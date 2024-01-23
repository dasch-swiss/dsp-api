/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import scala.util.matching.Regex

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

/**
 * RestrictedViewSize value object.
 */
case class RestrictedViewSize private (value: String) extends StringValue

object RestrictedViewSize extends StringValueCompanion[RestrictedViewSize] {
  def default: RestrictedViewSize = new RestrictedViewSize("!512,512") {}
  def from(value: String): Either[String, RestrictedViewSize] = {
    val trimmed: String = value.trim
    // matches strings "pct:1-100"
    val percentagePattern: Regex = "pct:[1-9][0-9]?0?$".r
    // matches strings "!x,x" where x is a number of pixels
    val dimensionsPattern: Regex = "!\\d+,\\d+$".r
    def isSquare: Boolean = {
      val substr = trimmed.substring(1).split(",").toSeq
      substr.head == substr.last
    }

    if (value.isEmpty) Left(ErrorMessages.RestrictedViewSizeMissing)
    else if (percentagePattern.matches(trimmed)) Right(new RestrictedViewSize(trimmed) {})
    else if (dimensionsPattern.matches(trimmed) && isSquare) Right(new RestrictedViewSize(trimmed) {})
    else Left(ErrorMessages.RestrictedViewSizeInvalid(value))
  }

}

object ErrorMessages {
  val RestrictedViewSizeMissing = "RestrictedViewSize cannot be empty."
  val RestrictedViewSizeInvalid = (v: String) => s"Invalid RestrictedViewSize: $v"
}
