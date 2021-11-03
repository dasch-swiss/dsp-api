/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM.{
  GROUP_DESCRIPTION_INVALID_ERROR,
  GROUP_DESCRIPTION_MISSING_ERROR,
  GROUP_IRI_INVALID_ERROR,
  GROUP_IRI_MISSING_ERROR,
  GROUP_NAME_INVALID_ERROR,
  GROUP_NAME_MISSING_ERROR
}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * This spec is used to test the [[GroupsValueObjectsADM]] value objects creation.
 */
class GroupsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {

  "GroupIRI value object" when {
    val validGroupIri = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupIRI.create("") should equal(Left(BadRequestException(GROUP_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupIRI.create("not a group IRI") should equal(Left(BadRequestException(GROUP_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "return value object that value equals to the value used to its creation" in {
        GroupIRI.create(validGroupIri) should not equal Left(BadRequestException(GROUP_IRI_INVALID_ERROR))
      }
    }
  }

  "GroupName value object" when {
    val validGroupName = "It's valid group name example"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupName.create("") should equal(Left(BadRequestException(GROUP_NAME_MISSING_ERROR)))
      }
    }
//    TODO: add more checks to name and description
//    "created using invalid value" should {
//      "throw BadRequestException" in {
////        TODO: should this: "\"It's invalid group name example\"" pass? Same for comments and labels
////        TODO: try this too "Neue geänderte Liste mit A'postroph"
//        GroupName.create("\r") should equal(Left(BadRequestException(GROUP_NAME_INVALID_ERROR)))
//      }
//    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupName.create(validGroupName) should not equal Left(BadRequestException(GROUP_NAME_INVALID_ERROR))
      }
    }
  }

  "GroupDescription value object" when {
    val validDescription = Seq(StringLiteralV2(value = "New Description", language = Some("en")))
    val invalidDescription = Seq(StringLiteralV2(value = "\r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupDescription.make(Seq.empty) should equal(
          Validation.fail(BadRequestException(GROUP_DESCRIPTION_MISSING_ERROR))
        )
      }
    }
//    "created using invalid value" should {
//      "throw BadRequestException" in {
//        Labels.create(invalidDescription) should equal(Left(BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR)))
//      }
//    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Labels.create(validDescription) should not equal Validation.fail(
          BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR)
        )
      }
    }
  }

  "GroupStatus value object" when {
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupStatus.make(true).map(_.value) should equal(Validation.succeed(true))
      }
    }
  }

  "GroupSelfJoin value object" when {
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupSelfJoin.make(false).map(_.value) should equal(Validation.succeed(false))
      }
    }
  }
}
