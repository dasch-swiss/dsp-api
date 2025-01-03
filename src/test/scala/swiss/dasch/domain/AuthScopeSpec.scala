/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AuthScope.ScopeValue.*
import zio.test.*

object AuthScopeSpec extends ZIOSpecDefault {
  val shortcode: String => ProjectShortcode = ProjectShortcode.unsafeFrom

  val spec = suite("AuthScopeSpec")(
    test("should parse the admin scope") {
      for {
        result1 <- AuthScope.parse("admin profile")
        result2 <- AuthScope.parse("admin admin admin")
      } yield assertTrue(Set(result1, result2, AuthScope(Set(Admin))).size == 1)
    },
    test("should parse the project write scope") {
      for {
        result1 <- AuthScope.parse("write:project:1234")
        result2 <- AuthScope.parse("write:project:2345")
        result3 <- AuthScope.parse("write:project:1234 write:project:2345")
      } yield {
        List(
          assertTrue(result1 == AuthScope(Set(Write(shortcode("1234"))))),
          assertTrue(result2 == AuthScope(Set(Write(shortcode("2345"))))),
          assertTrue(result3 == AuthScope(Set(Write(shortcode("1234")), Write(shortcode("2345"))))),
        ).fold(assertTrue(true))(_ && _)
      }
    },
    test("should parse the project read scope") {
      for {
        result1 <- AuthScope.parse("read:project:1234")
        result2 <- AuthScope.parse("read:project:2345")
        result3 <- AuthScope.parse("read:project:1234 write:project:2345")
      } yield {
        assertTrue(
          result1 == AuthScope(Set(Read(shortcode("1234")))),
          result2 == AuthScope(Set(Read(shortcode("2345")))),
          result3 == AuthScope(Set(Read(shortcode("1234")), Write(shortcode("2345")))),
        )
      }
    },
    test("should ignore unknown keys, report bad project shortcodes") {
      assertTrue(
        AuthScope.parse("write:project:1234x") == Left("Predicate failed: \"1234X\".matches(\"^\\p{XDigit}{4,4}$\")."),
        AuthScope.parse("write project:1234") == Right(AuthScope.Empty),
        AuthScope.parse("write:project1234") == Right(AuthScope.Empty),
      )
    },
  )
}
