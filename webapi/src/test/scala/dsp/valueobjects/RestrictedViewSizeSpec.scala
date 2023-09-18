/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.test._

/**
 * This spec is used to test the [[dsp.valueobjects.RestrictedViewSize]] value object creation.
 */
object RestrictedViewSizeSpec extends ZIOSpecDefault {

  def spec = suite("Size")(
    test("should succeed on passing percentage values") {
      val gen = Gen.int(1,100)
      check(gen) { int =>
        val param = s"pct:$int"
        assertTrue(RestrictedViewSize.make(param).map(_.value) == Right(param))
      }
    },
    test("should succeed on passing the same x y dimensions") {
      val gen = Gen.int(1, 1000)
      check(gen) { integer =>
        val param = s"!$integer,$integer"
        assertTrue(RestrictedViewSize.make(param).map(_.value) == Right(param))
      }
    },
    test("should fail on passing negative x y dimensions") {
      val gen = Gen.int(-1000, -1)
      check(gen) { integer =>
        val param = s"!$integer,$integer"
        assertTrue(RestrictedViewSize.make(param).map(_.value) == Left(ErrorMessages.RestrictedViewSizeInvalid))
      }
    },
    test("should fail on passing incorrect values") {
      val gen = Gen.fromIterable(Seq("!512,100", "pct:-1", "pct:0", "pct:101"))
      check(gen) { param =>
        assertTrue(RestrictedViewSize.make(param) == Left(ErrorMessages.RestrictedViewSizeInvalid))
      }
    },
    test("should fail on passing empty value") {
      assertTrue(RestrictedViewSize.make("") == Left(ErrorMessages.RestrictedViewSizeMissing))
    }
  )
}
