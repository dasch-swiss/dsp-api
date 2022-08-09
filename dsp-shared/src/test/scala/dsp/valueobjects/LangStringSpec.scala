/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.ValidationException
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.LangString]] value objects creation.
 */
object LangStringSpec extends ZIOSpecDefault {

  def spec = (langStringTest + multiLangStringTest)

  private val langStringTest = suite("LangString")(
    suite("`make()` smart constructor")(
      test("pass an empty string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.make(LanguageCode.en, "")
        assertTrue(result == expected)
      },
      test("pass an invalid string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.make(LanguageCode.en, "\t\n  ") // blank only is not allowed
        assertTrue(result == expected)
      },
      test("pass a valid value and successfully create value object") {
        val stringValue = "Some valid string"
        (for {
          result <- LangString.make(LanguageCode.en, stringValue)
        } yield assertTrue(result.language.value == "en") &&
          assertTrue(result.value == stringValue)).toZIO
      }
    ),
    suite("`makeFromStrings()` smart constructor")(
      test("pass an invalid language value and return an error") {
        val invalidLanguageCode = "english"
        val expected =
          Validation.fail(ValidationException(LanguageCodeErrorMessages.LanguageCodeInvalid(invalidLanguageCode)))
        val result = LangString.makeFromStrings(invalidLanguageCode, "ok string value")
        assertTrue(result == expected)
      },
      test("pass an empty string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.makeFromStrings("en", "")
        assertTrue(result == expected)
      },
      test("pass an invalid string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.makeFromStrings("en", "\t\n  ") // blank only is not allowed
        assertTrue(result == expected)
      },
      test("pass a valid value and successfully create value object") {
        val stringValue = "Some valid string"
        (for {
          result <- LangString.makeFromStrings("en", stringValue)
        } yield assertTrue(result.language.value == "en") &&
          assertTrue(result.value == stringValue)).toZIO
      }
    ),
    suite("`unsafeMake()` unsafe constructor")(
      test("create a valid LangString through the unsafe method") {
        val str         = "some langstring"
        val unsafeValid = LangString.unsafeMake(LanguageCode.en, str)
        assertTrue(unsafeValid.language.value == "en") &&
        assertTrue(unsafeValid.value == str)
      },
      test("create an invalid LangString through the unsafe method") {
        val str         = ""
        val unsafeValid = LangString.unsafeMake(LanguageCode.en, str)
        assertTrue(unsafeValid.language.value == "en") &&
        assertTrue(unsafeValid.value == str) // TODO-BL: [discuss] figure out how to test for logging output
      }
    )
  )

  private val multiLangStringTest = suite("MultiLangString")(
    test("pass an empty set of LangString and return an error") {
      val res      = MultiLangString.make(Set.empty)
      val expected = Validation.fail(ValidationException(MultiLangStringErrorMessages.MultiLangStringEmptySet))
      assertTrue(res == expected)
    },
    test("pass a set of LangString with non unique languages and return an error") {
      val langStrings = Set(
        LangString.unsafeMake(LanguageCode.en, "english 1"),
        LangString.unsafeMake(LanguageCode.en, "english 2"),
        LangString.unsafeMake(LanguageCode.de, "german 1"),
        LangString.unsafeMake(LanguageCode.de, "german 2"),
        LangString.unsafeMake(LanguageCode.fr, "french 1")
      )
      val nonUniqueLanguages = Set(LanguageCode.en, LanguageCode.de)
      val res                = MultiLangString.make(langStrings)
      val expected =
        Validation.fail(ValidationException(MultiLangStringErrorMessages.LanguageNotUnique(nonUniqueLanguages)))
      assertTrue(res == expected)
    },
    test("pass a valid set of LangString and return a MultiLangString") {
      val langStrings = Set(
        LangString.unsafeMake(LanguageCode.en, "string in english"),
        LangString.unsafeMake(LanguageCode.de, "string in german"),
        LangString.unsafeMake(LanguageCode.fr, "string in french")
      )
      (for {
        res <- MultiLangString.make(langStrings)
      } yield (
        assertTrue(res.langStrings.size == 3) &&
          assertTrue(res.langStrings == langStrings)
      )).toZIO
    }
  )

}
