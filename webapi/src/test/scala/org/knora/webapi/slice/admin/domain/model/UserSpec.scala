/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object UserSpec extends ZIOSpecDefault {
  private val validPassword   = "pass-word"
  private val validGivenName  = "John"
  private val validFamilyName = "Rambo"

  private val emailSuite = suite("Email")(
    test("Email must be a correct email address") {
      assertTrue(Email.from("j.doe@example.com").isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.from("") == Left("Email cannot be empty."))
    },
    test("Email must not be a username") {
      assertTrue(Email.from("j.doe") == Left("Email is invalid."))
    },
  )

  private val givenNameSuite = suite("GivenName")(
    test("pass an empty value and return an error") {
      assertTrue(GivenName.from("") == Left("GivenName cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.from(validGivenName).isRight)
    },
  )

  private val familyNameSuite = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(FamilyName.from("") == Left("FamilyName cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.from(validFamilyName).isRight)
    },
  )

  private val passwordSuite = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(Password.from("") == Left("Password cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.from(validPassword).isRight)
    },
  )

  private val passwordHashSuite = suite("PasswordHash")(
    test("pass an empty value and return an error") {
      assertTrue(PasswordHash.from("") == Left("Password cannot be empty."))
    },
    test("pass an invalid password strength value and return an error") {
      assertTrue(PasswordStrength.from(-1) == Left("PasswordStrength is invalid."))
    },
    test("pass a valid password strength value and create value object") {
      assertTrue(PasswordStrength.from(12).isRight)
    },
  )

  val spec: Spec[Any, Any] = suite("UserSpec")(
    emailSuite,
    givenNameSuite,
    familyNameSuite,
    passwordSuite,
    passwordHashSuite,
  )
}
