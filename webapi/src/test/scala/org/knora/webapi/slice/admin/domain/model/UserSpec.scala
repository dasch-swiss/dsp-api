/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

import dsp.valueobjects.IriErrorMessages

object UserSpec extends ZIOSpecDefault {
  private val validUserIri            = "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ"
  private val userIriWithUUIDVersion3 = "http://rdfh.ch/users/cCmdcpn2MO211YYOplR1hQ"
  private val invalidIri              = "Invalid IRI"
  private val validPassword           = "pass-word"
  private val validGivenName          = "John"
  private val validFamilyName         = "Rambo"
  private val pwStrength              = PasswordStrength.unsafeMake(12)

  private val userSuite = suite("User")()

  private val usernameSuite = suite("Username")(
    test("Username must not be empty") {
      assertTrue(Username.from("") == Left(UserErrorMessages.UsernameMissing))
    },
    test("Username may contain alphanumeric characters, underscore and dot") {
      assertTrue(Username.from("a_2.3").isRight)
    },
    test("Username has to be at least 4 characters long") {
      assertTrue(Username.from("abc") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username has to be at most 50 characters long") {
      assertTrue(
        Username
          .from("123456789012345678901234567890123456789012345678901")
          .left
          .exists(_.equals(UserErrorMessages.UsernameInvalid))
      )
    },
    test("Username must not contain other characters") {
      assertTrue(
        Username.from("a_2.3!") == Left(UserErrorMessages.UsernameInvalid),
        Username.from("a_2-3") == Left(UserErrorMessages.UsernameInvalid),
        Username.from("a.b@example.com") == Left(UserErrorMessages.UsernameInvalid)
      )
    },
    test("Username must not start with a dot") {
      assertTrue(Username.from(".abc") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username must not end with a dot") {
      assertTrue(Username.from("abc.") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username must not contain two dots in a row") {
      assertTrue(Username.from("a..bc") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username must not start with an underscore") {
      assertTrue(Username.from("_abc") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username must not end with an underscore") {
      assertTrue(Username.from("abc_") == Left(UserErrorMessages.UsernameInvalid))
    },
    test("Username must not contain two underscores in a row") {
      assertTrue(Username.from("a__bc") == Left(UserErrorMessages.UsernameInvalid))
    }
  )

  private val emailSuite = suite("Email")(
    test("Email must be a correct email address") {
      assertTrue(Email.from("j.doe@example.com").isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.from("") == Left(UserErrorMessages.EmailMissing))
    },
    test("Email must not be a username") {
      assertTrue(Email.from("j.doe") == Left(UserErrorMessages.EmailInvalid))
    }
  )

  private val iriSuite = suite("UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.from("") == Left(UserErrorMessages.UserIriMissing))
    },
    test("pass an invalid value and return an error") {
      assertTrue(UserIri.from(invalidIri) == Left(UserErrorMessages.UserIriInvalid(invalidIri)))
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(UserIri.from(userIriWithUUIDVersion3) == Left(IriErrorMessages.UuidVersionInvalid))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(UserIri.from(validUserIri).isRight)
    }
  )

  private val givenNameSuite = suite("GivenName")(
    test("pass an empty value and return an error") {
      assertTrue(GivenName.from("") == Left(UserErrorMessages.GivenNameMissing))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.from(validGivenName).isRight)
    }
  )

  private val familyNameSuite = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(FamilyName.from("") == Left(UserErrorMessages.FamilyNameMissing))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.from(validFamilyName).isRight)
    }
  )

  private val passwordSuite = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(Password.from("") == Left(UserErrorMessages.PasswordMissing))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.from(validPassword).isRight)
    }
  )

  private val passwordHashSuite = suite("PasswordHash")(
    test("pass an empty value and return an error") {
      assertTrue(PasswordHash.from("", pwStrength) == Left(UserErrorMessages.PasswordMissing))
    },
    test("pass a valid value and successfully create value object") {
      val passwordString = "password1"
      val password       = PasswordHash.from(passwordString, pwStrength)
      assertTrue(password.exists(_.matches(passwordString)))
    },
    test("test if a password matches its hashed value") {
      val passwordString         = "password1"
      val passwordEqualString    = "password1"
      val passwordNotEqualString = "password2"

      val password = PasswordHash.from(passwordString, pwStrength)

      assertTrue(
        password.exists(_.matches(passwordEqualString)),
        password.exists(!_.matches(passwordNotEqualString))
      )
    },
    test("pass an invalid password strength value and return an error") {
      assertTrue(PasswordStrength.from(-1) == Left(UserErrorMessages.PasswordStrengthInvalid))
    },
    test("pass a valid password strength value and create value object") {
      assertTrue(PasswordStrength.from(12).isRight)
    }
  )

  val spec: Spec[Any, Any] = suite("UserSpec")(
    userSuite,
    usernameSuite,
    emailSuite,
    iriSuite,
    givenNameSuite,
    familyNameSuite,
    passwordSuite,
    passwordHashSuite
  )
}
