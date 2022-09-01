/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test.Assertion._
import zio.test._

import dsp.errors.ValidationException

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
        assertTrue(unsafeValid.value == str)
      }
    )
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
    ),
    suite("update MultiLangString")(
      suite("add")(
        test("add a single LangString") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            multiLangString <- MultiLangString.make(Set(en))
            res             <- multiLangString.addLangString(de)
          } yield assertTrue(res.langStrings == Set(en, de))).toZIO
        },
        test("not add a LangString that already exists") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            en2             <- LangString.make(LanguageCode.en, "another string in english")
            multiLangString <- MultiLangString.make(Set(en))
            res              = multiLangString.addLangString(en2)
          } yield assertTrue(
            res == Validation.fail(
              ValidationException(MultiLangStringErrorMessages.LanguageNotUnique(Set(LanguageCode.en)))
            )
          )).toZIO
        },
        test("add multiple LangStrings") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            fr              <- LangString.make(LanguageCode.fr, "string in french")
            multiLangString <- MultiLangString.make(Set(en))
            res             <- multiLangString.addLangStrings(Set(de, fr))
          } yield assertTrue(res.langStrings == Set(en, de, fr))).toZIO
        },
        test("not add a LangString that already exist") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            de2             <- LangString.make(LanguageCode.de, "anotherstring in german")
            fr              <- LangString.make(LanguageCode.de, "string in french")
            multiLangString <- MultiLangString.make(Set(en, de))
            res              = multiLangString.addLangStrings(Set(de2, fr))
          } yield assertTrue(
            res == Validation.fail(
              ValidationException(MultiLangStringErrorMessages.LanguageNotUnique(Set(LanguageCode.de)))
            )
          )).toZIO
        }
      ),
      suite("remove")(
        // TODO-BL: [discuss] what should happen, if it's not in there.
        // Currently nothing happens, the MultiLangString remains unchanged
        test("remove a LangString by language") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            multiLangString <- MultiLangString.make(Set(en, de))
            res             <- multiLangString.removeLanguage(LanguageCode.en)
          } yield assertTrue(res.langStrings == Set(en))).toZIO
        },
        test("remove a single LangString") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            multiLangString <- MultiLangString.make(Set(en, de))
            res             <- multiLangString.removeLangString(de)
          } yield assertTrue(res.langStrings == Set(en))).toZIO
        },
        test("do not remove the only LangString contained") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            multiLangString <- MultiLangString.make(Set(en))
            res              = multiLangString.removeLangString(en)
          } yield assertTrue(
            res == Validation.fail(ValidationException(MultiLangStringErrorMessages.MultiLangStringEmptySet))
          )).toZIO
        },
        test("remove multiple LangStrings") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            fr              <- LangString.make(LanguageCode.fr, "string in french")
            multiLangString <- MultiLangString.make(Set(en, de, fr))
            res             <- multiLangString.removeLangStrings(Set(de, fr))
          } yield assertTrue(res.langStrings == Set(en))).toZIO
        },
        test("do not remove all contained LangStrings") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            multiLangString <- MultiLangString.make(Set(en, de))
            res              = multiLangString.removeLangStrings(Set(de, en))
          } yield assertTrue(
            res == Validation.fail(ValidationException(MultiLangStringErrorMessages.MultiLangStringEmptySet))
          )).toZIO
        }
      ),
      suite("update")(
        test("update the value of a LangString") {
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            enChanged       <- LangString.make(LanguageCode.en, "different string in english")
            multiLangString <- MultiLangString.make(Set(en))
            res             <- multiLangString.updateLangString(enChanged)
          } yield assertTrue(res.langStrings == Set(enChanged))).toZIO
        },
        test("update the value of a LangString that is not yet existing") {
          // TODO-BL: [discuss] what should actually happen here? currently it's just added
          (for {
            en              <- LangString.make(LanguageCode.en, "string in english")
            de              <- LangString.make(LanguageCode.de, "string in german")
            multiLangString <- MultiLangString.make(Set(en))
            res             <- multiLangString.updateLangString(de)
          } yield assertTrue(res.langStrings == Set(en, de))).toZIO
        }
      )
    ),
    suite("look-up in MultiLangString")(
      test("get nothing back for a language that is not there") {
        val langStrings = Set(LangString.unsafeMake(LanguageCode.en, "string in english"))
        for {
          multiLangString <- MultiLangString.make(langStrings).toZIO
          res              = multiLangString.getByLanguage(LanguageCode.de)
        } yield assert(res)(isNone)
      },
      test("get the LangString back for a language that is there") {
        val langString = LangString.unsafeMake(LanguageCode.en, "string in english")
        for {
          multiLangString <- MultiLangString.make(Set(langString)).toZIO
          res              = multiLangString.getByLanguage(LanguageCode.en)
        } yield assert(res)(isSome[LangString](equalTo(langString)))
      }
    )
  )

}
