/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.Username.Username

object UserSpec extends ZIOSpecDefault {

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
      assertTrue(Email.Email.from("j.doe@example.com").isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.Email.from("").isLeft)
    },
    test("Email must not be a username") {
      assertTrue(Email.Email.from("j.doe").isLeft)
    }
  )

  val spec: Spec[Any, Nothing] = suite("UserSpec")(
    userSuite,
    usernameSuite,
    emailSuite
  )
}
