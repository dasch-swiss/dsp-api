/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio._
import zio.prelude.Validation
import zio.test.Assertion._
import zio.test._
import dsp.errors.ValidationException
import dsp.valueobjects.Project._

/**
 * This spec is used to test the [[Project]] value objects creation.
 */
object ProjectSpec extends ZIOSpecDefault {
  private val validShortcode   = "1234"
  private val invalidShortcode = "12345"
  private val validName        = "That is the project longname"
  private val validDescription = Seq(
    V2.StringLiteralV2(value = "Valid project description", language = Some("en"))
  )
  private val validKeywords    = Seq("key", "word")
  private val tooShortKeywords = Seq("de", "key", "word")
  private val tooLongKeywords  = Seq("ThisIs65CharactersKeywordThatShouldFailTheTestSoItHasToBeThatLong", "key", "word")
  private val validLogo        = "/fu/bar/baz.jpg"

  def spec = suite("ProjectSpec")(
    shortcodeTest,
    shortnameTest,
    nameTest,
    projectDescriptionsTest,
    keywordsTest,
    logoTest,
    projectStatusTest,
    projectSelfJoinTest
  )

  private val shortcodeTest = suite("ProjectSpec - Shortcode")(
    test("pass an empty value and return an error") {
      assertTrue(
        Shortcode.make("") == Validation.fail(ValidationException(ProjectErrorMessages.ShortcodeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Shortcode.make(invalidShortcode) == Validation.fail(
          ValidationException(ProjectErrorMessages.ShortcodeInvalid(invalidShortcode))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      for {
        shortcode <- Shortcode.make(validShortcode).toZIO
      } yield assertTrue(shortcode.value == validShortcode)
    }
  )

  private val shortnameTest = suite("ProjectSpec - Shortname")(
    test("pass an empty value and return validation error") {
      assertTrue(
        Shortname.make("") == Validation.fail(ValidationException(ProjectErrorMessages.ShortnameMissing))
      )
    },
    test("pass invalid values and return validation error") {
      val gen = Gen.fromIterable(
        Seq(
          "invalid:shortname",
          "-invalidshortname",
          "_invalidshortname",
          ".invalidshortname",
          "invalid/shortname",
          "invalid@shortname",
          "1invalidshortname",
          " invalidshortname",
          "invalid shortname",
          "a",
          "ab",
          "just-21char-shortname"
        )
      )
      check(gen) { param =>
        assertTrue(
          Shortname.make(param) == Validation.fail(ValidationException(ProjectErrorMessages.ShortnameInvalid(param)))
        )
      }
    },
    test("pass valid values and successfully create value objects") {
      val gen = Gen.fromIterable(
        Seq("valid-shortname", "valid_shortname", "validshortname-", "validshortname_", "abc", "a20charLongShortname")
      )
      check(gen) { param =>
        assertTrue(
          Shortname.make(param).map(_.value) == Validation.succeed(param)
        ) && assert(Shortname.make(param).toOption)(isSome(isSubtype[Shortname](Assertion.anything)))
      }
    },
    test("successfully validate passing None") {
      assertTrue(
        Shortname.make(None) == Validation.succeed(None)
      )
    }
  )

  private val nameTest = suite("ProjectSpec - Name")(
    test("pass an empty value and return an error") {
      assertTrue(
        Name.make("") == Validation.fail(ValidationException(ProjectErrorMessages.NameMissing)),
        Name.make(Some("")) == Validation.fail(ValidationException(ProjectErrorMessages.NameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      for {
        name           <- Name.make(validName).toZIO
        optionalName   <- Name.make(Option(validName)).toZIO
        nameFromOption <- ZIO.fromOption(optionalName)
      } yield assertTrue(name.value == validName) &&
        assert(optionalName)(isSome(isSubtype[Name](Assertion.anything))) &&
        assertTrue(nameFromOption.value == validName)
    }
  )

  private val projectDescriptionsTest = suite("ProjectSpec - ProjectDescriptions")(
    test("pass an empty object and return an error") {
      assertTrue(
        ProjectDescription.make(Seq.empty) == Validation.fail(
          ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing)
        ),
        ProjectDescription.make(Some(Seq.empty)) == Validation.fail(
          ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      for {
        description           <- ProjectDescription.make(validDescription).toZIO
        optionalDescription   <- ProjectDescription.make(Option(validDescription)).toZIO
        descriptionFromOption <- ZIO.fromOption(optionalDescription)
      } yield assertTrue(description.value == validDescription) &&
        assert(optionalDescription)(isSome(isSubtype[ProjectDescription](Assertion.anything))) &&
        assertTrue(descriptionFromOption.value == validDescription)
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectDescription.make(None) == Validation.succeed(None)
      )
    }
  )

  private val keywordsTest = suite("ProjectSpec - Keywords")(
    test("pass an empty object and return an error") {
      assertTrue(
        Keywords.make(Seq.empty) == Validation.fail(ValidationException(ProjectErrorMessages.KeywordsMissing)),
        Keywords.make(Some(Seq.empty)) == Validation.fail(ValidationException(ProjectErrorMessages.KeywordsMissing))
      )
    },
    test("pass invalid keywords and return an error") {
      assertTrue(
        Keywords.make(tooShortKeywords) == Validation.fail(ValidationException(ProjectErrorMessages.KeywordsInvalid)),
        Keywords.make(tooLongKeywords) == Validation.fail(ValidationException(ProjectErrorMessages.KeywordsInvalid))
      )
    },
    test("pass a valid object and successfully create value object") {
      for {
        keywords           <- Keywords.make(validKeywords).toZIO
        optionalKeywords   <- Keywords.make(Option(validKeywords)).toZIO
        keywordsFromOption <- ZIO.fromOption(optionalKeywords)
      } yield assertTrue(keywords.value == validKeywords) &&
        assert(optionalKeywords)(isSome(isSubtype[Keywords](Assertion.anything))) &&
        assertTrue(keywordsFromOption.value == validKeywords)
    },
    test("successfully validate passing None") {
      assertTrue(
        Keywords.make(None) == Validation.succeed(None)
      )
    }
  )

  private val logoTest = suite("ProjectSpec - Logo")(
    test("pass an empty object and return an error") {
      assertTrue(
        Logo.make("") == Validation.fail(ValidationException(ProjectErrorMessages.LogoMissing)),
        Logo.make(Some("")) == Validation.fail(ValidationException(ProjectErrorMessages.LogoMissing))
      )
    },
    test("pass a valid object and successfully create value object") {
      for {
        logo           <- Logo.make(validLogo).toZIO
        optionalLogo   <- Logo.make(Option(validLogo)).toZIO
        logoFromOption <- ZIO.fromOption(optionalLogo)
      } yield assertTrue(logo.value == validLogo) &&
        assert(optionalLogo)(isSome(isSubtype[Logo](Assertion.anything))) &&
        assertTrue(logoFromOption.value == validLogo)
    },
    test("successfully validate passing None") {
      assertTrue(
        Keywords.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectStatusTest = suite("ProjectSpec - ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      for {
        status           <- ProjectStatus.make(true).toZIO
        optionalStatus   <- ProjectStatus.make(Option(false)).toZIO
        statusFromOption <- ZIO.fromOption(optionalStatus)
      } yield assertTrue(status.value, !statusFromOption.value) &&
        assert(optionalStatus)(isSome(isSubtype[ProjectStatus](Assertion.anything)))
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectStatus.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectSelfJoinTest = suite("ProjectSpec - ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      for {
        selfJoin           <- ProjectSelfJoin.make(true).toZIO
        optionalSelfJoin   <- ProjectSelfJoin.make(Option(false)).toZIO
        selfJoinFromOption <- ZIO.fromOption(optionalSelfJoin)
      } yield assertTrue(selfJoin.value, !selfJoinFromOption.value) &&
        assert(optionalSelfJoin)(isSome(isSubtype[ProjectSelfJoin](Assertion.anything)))
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectSelfJoin.make(None) == Validation.succeed(None)
      )
    }
  )
}
