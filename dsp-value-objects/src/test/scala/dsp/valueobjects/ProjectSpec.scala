/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.Project._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[Project]] value objects creation.
 */
object ProjectSpec extends ZIOSpecDefault {
  private val validShortcode   = "1234"
  private val invalidShortcode = "12345"
  private val validShortname   = "validShortname"
  private val invalidShortname = "~!@#$%^&*()_+"
  private val validLongname    = "That is the project longname"
  private val validDescription = Seq(
    V2.StringLiteralV2(value = "Valid project description", language = Some("en"))
  )
  private val validKeywords = Seq("key", "word")
  private val validLogo     = "/fu/bar/baz.jpg"

  def spec =
    (shortcodeTest + shortnameTest + longnameTest + projectDescriptionsTest + keywordsTest + logoTest + projectStatusTest + projectSelfJoinTest)

  private val shortcodeTest = suite("ProjectSpec - Shortcode")(
    test("pass an empty value and throw an error") {
      assertTrue(Shortcode.make("") == Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortcodeMissing)))
      assertTrue(
        Shortcode.make(Some("")) == Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortcodeMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          Shortcode.make(invalidShortcode) == Validation.fail(
            V2.BadRequestException(ProjectErrorMessages.ShortcodeInvalid)
          )
        )
        assertTrue(
          Shortcode.make(Some(invalidShortcode)) == Validation.fail(
            V2.BadRequestException(ProjectErrorMessages.ShortcodeInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(Shortcode.make(validShortcode).toOption.get.value == validShortcode)
        assertTrue(Shortcode.make(Option(validShortcode)).getOrElse(null).get.value == validShortcode)
      } +
      test("pass None") {
        assertTrue(
          Shortcode.make(None) == Validation.succeed(None)
        )
      }
  )

  private val shortnameTest = suite("ProjectSpec - Shortname")(
    test("pass an empty value and throw an error") {
      assertTrue(Shortname.make("") == Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortnameMissing)))
      assertTrue(
        Shortname.make(Some("")) == Validation.fail(V2.BadRequestException(ProjectErrorMessages.ShortnameMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          Shortname.make(invalidShortname) == Validation.fail(
            V2.BadRequestException(ProjectErrorMessages.ShortnameInvalid)
          )
        )
        assertTrue(
          Shortname.make(Some(invalidShortname)) == Validation.fail(
            V2.BadRequestException(ProjectErrorMessages.ShortnameInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(Shortname.make(validShortname).toOption.get.value == validShortname)
        assertTrue(Shortname.make(Option(validShortname)).getOrElse(null).get.value == validShortname)
      } +
      test("pass None") {
        assertTrue(
          Shortcode.make(None) == Validation.succeed(None)
        )
      }
  )

  private val longnameTest = suite("ProjectSpec - Longname")(
    test("pass an empty value and throw an error") {
      assertTrue(Longname.make("") == Validation.fail(V2.BadRequestException(ProjectErrorMessages.LongnameMissing)))
      assertTrue(
        Longname.make(Some("")) == Validation.fail(V2.BadRequestException(ProjectErrorMessages.LongnameMissing))
      )
    } +
      test("pass a valid value and successfully create value object") {
        assertTrue(Longname.make(validLongname).toOption.get.value == validLongname)
        assertTrue(Longname.make(Option(validLongname)).getOrElse(null).get.value == validLongname)
      } +
      test("pass None") {
        assertTrue(
          Shortcode.make(None) == Validation.succeed(None)
        )
      }
  )

  private val projectDescriptionsTest = suite("ProjectSpec - ProjectDescriptions")(
    test("pass an empty object and throw an error") {
      assertTrue(
        ProjectDescription.make(Seq.empty) == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.ProjectDescriptionMissing)
        )
      )
      assertTrue(
        ProjectDescription.make(Some(Seq.empty)) == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.ProjectDescriptionMissing)
        )
      )
    } +
      test("pass a valid object and successfully create value object") {
        assertTrue(ProjectDescription.make(validDescription).toOption.get.value == validDescription)
        assertTrue(ProjectDescription.make(Option(validDescription)).getOrElse(null).get.value == validDescription)
      } +
      test("pass None") {
        assertTrue(
          ProjectDescription.make(None) == Validation.succeed(None)
        )
      }
  )

  private val keywordsTest = suite("ProjectSpec - Keywords")(
    test("pass an empty object and throw an error") {
      assertTrue(
        Keywords.make(Seq.empty) == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.KeywordsMissing)
        )
      )
      assertTrue(
        Keywords.make(Some(Seq.empty)) == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.KeywordsMissing)
        )
      )
    } +
      test("pass a valid object and successfully create value object") {
        assertTrue(Keywords.make(validKeywords).toOption.get.value == validKeywords)
        assertTrue(Keywords.make(Option(validKeywords)).getOrElse(null).get.value == validKeywords)
      } +
      test("pass None") {
        assertTrue(
          Keywords.make(None) == Validation.succeed(None)
        )
      }
  )

  private val logoTest = suite("ProjectSpec - Logo")(
    test("pass an empty object and throw an error") {
      assertTrue(
        Logo.make("") == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.LogoMissing)
        )
      )
      assertTrue(
        Logo.make(Some("")) == Validation.fail(
          V2.BadRequestException(ProjectErrorMessages.LogoMissing)
        )
      )
    } +
      test("pass a valid object and successfully create value object") {
        assertTrue(Logo.make(validLogo).toOption.get.value == validLogo)
        assertTrue(Logo.make(Option(validLogo)).getOrElse(null).get.value == validLogo)
      } +
      test("pass None") {
        assertTrue(
          Keywords.make(None) == Validation.succeed(None)
        )
      }
  )

  private val projectStatusTest = suite("ProjectSpec - ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(ProjectStatus.make(true).toOption.get.value == true)
      assertTrue(ProjectStatus.make(Some(false)).getOrElse(null).get.value == false)
    } +
      test("pass None") {
        assertTrue(
          ProjectStatus.make(None) == Validation.succeed(None)
        )
      }
  )

  private val projectSelfJoinTest = suite("ProjectSpec - ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(ProjectSelfJoin.make(true).toOption.get.value == true)
      assertTrue(ProjectSelfJoin.make(Some(false)).getOrElse(null).get.value == false)
    } +
      test("pass None") {
        assertTrue(
          ProjectSelfJoin.make(None) == Validation.succeed(None)
        )
      }
  )
}
