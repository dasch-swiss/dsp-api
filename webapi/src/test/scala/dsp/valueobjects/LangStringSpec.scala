/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.ZIO
import zio.prelude.Validation
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.ValidationException
import org.knora.webapi.slice.common.domain.LanguageCode

/**
 * This spec is used to test the [[dsp.valueobjects.LangString]] value objects creation.
 */
object LangStringSpec extends ZIOSpecDefault {

  def spec: Spec[Any, ValidationException] = suite("LangStringSpec")(
    langStringTest,
    multiLangStringTest,
  )

  private val langStringTest = suite("LangString")(
    suite("`make()` smart constructor")(
      test("pass an empty string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.make(LanguageCode.EN, "")
        assertTrue(result == expected)
      },
      test("pass an invalid string value and return an error") {
        val expected = Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
        val result   = LangString.make(LanguageCode.EN, "\t\n  ") // blank only is not allowed
        assertTrue(result == expected)
      },
      test("pass a valid value and successfully create value object") {
        val stringValue = "Some valid string"
        (for {
          result <- LangString.make(LanguageCode.EN, stringValue)
        } yield assertTrue(result.language == LanguageCode.EN, result.value == stringValue)).toZIO
      },
    ),
    suite("`makeFromStrings()` smart constructor")(
      test("pass an invalid language value and return an error") {
        val invalidLanguageCode = "english"
        val expected            =
          Validation.fail(
            ValidationException("Unsupported language code: english, supported codes are: de, en, fr, it, rm"),
          )
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
        } yield assertTrue(result.language == LanguageCode.EN, result.value == stringValue)).toZIO
      },
    ),
    suite("`unsafeMake()` unsafe constructor")(
      test("create a valid LangString through the unsafe method") {
        val str         = "some langstring"
        val unsafeValid = LangString.unsafeMake(LanguageCode.EN, str)
        assertTrue(unsafeValid.language == LanguageCode.EN, unsafeValid.value == str)
      },
      test("not create an invalid LangString through the unsafe method") {
        val str = ""
        ZIO
          .attempt(LangString.unsafeMake(LanguageCode.EN, str))
          .exit
          .map(actual => assert(actual)(failsWithA[IllegalArgumentException]))
      },
    ),
  )

  private val multiLangStringTest = suite("MultiLangString")(
    suite("create MultiLangString")(
      test("pass an empty set of LangString and return an error") {
        val res      = MultiLangString.make(Set.empty)
        val expected = Validation.fail(ValidationException(MultiLangStringErrorMessages.MultiLangStringEmptySet))
        assertTrue(res == expected)
      },
      test("pass a set of LangString with non unique languages and return an error") {
        val langStrings = Set(
          LangString.unsafeMake(LanguageCode.EN, "english 1"),
          LangString.unsafeMake(LanguageCode.EN, "english 2"),
          LangString.unsafeMake(LanguageCode.DE, "german 1"),
          LangString.unsafeMake(LanguageCode.DE, "german 2"),
          LangString.unsafeMake(LanguageCode.FR, "french 1"),
        )
        val nonUniqueLanguages = Set(LanguageCode.EN, LanguageCode.DE)
        val res                = MultiLangString.make(langStrings)
        val expected           =
          Validation.fail(ValidationException(MultiLangStringErrorMessages.LanguageNotUnique(nonUniqueLanguages)))
        assertTrue(res == expected)
      },
      test("pass a valid set of LangString and return a MultiLangString") {
        val langStrings = Set(
          LangString.unsafeMake(LanguageCode.EN, "string in english"),
          LangString.unsafeMake(LanguageCode.DE, "string in german"),
          LangString.unsafeMake(LanguageCode.FR, "string in french"),
        )
        MultiLangString.make(langStrings).toZIO.map(res => assertTrue(res.langStrings == langStrings))
      },
    ),
  )
}
