/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.Scope
import zio.prelude.Validation
import zio.test.*

import scala.util.Random

import dsp.errors.ValidationException
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*

/**
 * This spec is used to test the [[KnoraProject]] value objects creation.
 */
object KnoraProjectSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment & Scope, Nothing] = suite("ProjectSpec")(
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
        Shortcode.from("") == Validation.fail(ValidationException("Shortcode cannot be empty."))
      )
    },
    test("pass an invalid value and return an error") {
      val invalidShortcodes = Gen.fromIterable(Seq("123", "000G", "12345"))
      check(invalidShortcodes) { shortcode =>
        assertTrue(
          Shortcode.from(shortcode) == Validation.fail(ValidationException(s"Shortcode is invalid: $shortcode"))
        )
      }
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Shortcode.from("FFFF").map(_.value).contains("FFFF"))
    }
  )

  private val shortnameTest = suite("Shortname")(
    test("pass an empty value and return validation error") {
      assertTrue(
        Shortname.from("") == Validation.fail(ValidationException("Shortname cannot be empty."))
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
          Shortname.from(param) == Validation.fail(ValidationException(s"Shortname is invalid: $param"))
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
      check(gen)(param => assertTrue(Shortname.from(param).map(_.value) == Validation.succeed(param)))
    }
  )

  private val nameTest = suite("Name")(
    test("pass invalid Name and expect an error to be returned") {
      val invalidNames =
        Gen.stringBounded(0, 2)(Gen.printableChar) ++ Gen.stringBounded(257, 300)(Gen.printableChar)
      check(invalidNames) { name =>
        assertTrue(
          Longname.from(name) == Validation.fail(ValidationException("Longname must be 3 to 256 characters long."))
        )
      }
    },
    test("pass a valid value and successfully create value object") {
      val validNames = Gen.stringBounded(3, 256)(Gen.printableChar)
      check(validNames) { name =>
        assertTrue(Longname.from(name).map(_.value).contains(name))
      }
    }
  )

  private val descriptionTest = suite("Description")(
    test("pass an object containing too short Description and expect an error to be returned") {
      assertTrue(
        Description.from(V2.StringLiteralV2("Ab", Some("en"))) ==
          Validation.fail(ValidationException("Description must be 3 to 40960 characters long."))
      )
    },
    test("pass an object containing too long Description and expect an error to be returned") {
      assertTrue(
        Description.from(V2.StringLiteralV2(new Random().nextString(40961), Some("en"))) ==
          Validation.fail(ValidationException("Description must be 3 to 40960 characters long."))
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Description.from(V2.StringLiteralV2(value = "Valid project description", language = Some("en"))).map(_.value) ==
          Validation.succeed(V2.StringLiteralV2(value = "Valid project description", language = Some("en")))
      )
    }
  )

  private val keywordsTest = suite("Keywords")(
    test("pass an empty object and return an error") {
      val invalidKeywords =
        Gen.fromIterable(Seq("ThisIs65CharactersKeywordThatShouldFailTheTestSoItHasToBeThatLong", "12", "1"))
      check(invalidKeywords) { keyword =>
        assertTrue(
          Keyword.from(keyword) == Validation.fail(ValidationException("Keyword must be 3 to 64 characters long."))
        )
      }
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(Keyword.from("validKeyword").map(_.value).contains("validKeyword"))
    }
  )

  private val logoTest = suite("Logo")(
    test("pass an empty object and return an error") {
      assertTrue(Logo.from("") == Validation.fail(ValidationException("Logo cannot be empty.")))
    },
    test("pass a valid object and successfully create value object") {
      val validLogo = "/foo/bar/baz.jpg"
      assertTrue(Logo.from(validLogo).map(_.value).contains(validLogo))
    }
  )

  private val projectStatusTest = suite("ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Status.from(true) == Status.Active,
        Status.from(false) == Status.Inactive
      )
    },
    test("value should be the correct boolean") {
      assertTrue(Status.Active.value, !Status.Inactive.value)
    }
  )

  private val projectSelfJoinTest = suite("ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        SelfJoin.from(true) == SelfJoin.CanJoin,
        SelfJoin.from(false) == SelfJoin.CannotJoin
      )
    },
    test("value should be the correct boolean") {
      assertTrue(SelfJoin.CanJoin.value, !SelfJoin.CannotJoin.value)
    }
  )
}
