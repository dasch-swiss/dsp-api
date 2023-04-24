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
  private val validShortCode   = "1234"
  private val invalidShortCode = "12345"
  private val validShortName   = "validShortname"
  private val invalidShortname = "~!@#$%^&*()_+"
  private val validName        = "That is the project longname"
  private val validDescription = Seq(
    V2.StringLiteralV2(value = "Valid project description", language = Some("en"))
  )
  private val validKeywords = Seq("key", "word")
  private val validLogo     = "/fu/bar/baz.jpg"

  def spec = suite("ProjectSpec")(
    shortCodeTest,
    shortNameTest,
    nameTest,
    projectDescriptionsTest,
    keywordsTest,
    logoTest,
    projectStatusTest,
    projectSelfJoinTest
  )

  private val shortCodeTest = suite("ProjectSpec - ShortCode")(
    test("pass an empty value and return an error") {
      assertTrue(
        ShortCode.make("") == Validation.fail(ValidationException(ProjectErrorMessages.ShortCodeMissing))
      ) &&
      assertTrue(
        ShortCode.make(Some("")) == Validation.fail(ValidationException(ProjectErrorMessages.ShortCodeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ShortCode.make(invalidShortCode) == Validation.fail(
          ValidationException(ProjectErrorMessages.ShortCodeInvalid(invalidShortCode))
        )
      ) &&
      assertTrue(
        ShortCode.make(Some(invalidShortCode)) == Validation.fail(
          ValidationException(ProjectErrorMessages.ShortCodeInvalid(invalidShortCode))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      for {
        shortCode           <- ShortCode.make(validShortCode).toZIO
        optionalShortCode   <- ShortCode.make(Option(validShortCode)).toZIO
        shortCodeFromOption <- ZIO.fromOption(optionalShortCode)
      } yield assertTrue(shortCode.value == validShortCode) &&
        assert(optionalShortCode)(isSome(isSubtype[ShortCode](Assertion.anything))) &&
        assertTrue(shortCodeFromOption.value == validShortCode)
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
        ShortName.make("") == Validation.fail(ValidationException(ProjectErrorMessages.ShortNameMissing))
      ) &&
      assertTrue(
        ShortName.make(Some("")) == Validation.fail(ValidationException(ProjectErrorMessages.ShortNameMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ShortName.make(invalidShortname) == Validation.fail(
          ValidationException(ProjectErrorMessages.ShortNameInvalid(invalidShortname))
        )
      ) &&
      assertTrue(
        ShortName.make(Some(invalidShortname)) == Validation.fail(
          ValidationException(ProjectErrorMessages.ShortNameInvalid(invalidShortname))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      for {
        shortName           <- ShortName.make(validShortName).toZIO
        optionalShortName   <- ShortName.make(Option(validShortName)).toZIO
        shortNameFromOption <- ZIO.fromOption(optionalShortName)
      } yield assertTrue(shortName.value == validShortName) &&
        assert(optionalShortName)(isSome(isSubtype[ShortName](Assertion.anything))) &&
        assertTrue(shortNameFromOption.value == validShortName)
    },
    test("successfully validate passing None") {
      assertTrue(
        ShortName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val nameTest = suite("ProjectSpec - Name")(
    test("pass an empty value and return an error") {
      assertTrue(Name.make("") == Validation.fail(ValidationException(ProjectErrorMessages.NameMissing))) &&
      assertTrue(
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
          ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing)
        )
      ) &&
      assertTrue(
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
        Keywords.make(Seq.empty) == Validation.fail(
          ValidationException(ProjectErrorMessages.KeywordsMissing)
        )
      ) &&
      assertTrue(
        Keywords.make(Some(Seq.empty)) == Validation.fail(
          ValidationException(ProjectErrorMessages.KeywordsMissing)
        )
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
        Logo.make("") == Validation.fail(
          ValidationException(ProjectErrorMessages.LogoMissing)
        )
      ) &&
      assertTrue(
        Logo.make(Some("")) == Validation.fail(
          ValidationException(ProjectErrorMessages.LogoMissing)
        )
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
      } yield assertTrue(status.value == true) &&
        assert(optionalStatus)(isSome(isSubtype[ProjectStatus](Assertion.anything))) &&
        assertTrue(statusFromOption.value == false)
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
      } yield assertTrue(selfJoin.value == true) &&
        assert(optionalSelfJoin)(isSome(isSubtype[ProjectSelfJoin](Assertion.anything))) &&
        assertTrue(selfJoinFromOption.value == false)
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectSelfJoin.make(None) == Validation.succeed(None)
      )
    }
  )
}
