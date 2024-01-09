/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.prelude.Validation
import zio.test.*

import dsp.errors.BadRequestException
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.IriSpec.invalidIri
import dsp.valueobjects.IriSpec.userIriWithUUIDVersion3
import dsp.valueobjects.IriSpec.validUserIri
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

  private val iriSuite = suite("UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        UserIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.UserIriInvalid(invalidIri))
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        UserIri.make(userIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      val userIri = UserIri.make(validUserIri)

      (for {
        iri <- userIri
      } yield assertTrue(iri.value == validUserIri)).toZIO
    }
  )

  val spec: Spec[Any, Any] = suite("UserSpec")(
    userSuite,
    usernameSuite,
    emailSuite,
    iriSuite
  )
}
