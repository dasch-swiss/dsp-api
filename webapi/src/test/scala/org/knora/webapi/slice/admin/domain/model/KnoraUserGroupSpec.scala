/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.*

/**
 * This spec is used to test the [[Group]] value objects creation.
 */
object KnoraUserGroupSpec extends ZIOSpecDefault {
  private val validDescription = Seq(V2.StringLiteralV2(value = "Valid group description", language = Some("en")))
  private val invalidDescription = Seq(
    V2.StringLiteralV2(value = "", language = Some("en"))
  )

  def spec: Spec[Any, Any] = groupNameTest + groupDescriptionsTest + groupStatusTest + groupSelfJoinTest

  private val groupNameTest = suite("GroupName should")(
    test("not be created from an empty value") {
      assertTrue(GroupName.from("") == Left(GroupErrorMessages.GroupNameMissing))
    },
    test("be created from a valid value") {
      assertTrue(GroupName.from("Valid group name").map(_.value) == Right("Valid group name"))
    }
  )

  private val groupDescriptionsTest = suite("GroupDescriptions should")(
    test("not be created from an empty value") {
      val emptyDescription = Seq.empty[V2.StringLiteralV2]
      assertTrue(GroupDescriptions.from(emptyDescription) == Left(GroupErrorMessages.GroupDescriptionsMissing))
    },
    test("not be created from an invalid value") {
      assertTrue(GroupDescriptions.from(invalidDescription) == Left(GroupErrorMessages.GroupDescriptionsInvalid))
    },
    test("be created from a valid value") {
      assertTrue(GroupDescriptions.from(validDescription).map(_.value) == Right(validDescription))
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
