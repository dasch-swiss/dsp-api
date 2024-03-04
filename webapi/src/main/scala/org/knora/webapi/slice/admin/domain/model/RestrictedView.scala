/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import scala.util.matching.Regex

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue

sealed trait RestrictedView

object RestrictedView {

  val default: RestrictedView = Size.default

  final case class Watermark private (value: Boolean) extends RestrictedView with BooleanValue
  object Watermark {

    val On: Watermark  = Watermark(true)
    val Off: Watermark = Watermark(false)

    def from(value: Boolean): Watermark = if (value) On else Off
  }

  final case class Size private (value: String) extends RestrictedView with StringValue

  object Size extends StringValueCompanion[Size] {

    // matches strings "pct:n" with n between 1 and 100
    private val percentagePattern: Regex = "pct:(?:100|[1-9][0-9]?)$".r

    // matches strings "!x,x" where x is a positive integer and represents the dimensions of the restricted view
    private val dimensionsPattern: Regex = "!(\\d+),(\\1)$".r

    val default: Size = Size.unsafeFrom("!128,128")

    def from(value: String): Either[String, Size] =
      value match {
        case _ if value.isEmpty                    => Left("RestrictedViewSize cannot be empty.")
        case _ if percentagePattern.matches(value) => Right(Size(value))
        case _ if dimensionsPattern.matches(value) => Right(Size(value))
        case _                                     => Left(s"Invalid RestrictedViewSize: $value")
      }
  }
}
