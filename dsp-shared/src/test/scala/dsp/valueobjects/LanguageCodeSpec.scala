/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.ValidationException
import zio.prelude.Validation
import zio.test._

object LanguageCodeSpec extends ZIOSpecDefault {

  private val validLanguageCode   = "de"
  private val invalidLanguageCode = "00"

  def spec = (languageCodeTest)

  private val languageCodeTest = suite("LanguageCode")(
    test("pass an empty value and return an error") {
      assertTrue(
        LanguageCode.make("") == Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        LanguageCode.make(invalidLanguageCode) == Validation.fail(
          ValidationException(LanguageCodeErrorMessages.LanguageCodeInvalid(invalidLanguageCode))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(LanguageCode.make(validLanguageCode).toOption.get.value == validLanguageCode)
    },
    suite("default language codes should be correct")(
      test("English") {
        val lang = "en"
        (for {
          manual <- LanguageCode.make(lang)
          default = LanguageCode.en
        } yield assertTrue(manual == default) &&
          assertTrue(default.value == lang)).toZIO
      },
      test("German") {
        val lang = "de"
        (for {
          manual <- LanguageCode.make(lang)
          default = LanguageCode.de
        } yield assertTrue(manual == default) &&
          assertTrue(default.value == lang)).toZIO
      },
      test("French") {
        val lang = "fr"
        (for {
          manual <- LanguageCode.make(lang)
          default = LanguageCode.fr
        } yield assertTrue(manual == default) &&
          assertTrue(default.value == lang)).toZIO
      },
      test("Italian") {
        val lang = "it"
        (for {
          manual <- LanguageCode.make(lang)
          default = LanguageCode.it
        } yield assertTrue(manual == default) &&
          assertTrue(default.value == lang)).toZIO
      },
      test("Romansh") {
        val lang = "rm"
        (for {
          manual <- LanguageCode.make(lang)
          default = LanguageCode.rm
        } yield assertTrue(manual == default) &&
          assertTrue(default.value == lang)).toZIO
      }
    )
  )

}
