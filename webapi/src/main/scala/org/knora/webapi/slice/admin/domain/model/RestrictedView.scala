/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import scala.util.matching.Regex

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class RestrictedView(size: RestrictedViewSize, watermark: Boolean)

case class RestrictedViewSize private (value: String) extends AnyVal with StringValue

object RestrictedViewSize extends StringValueCompanion[RestrictedViewSize] {

  val default: RestrictedViewSize = RestrictedViewSize.unsafeFrom("!512,512")

  // matches strings "pct:n" with n between 1 and 100
  private val percentage: Regex = "pct:(?:100|[1-9][0-9]?)$".r

  // matches strings "!x,x" where x is a positive integer and represents the dimensions of the restricted view
  private val dimensionsPattern: Regex = "!(\\d+),(\\1)$".r

  def from(value: String): Either[String, RestrictedViewSize] =
    value match {
      case _ if value.isEmpty                    => Left("RestrictedViewSize cannot be empty.")
      case _ if percentage.matches(value)        => Right(RestrictedViewSize(value))
      case _ if dimensionsPattern.matches(value) => Right(RestrictedViewSize(value))
      case _                                     => Left(s"Invalid RestrictedViewSize: $value")
    }
}
