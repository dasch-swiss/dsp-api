/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.User._
import zio.prelude.Validation
import zio.test._
import dsp.errors.BadRequestException

/**
 * This spec is used to test the [[User]] value objects creation.
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
    test("pass an empty value and throw an error") {
      assertTrue(
        Username.make("") == Validation.fail(BadRequestException(UserErrorMessages.UsernameMissing))
      ) &&
      assertTrue(
        Username.make(Some("")) == Validation.fail(BadRequestException(UserErrorMessages.UsernameMissing))
      )
    },
    test("pass an invalid value and throw an error") {
      assertTrue(
        Username.make(invalidUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsername)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too short value and throw an error") {
      assertTrue(
        Username.make(tooShortUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(tooShortUsername)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too long value and throw an error") {
      assertTrue(
        Username.make(tooLongUsername) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(tooLongUsername)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the first char and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsFirstChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithUnderscoreAsFirstChar)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the last char and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsLastChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithUnderscoreAsLastChar)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' used multiple times in a row and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleUnderscoresInRow) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithMultipleUnderscoresInRow)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the first char and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsFirstChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithDotAsFirstChar)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the last char and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsLastChar) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithDotAsLastChar)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' used multiple times in a row and throw an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleDotsInRow) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      ) &&
      assertTrue(
        Username.make(Some(invalidUsernameWithMultipleDotsInRow)) == Validation.fail(
          BadRequestException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Username.make(validUsername).toOption.get.value == validUsername)
      assertTrue(Username.make(Option(validUsername)).getOrElse(null).get.value == validUsername)
    } +
      test("successfully validate passing None") {
        assertTrue(
          Username.make(None) == Validation.succeed(None)
        )
      }
  )

  private val emailTest = suite("Email")(
    test("pass an empty value and throw an error") {
      assertTrue(Email.make("") == Validation.fail(BadRequestException(UserErrorMessages.EmailMissing))) &&
      assertTrue(
        Email.make(Some("")) == Validation.fail(BadRequestException(UserErrorMessages.EmailMissing))
      )
    },
    test("pass an invalid value and throw an error") {
      assertTrue(
        Email.make(invalidEmailAddress) == Validation.fail(
          BadRequestException(UserErrorMessages.EmailInvalid)
        )
      ) &&
      assertTrue(
        Email.make(Some(invalidEmailAddress)) == Validation.fail(
          BadRequestException(UserErrorMessages.EmailInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Email.make(validEmailAddress).toOption.get.value == validEmailAddress) &&
      assertTrue(Email.make(Option(validEmailAddress)).getOrElse(null).get.value == validEmailAddress)
    },
    test("successfully validate passing None") {
      assertTrue(
        Email.make(None) == Validation.succeed(None)
      )
    }
  )

  private val givenNameTest = suite("GivenName")(
    test("pass an empty value and throw an error") {
      assertTrue(
        GivenName.make("") == Validation.fail(BadRequestException(UserErrorMessages.GivenNameMissing))
      ) &&
      assertTrue(
        GivenName.make(Some("")) == Validation.fail(BadRequestException(UserErrorMessages.GivenNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.make(validGivenName).toOption.get.value == validGivenName) &&
      assertTrue(GivenName.make(Option(validGivenName)).getOrElse(null).get.value == validGivenName)
    },
    test("successfully validate passing None") {
      assertTrue(
        GivenName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val familyNameTest = suite("FamilyName")(
    test("pass an empty value and throw an error") {
      assertTrue(
        FamilyName.make("") == Validation.fail(BadRequestException(UserErrorMessages.FamilyNameMissing))
      ) &&
      assertTrue(
        FamilyName.make(Some("")) == Validation.fail(
          BadRequestException(UserErrorMessages.FamilyNameMissing)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.make(validFamilyName).toOption.get.value == validFamilyName) &&
      assertTrue(FamilyName.make(Option(validFamilyName)).getOrElse(null).get.value == validFamilyName)
    },
    test("successfully validate passing None") {
      assertTrue(
        FamilyName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val passwordTest = suite("Password")(
    test("pass an empty value and throw an error") {
      assertTrue(
        Password.make("") == Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      ) &&
      assertTrue(
        Password.make(Some("")) == Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.make(validPassword).toOption.get.value == validPassword) &&
      assertTrue(Password.make(Option(validPassword)).getOrElse(null).get.value == validPassword)
    },
    test("successfully validate passing None") {
      assertTrue(
        Password.make(None) == Validation.succeed(None)
      )
    }
  )

  private val passwordHashTest = suite("PasswordHash")(
    test("pass an empty value and throw an error") {
      assertTrue(
        PasswordHash.make("") == Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      val passwordString = "password1"
      val password       = PasswordHash.make(passwordString).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordString))
    },
    test("test if a password matches it hashed value") {
      val passwordString         = "password1"
      val passwordEqualString    = "password1"
      val passwordNotEqualString = "password2"

      val password = PasswordHash.make(passwordString).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordEqualString)) &&
      assertTrue(!password.matches(passwordNotEqualString))
    }
  )

  private val languageCodeTest = suite("LanguageCode")(
    test("pass an empty value and throw an error") {
      assertTrue(
        LanguageCode.make("") == Validation.fail(BadRequestException(UserErrorMessages.LanguageCodeMissing))
      ) &&
      assertTrue(
        LanguageCode.make(Some("")) == Validation.fail(
          BadRequestException(UserErrorMessages.LanguageCodeMissing)
        )
      )
    },
    test("pass an invalid value and throw an error") {
      assertTrue(
        LanguageCode.make(invalidLanguageCode) == Validation.fail(
          BadRequestException(UserErrorMessages.LanguageCodeInvalid)
        )
      ) &&
      assertTrue(
        LanguageCode.make(Some(invalidLanguageCode)) == Validation.fail(
          BadRequestException(UserErrorMessages.LanguageCodeInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(LanguageCode.make(validLanguageCode).toOption.get.value == validLanguageCode) &&
      assertTrue(LanguageCode.make(Option(validLanguageCode)).getOrElse(null).get.value == validLanguageCode)
    },
    test("successfully validate passing None") {
      assertTrue(
        LanguageCode.make(None) == Validation.succeed(None)
      )
    }
  )

  private val systemAdminTest = suite("SystemAdmin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(SystemAdmin.make(true).toOption.get.value == true)
    }
  )
}
