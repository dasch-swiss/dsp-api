/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

import dsp.valueobjects.IriErrorMessages

object UserSpec extends ZIOSpecDefault {
  private val validUserIri                                = "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ"
  private val userIriWithUUIDVersion3                     = "http://rdfh.ch/users/cCmdcpn2MO211YYOplR1hQ"
  private val invalidIri                                  = "Invalid IRI"
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
  private val pwStrength                                  = PasswordStrength.unsafeMake(12)

  private val usernameTest = suite("Username")(
    test("pass an empty value and return an error") {
      assertTrue(
        Username.from("") == Left(UserErrorMessages.UsernameMissing)
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Username.from(invalidUsername) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass too short value and return an error") {
      assertTrue(
        Username.from(tooShortUsername) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass too long value and return an error") {
      assertTrue(
        Username.from(tooLongUsername) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass an invalid value with '_' as the first char and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithUnderscoreAsFirstChar) == Left(UserErrorMessages.UsernameInvalid)
      )
    },
    test("pass an invalid value with '_' as the last char and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithUnderscoreAsLastChar) == Left(UserErrorMessages.UsernameInvalid)
      )
    },
    test("pass an invalid value with '_' used multiple times in a row and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithMultipleUnderscoresInRow) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass an invalid value with '.' as the first char and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithDotAsFirstChar) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass an invalid value with '.' as the last char and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithDotAsLastChar) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass an invalid value with '.' used multiple times in a row and return an error") {
      assertTrue(
        Username.from(invalidUsernameWithMultipleDotsInRow) == Left((UserErrorMessages.UsernameInvalid))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Username.from(validUsername).contains(validUsername))
    }
  )

  private val emailTest = suite("Email")(
    test("pass an empty value and return an error") {
      assertTrue(Email.from("") == Left((UserErrorMessages.EmailMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Email.from(invalidEmailAddress) == Left((UserErrorMessages.EmailInvalid))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Email.from(validEmailAddress).contains(validEmailAddress))
    }
  )

  private val givenNameTest = suite("GivenName")(
    test("pass an empty value and return an error") {
      assertTrue(
        GivenName.from("") == Left((UserErrorMessages.GivenNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.from(validGivenName).contains(validGivenName))
    }
  )

  private val familyNameTest = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(
        FamilyName.from("") == Left((UserErrorMessages.FamilyNameMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.from(validFamilyName).contains(validFamilyName))
    }
  )

  private val passwordTest = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(
        Password.from("") == Left((UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.from(validPassword).contains(validPassword))
    }
  )

  private val passwordHashTest = suite("PasswordHash")(
    test("pass an empty value and return an error") {
      assertTrue(
        PasswordHash.from("", pwStrength) == Left((UserErrorMessages.PasswordMissing))
      )
    },
    test("pass a valid value and successfully create value object") {
      val passwordString = "password1"
      val password       = PasswordHash.unsafeFrom("password1", pwStrength)

      assertTrue(password.matches(passwordString))
    },
    test("test if a password matches its hashed value") {
      val passwordString         = "password1"
      val passwordEqualString    = "password1"
      val passwordNotEqualString = "password2"

      val password = PasswordHash.unsafeFrom(passwordString, pwStrength)

      assertTrue(password.matches(passwordEqualString), !password.matches(passwordNotEqualString))
    },
    test("pass an invalid password strength value and return an error") {
      assertTrue(
        PasswordStrength.from(-1) == Left(("PasswordStrength is invalid."))
      )
    },
    test("pass a valid password strength value and create value object") {
      assertTrue(
        PasswordStrength.from(12) == Right(pwStrength)
      )
    }
  )

  private val systemAdminTest = suite("SystemAdmin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(SystemAdmin.from(true).value)
    }
  )

  private val userSuite = suite("User")()

  private val usernameSuite = suite("Username")(
    test("Username may contain alphanumeric characters, underscore and dot") {
      assertTrue(Username.from("a_2.3").isRight)
    },
    test("Username has to be at least 4 characters long") {
      assertTrue(Username.from("abc").isLeft)
    },
    test("Username has to be at most 50 characters long") {
      assertTrue(Username.from("123456789012345678901234567890123456789012345678901").isLeft)
    },
    test("Username must not contain other characters") {
      assertTrue(
        Username.from("a_2.3!").isLeft,
        Username.from("a_2-3").isLeft,
        Username.from("a.b@example.com").isLeft
      )
    },
    test("Username must not start with a dot") {
      assertTrue(Username.from(".abc").isLeft)
    },
    test("Username must not end with a dot") {
      assertTrue(Username.from("abc.").isLeft)
    },
    test("Username must not contain two dots in a row") {
      assertTrue(Username.from("a..bc").isLeft)
    },
    test("Username must not start with an underscore") {
      assertTrue(Username.from("_abc").isLeft)
    },
    test("Username must not end with an underscore") {
      assertTrue(Username.from("abc_").isLeft)
    },
    test("Username must not contain two underscores in a row") {
      assertTrue(Username.from("a__bc").isLeft)
    }
  )

  private val emailSuite = suite("Email")(
    test("Email must be a correct email address") {
      assertTrue(Email.from("j.doe@example.com").isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.from("").isLeft)
    },
    test("Email must not be a username") {
      assertTrue(Email.from("j.doe").isLeft)
    }
  )

  private val iriSuite = suite("UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.from("") == Left((UserErrorMessages.UserIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        UserIri.from(invalidIri) == Left((UserErrorMessages.UserIriInvalid(invalidIri)))
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        UserIri.from(userIriWithUUIDVersion3) == Left((IriErrorMessages.UuidVersionInvalid))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(UserIri.from(validUserIri).contains(validUserIri))
    }
  )

  private val oldTests = suite("ordTests")(
    usernameTest,
    emailTest,
    givenNameTest,
    familyNameTest,
    passwordTest,
    passwordHashTest,
    systemAdminTest
  )

  val spec: Spec[Any, Any] = suite("UserSpec")(
    userSuite,
    usernameSuite,
    emailSuite,
    iriSuite,
    oldTests // TODO: get rid of those tests
  )
}
