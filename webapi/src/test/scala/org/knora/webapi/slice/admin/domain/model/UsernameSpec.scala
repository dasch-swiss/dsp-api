/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object UsernameSpec extends ZIOSpecDefault {
  private val validNames = Seq(
    "username",
    "user_name",
    "user-name",
    "user.name",
    "user123",
    "user_123",
    "user-123",
    "user.123",
    "use",
  )
  private val invalidNames = Seq(
    "_username",   // (starts with underscore)
    "-username",   // (starts with hyphen)
    ".username",   // (starts with dot)
    "username_",   // (ends with underscore)
    "username-",   // (ends with hyphen)
    "username.",   // (ends with dot)
    "user__name",  // (contains multiple underscores in a row)
    "user--name",  // (contains multiple hyphens in a row)
    "user..name",  // (contains multiple dots in a row)
    "us",          // (less than 3 characters)
    "a".repeat(51), // (more than 50 characters)
  )

  val spec = suite("UsernameSpec")(
    test("Username must not be empty") {
      assertTrue(Username.from("") == Left("Username cannot be empty."))
    },
    test("should allow valid names") {
      check(Gen.fromIterable(validNames))(it => assertTrue(Username.from(it).map(_.value) == Right(it)))
    },
    test("should reject invalid names") {
      check(Gen.fromIterable(invalidNames))(it => assertTrue(Username.from(it) == Left("Username is invalid.")))
    },
  )
}
