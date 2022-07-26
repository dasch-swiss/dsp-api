/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.Role._
import zio.test.ZIOSpecDefault
import zio.prelude.Validation
import zio.test._
import dsp.errors.BadRequestException

/**
 * This spec is used to test the [[Role]] value objects creation.
 */
object RoleSpec extends ZIOSpecDefault {
  private val validLangStringValue     = "I'm valid LangString value"
  private val invalidLangStringValue   = "Invalid \r"
  private val validPermission          = Permission.View
  private val invalidPermission        = "mod"
  private val validLangStringIsoCode   = V2.EN
  private val invalidLangStringIsoCode = "iso"

  def spec =
    (langStringTest + permissionTest)

  private val langStringTest = suite("LangString")(
    test("pass an empty value and return an error") {
      assertTrue(
        LangString.make("", validLangStringIsoCode) == Validation.fail(
          BadRequestException(RoleErrorMessages.LangStringValueMissing)
        )
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        LangString.make(invalidLangStringValue, validLangStringIsoCode) == Validation.fail(
          BadRequestException(RoleErrorMessages.LangStringValueInvalid(invalidLangStringValue))
        )
      )
    },
    test("pass an empty ISO code and return an error") {
      assertTrue(
        LangString.make(validLangStringValue, "") == Validation.fail(
          BadRequestException(RoleErrorMessages.LangStringIsoCodeMissing)
        )
      )
    },
    test("pass an invalid ISO code and return an error") {
      assertTrue(
        LangString.make(validLangStringValue, invalidLangStringIsoCode) == Validation.fail(
          BadRequestException(RoleErrorMessages.LangStringIsoCodeInvalid(invalidLangStringIsoCode))
        )
      )
    }
  )

  private val permissionTest = suite("Permission")(
    test("pass an empty value and return an error") {
      assertTrue(
        Permission.make("") == Validation.fail(
          BadRequestException(RoleErrorMessages.PermissionMissing)
        )
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        Permission.make(invalidPermission) == Validation.fail(
          BadRequestException(RoleErrorMessages.PermissionInvalid(invalidPermission))
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Permission.make(validPermission).toOption.get.value == validPermission)
    }
  )
}
