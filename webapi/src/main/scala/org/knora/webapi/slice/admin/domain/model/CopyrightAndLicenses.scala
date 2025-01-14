/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import java.time.LocalDate
import scala.util.Try

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion.*
import org.knora.webapi.slice.common.StringValueCompanion.maxLength
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

final case class CopyrightHolder private (override val value: String) extends StringValue
object CopyrightHolder extends StringValueCompanion[CopyrightHolder] {
  def from(str: String): Either[String, CopyrightHolder] =
    fromValidations(
      "Copyright Holder",
      CopyrightHolder.apply,
      List(nonEmpty, noLineBreaks, maxLength(1_000)),
    )(str)
}

final case class Authorship private (override val value: String) extends StringValue
object Authorship extends StringValueCompanion[Authorship] {
  def from(str: String): Either[String, Authorship] =
    fromValidations("Authorship", Authorship.apply, List(nonEmpty, noLineBreaks, maxLength(1_000)))(str)
}

final case class LicenseText private (override val value: String) extends StringValue
object LicenseText extends StringValueCompanion[LicenseText] {
  def from(str: String): Either[String, LicenseText] =
    fromValidations("License text", LicenseText.apply, List(nonEmpty, maxLength(100_000)))(str)
}

final case class LicenseUri private (override val value: String) extends StringValue
object LicenseUri extends StringValueCompanion[LicenseUri] {
  def from(str: String): Either[String, LicenseUri] =
    fromValidations("License URI", LicenseUri.apply, List(nonEmpty, isUri))(str)
}

final case class LicenseDate private (override val value: LocalDate) extends Value[LocalDate]
object LicenseDate extends WithFrom[String, LicenseDate] {
  def makeNew: LicenseDate = LicenseDate(LocalDate.now())
  def from(str: String): Either[String, LicenseDate] =
    Try(LocalDate.parse(str)).toEither.left
      .map(_ => "License Date must be in format 'YYYY-MM-DD'.")
      .map(LicenseDate.apply)
  def from(date: LocalDate): LicenseDate = LicenseDate(date)
}
