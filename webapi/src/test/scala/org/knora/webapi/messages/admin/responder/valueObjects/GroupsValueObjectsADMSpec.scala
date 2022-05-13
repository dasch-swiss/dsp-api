/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter.IriErrorMessages.UuidInvalid
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * This spec is used to test the [[GroupsValueObjectsADM]] value objects creation.
 */
class GroupsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "GroupIRI value object" when {
    val validGroupIri            = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"
    val groupIRIWithUUIDVersion3 = "http://rdfh.ch/groups/0803/rKAU0FNjPUKWqOT8MEW_UQ"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupIRI.make("") should equal(Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupIRI.make("not a group IRI") should equal(
          Validation.fail(BadRequestException(IriErrorMessages.GroupIriInvalid))
        )
        GroupIRI.make(groupIRIWithUUIDVersion3) should equal(
          Validation.fail(BadRequestException(IriErrorMessages.UuidInvalid))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestException" in {
        GroupIRI.make(validGroupIri) should not equal Validation.fail(
          BadRequestException(IriErrorMessages.GroupIriInvalid)
        )
      }
      "return value passed to value object" in {
        GroupIRI.make(validGroupIri).toOption.get.value should equal(validGroupIri)
      }
    }
  }

  "GroupName value object" when {
    val validGroupName = "Valid group name"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupName.make("") should equal(Validation.fail(BadRequestException(GroupErrorMessages.GroupNameMissing)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupName.make("Invalid group name\r") should equal(
          Validation.fail(BadRequestException(GroupErrorMessages.GroupNameInvalid))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupName.make(validGroupName) should not equal Validation.fail(
          BadRequestException(GroupErrorMessages.GroupNameInvalid)
        )
      }
      "return value passed to value object" in {
        GroupName.make(validGroupName).toOption.get.value should equal(validGroupName)
      }
    }
  }

  "GroupDescriptions value object" when {
    val validDescription   = Seq(StringLiteralV2(value = "Valid description", language = Some("en")))
    val invalidDescription = Seq(StringLiteralV2(value = "Invalid description \r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupDescriptions.make(Seq.empty) should equal(
          Validation.fail(BadRequestException(GroupErrorMessages.GroupDescriptionMissing))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupDescriptions.make(invalidDescription) should equal(
          Validation.fail(BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupDescriptions.make(validDescription).toOption.get.value should not equal
          BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR)
      }
      "return value passed to value object" in {
        GroupDescriptions.make(validDescription).toOption.get.value should equal(validDescription)
      }
    }
  }

  "GroupStatus value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        GroupStatus.make(true).toOption.get.value should equal(true)
        GroupStatus.make(false).toOption.get.value should equal(false)
      }
    }
  }

  "GroupSelfJoin value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        GroupSelfJoin.make(false).toOption.get.value should equal(false)
        GroupSelfJoin.make(true).toOption.get.value should equal(true)
      }
    }
  }
}
