/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test.*

import dsp.errors.BadRequestException
import dsp.valueobjects.Group.*

/**
 * This spec is used to test the [[Group]] value objects creation.
 */
object GroupSpec extends ZIOSpecDefault {
  private val validDescription = Seq(V2.StringLiteralV2(value = "Valid group description", language = Some("en")))
  private val invalidDescription = Seq(
    V2.StringLiteralV2(value = "Invalid group description \r", language = Some("en"))
  )

  def spec: Spec[Any, Any] = groupNameTest + groupDescriptionsTest + groupStatusTest + groupSelfJoinTest

  private val groupNameTest = suite("GroupName should")(
    test("not be created from an empty value") {
      assertTrue(GroupName.from("") == Left(GroupErrorMessages.GroupNameMissing))
    },
    test("not be created from an invalid value") {
      assertTrue(GroupName.from("Invalid group name\r") == Left(GroupErrorMessages.GroupNameInvalid))
    },
    test("be created from a valid value") {
      assertTrue(GroupName.from("Valid group name").map(_.value) == Right("Valid group name"))
    }
  )

  private val groupDescriptionsTest = suite("GroupSpec - GroupDescriptions")(
    test("pass an empty object and return an error") {
      assertTrue(
        GroupDescriptions.make(Seq.empty) == Validation.fail(
          BadRequestException(GroupErrorMessages.GroupDescriptionsMissing)
        )
      ) &&
      assertTrue(
        GroupDescriptions.make(Some(Seq.empty)) == Validation.fail(
          BadRequestException(GroupErrorMessages.GroupDescriptionsMissing)
        )
      )
    },
    test("pass an invalid object and return an error") {
      assertTrue(
        GroupDescriptions.make(invalidDescription) == Validation.fail(
          BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid)
        )
      ) &&
      assertTrue(
        GroupDescriptions.make(Some(invalidDescription)) == Validation.fail(
          BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid)
        )
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(GroupDescriptions.make(validDescription).toOption.get.value == validDescription) &&
      assertTrue(GroupDescriptions.make(Option(validDescription)).getOrElse(null).get.value == validDescription)
    },
    test("successfully validate passing None") {
      assertTrue(
        GroupDescriptions.make(None) == Validation.succeed(None)
      )
    }
  )

  private val groupStatusTest = suite("GroupStatus")(
    test("should be created from a valid value") {
      assertTrue(
        GroupStatus.from(true) == GroupStatus.active,
        GroupStatus.from(false) == GroupStatus.inactive
      )
    }
  )

  private val groupSelfJoinTest = suite("GroupSelfJoin")(
    test("should be created from a valid value") {
      assertTrue(
        GroupSelfJoin.from(true) == GroupSelfJoin.possible,
        GroupSelfJoin.from(false) == GroupSelfJoin.impossible
      )
    }
  )
}
