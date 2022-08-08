/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.Project._
import zio.prelude.Validation
import zio.test._
import dsp.errors.BadRequestException

/**
 * This spec is used to test the [[Project]] value objects creation.
 */
object ProjectSpec extends ZIOSpecDefault {
  private val validShortcode   = "1234"
  private val invalidShortcode = "12345"
  private val validShortName   = "validShortname"
  private val invalidShortname = "~!@#$%^&*()_+"
  private val validName        = "That is the project longname"
  private val validDescription = Seq(
    V2.StringLiteralV2(value = "Valid project description", language = Some("en"))
  )
  private val validKeywords = Seq("key", "word")
  private val validLogo     = "/fu/bar/baz.jpg"

  def spec =
    (shortcodeTest + shortNameTest + nameTest + projectDescriptionsTest + keywordsTest + logoTest + projectStatusTest + projectSelfJoinTest)

  private val shortcodeTest = suite("ProjectSpec - Shortcode")(
    test("pass an empty value and return an error") {
      assertTrue(
        ShortCode.make("") == Validation.fail(BadRequestException(ProjectErrorMessages.ShortcodeMissing))
      ) &&
      assertTrue(
        ShortCode.make(Some("")) == Validation.fail(BadRequestException(ProjectErrorMessages.ShortcodeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ShortCode.make(invalidShortcode) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ShortcodeInvalid)
        )
      ) &&
      assertTrue(
        ShortCode.make(Some(invalidShortcode)) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ShortcodeInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ShortCode.make(validShortcode).toOption.get.value == validShortcode) &&
      assertTrue(ShortCode.make(Option(validShortcode)).getOrElse(null).get.value == validShortcode)
    },
    test("successfully validate passing None") {
      assertTrue(
        ShortCode.make(None) == Validation.succeed(None)
      )
    }
  )

  private val shortNameTest = suite("ProjectSpec - ShortName")(
    test("pass an empty value and return an error") {
      assertTrue(
        ShortName.make("") == Validation.fail(BadRequestException(ProjectErrorMessages.ShortNameMissing))
      ) &&
      assertTrue(
        ShortName.make(Some("")) == Validation.fail(BadRequestException(ProjectErrorMessages.ShortNameMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ShortName.make(invalidShortname) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ShortNameInvalid)
        )
      ) &&
      assertTrue(
        ShortName.make(Some(invalidShortname)) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ShortNameInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ShortName.make(validShortName).toOption.get.value == validShortName) &&
      assertTrue(ShortName.make(Option(validShortName)).getOrElse(null).get.value == validShortName)
    },
    test("successfully validate passing None") {
      assertTrue(
        ShortName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val nameTest = suite("ProjectSpec - Name")(
    test("pass an empty value and return an error") {
      assertTrue(Name.make("") == Validation.fail(BadRequestException(ProjectErrorMessages.NameMissing))) &&
      assertTrue(
        Name.make(Some("")) == Validation.fail(BadRequestException(ProjectErrorMessages.NameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Name.make(validName).toOption.get.value == validName) &&
      assertTrue(Name.make(Option(validName)).getOrElse(null).get.value == validName)
    },
    test("successfully validate passing None") {
      assertTrue(
        ShortCode.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectDescriptionsTest = suite("ProjectSpec - ProjectDescriptions")(
    test("pass an empty object and return an error") {
      assertTrue(
        ProjectDescription.make(Seq.empty) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ProjectDescriptionsMissing)
        )
      ) &&
      assertTrue(
        ProjectDescription.make(Some(Seq.empty)) == Validation.fail(
          BadRequestException(ProjectErrorMessages.ProjectDescriptionsMissing)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(ProjectDescription.make(validDescription).toOption.get.value == validDescription) &&
      assertTrue(ProjectDescription.make(Option(validDescription)).getOrElse(null).get.value == validDescription)
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
        Keywords.make(Seq.empty) == Validation.fail(
          BadRequestException(ProjectErrorMessages.KeywordsMissing)
        )
      ) &&
      assertTrue(
        Keywords.make(Some(Seq.empty)) == Validation.fail(
          BadRequestException(ProjectErrorMessages.KeywordsMissing)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(Keywords.make(validKeywords).toOption.get.value == validKeywords) &&
      assertTrue(Keywords.make(Option(validKeywords)).getOrElse(null).get.value == validKeywords)
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
        Logo.make("") == Validation.fail(
          BadRequestException(ProjectErrorMessages.LogoMissing)
        )
      ) &&
      assertTrue(
        Logo.make(Some("")) == Validation.fail(
          BadRequestException(ProjectErrorMessages.LogoMissing)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(Logo.make(validLogo).toOption.get.value == validLogo) &&
      assertTrue(Logo.make(Option(validLogo)).getOrElse(null).get.value == validLogo)
    },
    test("successfully validate passing None") {
      assertTrue(
        Keywords.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectStatusTest = suite("ProjectSpec - ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(ProjectStatus.make(true).toOption.get.value == true) &&
      assertTrue(ProjectStatus.make(Some(false)).getOrElse(null).get.value == false)
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectStatus.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectSelfJoinTest = suite("ProjectSpec - ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(ProjectSelfJoin.make(true).toOption.get.value == true) &&
      assertTrue(ProjectSelfJoin.make(Some(false)).getOrElse(null).get.value == false)
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectSelfJoin.make(None) == Validation.succeed(None)
      )
    }
  )
}
