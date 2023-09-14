/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test._

import dsp.errors.BadRequestException

/**
 * This spec is used to test the [[dsp.valueobjects.RestrictedViewSize]] value object creation.
 */
object RestrictedViewSizeSpec extends ZIOSpecDefault {

  def spec = suite("Size")(
    test("pass correct percentage value") {
      assertTrue(RestrictedViewSize.make("pct:1").toOption.get.value == "pct:1")
    },
    test("pass correct dimension value") {
      assertTrue(RestrictedViewSize.make("!512,512").toOption.get.value == "!512,512")
    },
    test("fail on passing incorrect values") {
      assertTrue(
        RestrictedViewSize.make("!512,100") == Validation.fail(
          BadRequestException(ErrorMessages.RestrictedViewSizeInvalid)
        ),
        RestrictedViewSize.make("pct:0") == Validation.fail(
          BadRequestException(ErrorMessages.RestrictedViewSizeInvalid)
        ),
        RestrictedViewSize.make("pct:101") == Validation.fail(
          BadRequestException(ErrorMessages.RestrictedViewSizeInvalid)
        )
//        RestrictedViewSize.make("") == Validation.fail(BadRequestException(ErrorMessages.RestrictedViewSizeMissing))
      )
    }
  )
}
