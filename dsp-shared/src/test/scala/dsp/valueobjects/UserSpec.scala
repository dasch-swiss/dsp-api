/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.User._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.User]] value objects creation.
 */
object UserSpec extends ZIOSpecDefault {
  private val validUsername                               = "user008"
  private val tooShortUsername                            = "123"
  private val tooLongUsername                             = "01234567890123456789012345678901234567890123456789011"
  private val invalidUsernameWithUnderscoreAsFirstChar    = "_123"
  private val invalidUsernameWithUnderscoreAsLastChar     = "123_"
  private val invalidUsernameWithMultipleUnderscoresInRow = "12__3"
  private val invalidUsernameWithDotAsFirstChar           = ".123"
  private val invalidUsernameWithDotAsLastChar            = "123."
  private val invalidUsernameWithMultipleDotsInRow        = "12..3"
  private val invalidUsername                             = "user!@#$%^&*()_+"
  private val validEmailAddress                           = "address@ch"
  private val invalidEmailAddress                         = "invalid_email_address"
  private val validPassword                               = "pass-word"
  private val validGivenName                              = "John"
  private val validFamilyName                             = "Rambo"
  private val validLanguageCode                           = "de"
  private val invalidLanguageCode                         = "00"

  def spec =
    (usernameTest + emailTest + givenNameTest + familyNameTest + passwordTest + passwordHashTest + languageCodeTest + systemAdminTest)

  private val usernameTest = suite("Username")(
    test("pass an empty value and return an error") {
      assertTrue(
        Username.make("") == Validation.fail(BadRequestException(UserErrorMessages.UsernameMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Username.make(invalidUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too short value and return an error") {
      assertTrue(
        Username.make(tooShortUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too long value and return an error") {
      assertTrue(
        Username.make(tooLongUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the first char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsFirstChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the last char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsLastChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' used multiple times in a row and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleUnderscoresInRow) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the first char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsFirstChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the last char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsLastChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' used multiple times in a row and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleDotsInRow) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Username.make(validUsername).toOption.get.value == validUsername)
    }
  )

  private val emailTest = suite("Email")(
    test("pass an empty value and return an error") {
      assertTrue(Email.make("") == Validation.fail(BadRequestException(UserErrorMessages.EmailMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Email.make(invalidEmailAddress) == Validation.fail(
          BadRequestException(UserErrorMessages.EmailInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Email.make(validEmailAddress).toOption.get.value == validEmailAddress)
    }
  )

  private val givenNameTest = suite("GivenName")(
    test("pass an empty value and return an error") {
      assertTrue(
        GivenName.make("") == Validation.fail(BadRequestException(UserErrorMessages.GivenNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.make(validGivenName).toOption.get.value == validGivenName)
    }
  )

  private val familyNameTest = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(
        FamilyName.make("") == Validation.fail(BadRequestException(UserErrorMessages.FamilyNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.make(validFamilyName).toOption.get.value == validFamilyName)
    }
  )

  private val passwordTest = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(
        Password.make("") == Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.make(validPassword).toOption.get.value == validPassword)
    }
  )

  private val passwordHashTest = suite("PasswordHash")(
    test("pass an empty value and return an error") {
      val passwordStrength = PasswordStrength.make(12).fold(e => throw e.head, v => v)
      assertTrue(
        PasswordHash.make("", passwordStrength) == Validation.fail(
          BadRequestException(UserErrorMessages.PasswordMissing)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      val passwordString   = "password1"
      val passwordStrength = PasswordStrength.make(12).fold(e => throw e.head, v => v)
      val password         = PasswordHash.make(passwordString, passwordStrength).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordString))
    },
    test("test if a password matches it hashed value") {
      val passwordString         = "password1"
      val passwordEqualString    = "password1"
      val passwordNotEqualString = "password2"

      val passwordStrength = PasswordStrength.make(12).fold(e => throw e.head, v => v)
      val password         = PasswordHash.make(passwordString, passwordStrength).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordEqualString)) &&
      assertTrue(!password.matches(passwordNotEqualString))
    }
  )

  private val languageCodeTest = suite("LanguageCode")(
    test("pass an empty value and return an error") {
      assertTrue(
        LanguageCode.make("") == Validation.fail(ValidationException(UserErrorMessages.LanguageCodeMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        LanguageCode.make(invalidLanguageCode) == Validation.fail(
          ValidationException(UserErrorMessages.LanguageCodeInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(LanguageCode.make(validLanguageCode).toOption.get.value == validLanguageCode)
    }
  )

  private val systemAdminTest = suite("SystemAdmin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(SystemAdmin.make(true).toOption.get.value == true)
    }
  )
}
