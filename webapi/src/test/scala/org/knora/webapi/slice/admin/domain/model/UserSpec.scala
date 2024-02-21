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

  private val userSuite = suite("User")()

  private val usernameSuite = suite("Username")(
    test("Username must not be empty") {
      assertTrue(Username.from("") == Left("Username cannot be empty."))
    },
    test("Username may contain alphanumeric characters, underscore, hyphen and dot") {
      assertTrue(Username.from("a_2.3-4").isRight)
    },
    test("Username has to be at least 4 characters long") {
      assertTrue(Username.from("abc") == Left("Username is invalid."))
    },
    test("Username has to be at most 50 characters long") {
      assertTrue(
        Username.from("123456789012345678901234567890123456789012345678901") == Left("Username is invalid.")
      )
    },
    test("Username must not contain other characters") {
      val invalid = List("a_2.3!", "a_2,3", "a.b@example.com")
      check(Gen.fromIterable(invalid)) { i =>
        assertTrue(Username.from(i) == Left("Username is invalid."))
      }
    },
    test("Username must not start with a dot") {
      assertTrue(Username.from(".abc") == Left("Username is invalid."))
    },
    test("Username must not end with a dot") {
      assertTrue(Username.from("abc.") == Left("Username is invalid."))
    },
    test("Username must not contain two dots in a row") {
      assertTrue(Username.from("a..bc") == Left("Username is invalid."))
    },
    test("Username must not start with an underscore") {
      assertTrue(Username.from("_abc") == Left("Username is invalid."))
    },
    test("Username must not end with an underscore") {
      assertTrue(Username.from("abc_") == Left("Username is invalid."))
    },
    test("Username must not contain two underscores in a row") {
      assertTrue(Username.from("a__bc") == Left("Username is invalid."))
    },
    test("Username must not start with an hyphen") {
      assertTrue(Username.from("-abc") == Left("Username is invalid."))
    },
    test("Username must not end with an hyphen") {
      assertTrue(Username.from("abc-") == Left("Username is invalid."))
    },
    test("Username must not contain two hyphen in a row") {
      assertTrue(Username.from("a--bc") == Left("Username is invalid."))
    }
  )

  private val emailSuite = suite("Email")(
    test("Email must be a correct email address") {
      assertTrue(Email.from("j.doe@example.com").isRight)
    },
    test("Email must not be empty") {
      assertTrue(Email.from("") == Left("Email cannot be empty."))
    },
    test("Email must not be a username") {
      assertTrue(Email.from("j.doe") == Left("Email is invalid."))
    }
  )

  private val iriSuite = suite("UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.from("") == Left("User IRI cannot be empty."))
    },
    test("make new should create a valid user iri") {
      assertTrue(UserIri.makeNew.value.startsWith("http://rdfh.ch/users/"))
    },
    test("built in users should be builtIn") {
      val builtInIris = Gen.fromIterable(
        Seq(
          "http://www.knora.org/ontology/knora-admin#AnonymousUser",
          "http://www.knora.org/ontology/knora-admin#SystemUser",
          "http://www.knora.org/ontology/knora-admin#AnonymousUser"
        )
      )
      check(builtInIris) { i =>
        val userIri = UserIri.unsafeFrom(i)
        assertTrue(!userIri.isRegularUser, userIri.isBuiltInUser)
      }
    },
    test("regular user iris should not be builtIn") {
      val builtInIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE"
        )
      )
      check(builtInIris) { i =>
        val userIri = UserIri.unsafeFrom(i)
        assertTrue(userIri.isRegularUser, !userIri.isBuiltInUser)
      }
    },
    test("valid iris should be a valid iri") {
      val validIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
          "http://www.knora.org/ontology/knora-admin#AnonymousUser",
          "http://www.knora.org/ontology/knora-admin#SystemUser",
          "http://www.knora.org/ontology/knora-admin#AnonymousUser",
          "http://rdfh.ch/users/mls-0807-import-user",
          "http://rdfh.ch/users/root",
          "http://rdfh.ch/users/images-reviewer-user",
          "http://rdfh.ch/users/AnythingAdminUser",
          "http://rdfh.ch/users/subotic",
          "http://rdfh.ch/users/_fH9FS-VRMiPPiIMRpjevA"
        )
      )
      check(validIris)(i => assertTrue(UserIri.from(i).isRight))
    },
    test("pass an invalid value and return an error") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/user/AnythingAdminUser",
          "http://rdfh.ch/users/AnythingAdminUser/"
        )
      )
      check(invalidIris)(i => assertTrue(UserIri.from(i) == Left(s"User IRI is invalid.")))
    }
  )

  private val givenNameSuite = suite("GivenName")(
    test("pass an empty value and return an error") {
      assertTrue(GivenName.from("") == Left("GivenName cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GivenName.from(validGivenName).isRight)
    }
  )

  private val familyNameSuite = suite("FamilyName")(
    test("pass an empty value and return an error") {
      assertTrue(FamilyName.from("") == Left("FamilyName cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(FamilyName.from(validFamilyName).isRight)
    }
  )

  private val passwordSuite = suite("Password")(
    test("pass an empty value and return an error") {
      assertTrue(Password.from("") == Left("Password cannot be empty."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Password.from(validPassword).isRight)
    }
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
