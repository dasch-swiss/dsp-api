/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.json.JsonCodec
import zio.prelude.Validation

import dsp.errors.ValidationException

/**
 * LanguageCode value object.
 */
sealed abstract case class LanguageCode private (value: String)

object LanguageCode { self =>
  implicit val codec: JsonCodec[LanguageCode] =
    JsonCodec[String].transformOrFail(
      value => LanguageCode.make(value).toEitherWith(e => e.head.getMessage()),
      languageCode => languageCode.value
    )

  val DE: String = "de"
  val EN: String = "en"
  val FR: String = "fr"
  val IT: String = "it"
  val RM: String = "rm"
  // TODO-BL: with NewTypes we shouldn't need the strings separately anymore, as the valueobject can be used just like a string value

  val SupportedLanguageCodes: Set[String] = Set(
    DE,
    EN,
    FR,
    IT,
    RM
  )

  def make(value: String): Validation[ValidationException, LanguageCode] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeMissing))
    } else if (!SupportedLanguageCodes.contains(value)) {
      Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeInvalid(value)))
    } else {
      Validation.succeed(new LanguageCode(value) {})
    }

  lazy val en: LanguageCode = new LanguageCode(EN) {}
  lazy val de: LanguageCode = new LanguageCode(DE) {}
  lazy val fr: LanguageCode = new LanguageCode(FR) {}
  lazy val it: LanguageCode = new LanguageCode(IT) {}
  lazy val rm: LanguageCode = new LanguageCode(RM) {}
}

object LanguageCodeErrorMessages {
  val LanguageCodeMissing               = "LanguageCode cannot be empty."
  def LanguageCodeInvalid(lang: String) = s"LanguageCode '$lang' is invalid."
}
