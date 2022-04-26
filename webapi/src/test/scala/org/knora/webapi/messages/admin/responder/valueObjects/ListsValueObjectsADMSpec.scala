/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter.UUID_INVALID_ERROR
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * This spec is used to test the [[ListsValueObjectsADM]] value objects creation.
 */
class ListsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "ListIRI value object" when {
    val validListIri            = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
    val listIRIWithUUIDVersion3 = "http://rdfh.ch/lists/0803/6_xROK_UN1S2ZVNSzLlSXQ"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListIRI.make("") should equal(Validation.fail(BadRequestException(LIST_NODE_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ListIRI.make("not a list IRI") should equal(Validation.fail(BadRequestException(LIST_NODE_IRI_INVALID_ERROR)))
        ListIRI.make(listIRIWithUUIDVersion3) should equal(Validation.fail(BadRequestException(UUID_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "return value object that value equals to the value used to its creation" in {
        ListIRI.make(validListIri) should not equal Validation.fail(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
      }
      "return value passed to value object" in {
        ListIRI.make(validListIri).toOption.get.value should equal(validListIri)
      }
    }
  }

  "ListName value object" when {
    val validListName = "Valid list name"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListName.make("") should equal(Validation.fail(BadRequestException(LIST_NAME_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ListName.make("\r") should equal(Validation.fail(BadRequestException(LIST_NAME_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ListName.make(validListName) should not equal Validation.fail(BadRequestException(LIST_NAME_INVALID_ERROR))
      }
      "return value passed to value object" in {
        ListName.make(validListName).toOption.get.value should equal(validListName)
      }
    }
  }

  "Position value object" when {
    val validPosition = 0

    "created using invalid value" should {
      "throw BadRequestException" in {
        Position.make(-2) should equal(Validation.fail(BadRequestException(INVALID_POSITION)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Position.make(validPosition) should not equal Validation.fail(BadRequestException(INVALID_POSITION))
      }
      "return value passed to value object" in {
        Position.make(validPosition).toOption.get.value should equal(validPosition)
      }
    }
  }

  "Labels value object" when {
    val validLabels   = Seq(StringLiteralV2(value = "New Label", language = Some("en")))
    val invalidLabels = Seq(StringLiteralV2(value = "\r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Labels.make(Seq.empty) should equal(Validation.fail(BadRequestException(LABEL_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Labels.make(invalidLabels) should equal(Validation.fail(BadRequestException(LABEL_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Labels.make(validLabels) should not equal Validation.fail(BadRequestException(LABEL_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Labels.make(validLabels).toOption.get.value should equal(validLabels)
      }
    }
  }

  "Comments value object" when {
    val validComments   = Seq(StringLiteralV2(value = "Valid comment", language = Some("en")))
    val invalidComments = Seq(StringLiteralV2(value = "Invalid comment \r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Comments.make(Seq.empty) should equal(Validation.fail(BadRequestException(COMMENT_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Comments.make(invalidComments) should equal(Validation.fail(BadRequestException(COMMENT_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Comments.make(validComments) should not equal Validation.fail(BadRequestException(COMMENT_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Comments.make(validComments).toOption.get.value should equal(validComments)
      }
    }
  }
}
