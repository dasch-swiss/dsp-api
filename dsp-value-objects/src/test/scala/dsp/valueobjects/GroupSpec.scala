/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.Group._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[Group]] value objects creation.
 */
object GroupSpec extends ZIOSpecDefault {
  private val validName        = "Valid group name"
  private val invalidName      = "Invalid group name\r"
  private val validDescription = Seq(V2.StringLiteralV2(value = "Valid group description", language = Some("en")))
  private val invalidDescription = Seq(
    V2.StringLiteralV2(value = "Invalid group description \r", language = Some("en"))
  )

  def spec = (groupNameTest + groupDescriptionsTest + groupStatusTest + groupSelfJoinTest)

  private val groupNameTest = suite("GroupSpec - GroupName")(
    test("pass an empty value and throw an error") {
      assertTrue(GroupName.make("") == Validation.fail(V2.BadRequestException(GroupErrorMessages.GroupNameMissing))) &&
      assertTrue(
        GroupName.make(Some("")) == Validation.fail(V2.BadRequestException(GroupErrorMessages.GroupNameMissing))
      )
    },
    test("pass an invalid value and throw an error") {
      assertTrue(
        GroupName.make(invalidName) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupNameInvalid)
        )
      ) &&
      assertTrue(
        GroupName.make(Some(invalidName)) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupNameInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GroupName.make(validName).toOption.get.value == validName) &&
      assertTrue(GroupName.make(Option(validName)).getOrElse(null).get.value == validName)
    },
    test("successfully validate passing None") {
      assertTrue(
        GroupName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val groupDescriptionsTest = suite("GroupSpec - GroupDescriptions")(
    test("pass an empty object and throw an error") {
      assertTrue(
        GroupDescriptions.make(Seq.empty) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupDescriptionsMissing)
        )
      ) &&
      assertTrue(
        GroupDescriptions.make(Some(Seq.empty)) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupDescriptionsMissing)
        )
      )
    },
    test("pass an invalid object and throw an error") {
      assertTrue(
        GroupDescriptions.make(invalidDescription) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid)
        )
      ) &&
      assertTrue(
        GroupDescriptions.make(Some(invalidDescription)) == Validation.fail(
          V2.BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid)
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

  private val groupStatusTest = suite("GroupSpec - GroupStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(GroupStatus.make(true).toOption.get.value == true) &&
      assertTrue(GroupStatus.make(Some(false)).getOrElse(null).get.value == false)
    },
    test("successfully validate passing None") {
      assertTrue(
        GroupStatus.make(None) == Validation.succeed(None)
      )
    }
  )

  private val groupSelfJoinTest = suite("GroupSpec - GroupSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(GroupSelfJoin.make(true).toOption.get.value == true) &&
      assertTrue(GroupSelfJoin.make(Some(false)).getOrElse(null).get.value == false)
    },
    test("successfully validate passing None") {
      assertTrue(
        GroupSelfJoin.make(None) == Validation.succeed(None)
      )
    }
  )
}
