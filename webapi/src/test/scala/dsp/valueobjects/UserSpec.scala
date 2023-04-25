/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.json._
import zio.prelude.Validation
import zio.test._

import dsp.errors.ValidationException
import dsp.valueobjects.User._

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
  private val validPasswordHash                           = PasswordHash.make("test", PasswordStrength(12)).fold(e => throw e.head, v => v)

  def spec =
    (usernameTest + emailTest + givenNameTest + familyNameTest + passwordTest + passwordHashTest + systemAdminTest)

  private val usernameTest = suite("Username")(
    test("pass an empty value and return an error") {
      assertTrue(
        Username.make("") == Validation.fail(ValidationException(UserErrorMessages.UsernameMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Username.make(invalidUsername) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too short value and return an error") {
      assertTrue(
        Username.make(tooShortUsername) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass too long value and return an error") {
      assertTrue(
        Username.make(tooLongUsername) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the first char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsFirstChar) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' as the last char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithUnderscoreAsLastChar) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '_' used multiple times in a row and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleUnderscoresInRow) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the first char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsFirstChar) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' as the last char and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithDotAsLastChar) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass an invalid value with '.' used multiple times in a row and return an error") {
      assertTrue(
        Username.make(invalidUsernameWithMultipleDotsInRow) == Validation.fail(
          ValidationException(UserErrorMessages.UsernameInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Username.make(validUsername).toOption.get.value == validUsername)
    }
  )

  private val emailTest = suite("Email")(
    test("pass an empty value and return an error") {
      assertTrue(Email.make("") == Validation.fail(ValidationException(UserErrorMessages.EmailMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Email.make(invalidEmailAddress) == Validation.fail(
          ValidationException(UserErrorMessages.EmailInvalid)
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
        GivenName.make("") == Validation.fail(ValidationException(UserErrorMessages.GivenNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.make(validGivenName).toOption.get.value == validGivenName)
    }
  )

  private val familyNameTest = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(
        FamilyName.make("") == Validation.fail(ValidationException(UserErrorMessages.FamilyNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.make(validFamilyName).toOption.get.value == validFamilyName)
    }
  )

  private val passwordTest = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(
        Password.make("") == Validation.fail(ValidationException(UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.make(validPassword).toOption.get.value == validPassword)
    }
  )

  private val passwordHashTest = suite("PasswordHash")(
    test("pass an empty value and return an error") {
      assertTrue(
        PasswordHash.make("", PasswordStrength(12)) == Validation.fail(
          ValidationException(UserErrorMessages.PasswordMissing)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      val passwordString = "password1"
      val password       = PasswordHash.make("password1", PasswordStrength(12)).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordString))
    },
    test("test if a password matches its hashed value") {
      val passwordString         = "password1"
      val passwordEqualString    = "password1"
      val passwordNotEqualString = "password2"

      val password = PasswordHash.make(passwordString, PasswordStrength(12)).fold(e => throw e.head, v => v)

      assertTrue(password.matches(passwordEqualString)) &&
      assertTrue(!password.matches(passwordNotEqualString))
    },
    test("pass an invalid password strength value and return an error") {
      assertTrue(
        PasswordStrength.make(-1) == Validation.fail("-1 did not satisfy greaterThanOrEqualTo(4)")
      )
    },
    test("pass a valid password strength value and create value object") {
      assertTrue(
        PasswordStrength.make(12) == Validation.succeed(PasswordStrength(12))
      )
    },
    test("decode a PasswordHash from JSON") {
      val passwordHashFromJson =
        """[ "$2a$12$DulNTvjUALMJufhJ.FR37uqXOCeXp7HFHWzwcjodLlmhUSMe2XKT", 12 ]""".fromJson[PasswordHash]
      val result = passwordHashFromJson match {
        case Right(passwordHash) => passwordHash.value.startsWith("$2a")
        case Left(_)             => false
      }
      assertTrue(result)
    },
    test("encode a PasswordHash into JSON") {
      val passwordHash     = PasswordHash.make("test", PasswordStrength(12)).getOrElse(validPasswordHash)
      val passwordHashJson = passwordHash.toJson
      assertTrue(passwordHashJson.startsWith(""""$2a"""))
    }
  )

  private val systemAdminTest = suite("SystemAdmin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(SystemAdmin.make(true).toOption.get.value == true)
    }
  )
}
