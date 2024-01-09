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

object UserSpec extends ZIOSpecDefault {

  private val userSuite = suite("User")()

  private val usernameSuite = suite("Username")(
    test("Username may contain alphanumeric characters, underscore and dot") {
      assertTrue(Username.make("a_2.3").toEither.isRight)
    },
    test("Username has to be at least 4 characters long") {
      assertTrue(Username.make("abc").toEither.isLeft)
    },
    test("Username has to be at most 50 characters long") {
      assertTrue(Username.make("123456789012345678901234567890123456789012345678901").toEither.isLeft)
    },
    test("Username must not contain other characters") {
      assertTrue(
        Username.make("a_2.3!").toEither.isLeft,
        Username.make("a_2-3").toEither.isLeft,
        Username.make("a.b@example.com").toEither.isLeft
      )
    },
    test("Username must not start with a dot") {
      assertTrue(Username.make(".abc").toEither.isLeft)
    },
    test("Username must not end with a dot") {
      assertTrue(Username.make("abc.").toEither.isLeft)
    },
    test("Username must not contain two dots in a row") {
      assertTrue(Username.make("a..bc").toEither.isLeft)
    },
    test("Username must not start with an underscore") {
      assertTrue(Username.make("_abc").toEither.isLeft)
    },
    test("Username must not end with an underscore") {
      assertTrue(Username.make("abc_").toEither.isLeft)
    },
    test("Username must not contain two underscores in a row") {
      assertTrue(Username.make("a__bc").toEither.isLeft)
    }
  )

  private val emailSuite = suite("Email")(
    test("Email must be a correct email address") {
      assertTrue(Email.make("j.doe@example.com").toEither.isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.make("").toEither.isLeft)
    },
    test("Email must not be a username") {
      assertTrue(Email.make("j.doe").toEither.isLeft)
    }
  )

  private val iriSuite = suite("UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.make("") == Validation.fail(BadRequestException(UserErrorMessages.UserIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        UserIri.make(invalidIri) == Validation.fail(
          BadRequestException(UserErrorMessages.UserIriInvalid(invalidIri))
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
