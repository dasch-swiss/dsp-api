/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio._
import zio.prelude.Validation
import zio.test.Assertion._
import zio.test._

import scala.util.Random

import dsp.errors.ValidationException
import org.knora.webapi.slice.admin.domain.model.KnoraProject._

/**
 * This spec is used to test the [[KnoraProject]] value objects creation.
 */
object KnoraProjectSpec extends ZIOSpecDefault {
  private val validDescription    = Seq(V2.StringLiteralV2(value = "Valid project description", language = Some("en")))
  private val tooShortDescription = Seq(V2.StringLiteralV2("Ab", Some("en")))
  private val tooLongDescription  = Seq(V2.StringLiteralV2(new Random().nextString(40961), Some("en")))
  private val validKeywords       = Seq("key", "word")
  private val tooShortKeywords    = Seq("de", "key", "word")
  private val tooLongKeywords     = Seq("ThisIs65CharactersKeywordThatShouldFailTheTestSoItHasToBeThatLong", "key", "word")
  private val validLogo           = "/fu/bar/baz.jpg"

  def spec = suite("ProjectSpec")(
    shortcodeTest,
    shortnameTest,
    nameTest,
    descriptionTest,
    keywordsTest,
    logoTest,
    projectStatusTest,
    projectSelfJoinTest
  )

  private val shortcodeTest = suite("Shortcode")(
    test("pass an empty value and return an error") {
      assertTrue(
        Shortcode.make("") == Validation.fail(ValidationException("Shortcode cannot be empty."))
      )
    },
    test("pass an invalid value and return an error") {
      val invalidShortcodes = Gen.fromIterable(Seq("123", "000G", "12345"))
      check(invalidShortcodes) { shortcode =>
        assertTrue(
          Shortcode.make(shortcode) == Validation.fail(ValidationException(s"Shortcode is invalid: $shortcode"))
        )
      }
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Shortcode.make("FFFF").map(_.value).contains("FFFF"))
    }
  )

  private val shortnameTest = suite("Shortname")(
    test("pass an empty value and return validation error") {
      assertTrue(
        Shortname.make("") == Validation.fail(ValidationException("Shortname cannot be empty."))
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
          Shortname.make(param) == Validation.fail(ValidationException(s"Shortname is invalid: $param"))
        )
      }
    },
    test("pass valid values and successfully create value objects") {
      val gen = Gen.fromIterable(
        Seq(
          "DefaultSharedOntologiesProject",
          "valid-shortname",
          "valid_shortname",
          "validshortname-",
          "validshortname_",
          "abc",
          "a20charLongShortname"
        )
      )
      check(gen)(param => assertTrue(Shortname.make(param).map(_.value) == Validation.succeed(param)))
    }
  )

  private val nameTest = suite("Name")(
    test("pass invalid Name and expect an error to be returned") {
      val invalidNames =
        Gen.stringBounded(0, 2)(Gen.printableChar) ++ Gen.stringBounded(257, 300)(Gen.printableChar)
      check(invalidNames) { name =>
        assertTrue(
          Longname.make(name) == Validation.fail(ValidationException("Longname must be 3 to 256 characters long."))
        )
      }
    },
    test("pass a valid value and successfully create value object") {
      val validNames = Gen.stringBounded(3, 256)(Gen.printableChar)
      check(validNames) { name =>
        assertTrue(Longname.make(name).map(_.value).contains(name))
      }
    }
  )

  private val descriptionTest = suite("Description")(
    test("pass an empty object and return an error") {
      assertTrue(
        Description.make(Seq.empty) == Validation.fail(ValidationException(ErrorMessages.ProjectDescriptionMissing)),
        Description.make(Some(Seq.empty)) == Validation.fail(
          ValidationException(ErrorMessages.ProjectDescriptionMissing)
        )
      )
    },
    test("pass an object containing invalid Description and expect an error to be returned") {
      assertTrue(
        Description.make(tooShortDescription) == Validation.fail(
          ValidationException(ErrorMessages.ProjectDescriptionInvalid)
        ),
        Description.make(tooLongDescription) == Validation.fail(
          ValidationException(ErrorMessages.ProjectDescriptionInvalid)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      for {
        description           <- Description.make(validDescription).toZIO
        optionalDescription   <- Description.make(Option(validDescription)).toZIO
        descriptionFromOption <- ZIO.fromOption(optionalDescription)
      } yield assertTrue(description.value == validDescription) &&
        assert(optionalDescription)(isSome(isSubtype[Description](Assertion.anything))) &&
        assertTrue(descriptionFromOption.value == validDescription)
    },
    test("successfully validate passing None") {
      assertTrue(
        Description.make(None) == Validation.succeed(None)
      )
    }
  )

  private val keywordsTest = suite("Keywords")(
    test("pass an empty object and return an error") {
      assertTrue(
        Keywords.make(Seq.empty) == Validation.fail(ValidationException(ErrorMessages.KeywordsMissing)),
        Keywords.make(Some(Seq.empty)) == Validation.fail(ValidationException(ErrorMessages.KeywordsMissing))
      )
    },
    test("pass invalid keywords and return an error") {
      assertTrue(
        Keywords.make(tooShortKeywords) == Validation.fail(ValidationException(ErrorMessages.KeywordsInvalid)),
        Keywords.make(tooLongKeywords) == Validation.fail(ValidationException(ErrorMessages.KeywordsInvalid))
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

  private val logoTest = suite("Logo")(
    test("pass an empty object and return an error") {
      assertTrue(
        Logo.make("") == Validation.fail(ValidationException(ErrorMessages.LogoMissing)),
        Logo.make(Some("")) == Validation.fail(ValidationException(ErrorMessages.LogoMissing))
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

  private val projectStatusTest = suite("ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        ProjectStatus.from(true) == ProjectStatus.Active,
        ProjectStatus.from(false) == ProjectStatus.Inactive
      )
    },
    test("value should be the correct boolean") {
      assertTrue(ProjectStatus.Active.value, !ProjectStatus.Inactive.value)
    }
  )

  private val projectSelfJoinTest = suite("ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        ProjectSelfJoin.from(true) == ProjectSelfJoin.CanJoin,
        ProjectSelfJoin.from(false) == ProjectSelfJoin.CannotJoin
      )
    },
    test("value should be the correct boolean") {
      assertTrue(
        ProjectSelfJoin.CanJoin.value,
        !ProjectSelfJoin.CannotJoin.value
      )
    }
  )
}
