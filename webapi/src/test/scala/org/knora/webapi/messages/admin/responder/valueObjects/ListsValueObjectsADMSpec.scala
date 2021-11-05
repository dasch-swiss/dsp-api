/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

/**
 * This spec is used to test the [[ListsValueObjectsADM]] value objects creation.
 */
class ListsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "ListIRI value object" when {
    val validListIri = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListIRI.create("") should equal(Left(BadRequestException(LIST_NODE_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ListIRI.create("not a list IRI") should equal(Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "return value object that value equals to the value used to its creation" in {
        ListIRI.create(validListIri) should not equal Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
      }
      "return value passed to value object" in {
        ListIRI.create(validListIri).toOption.get.value should equal(validListIri)
      }
    }
  }

  "ProjectIRI value object" when {
//    TODO: check string formatter project iri validation because passing just "http://rdfh.ch/projects/@@@@@@" works
//    TODO: move project IRI related test
    val validProjectIri = "http://rdfh.ch/projects/0001"

    "created using empty value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("") should equal(Left(BadRequestException(PROJECT_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("not a project IRI") should equal(Left(BadRequestException(PROJECT_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ProjectIRI.create(validProjectIri) should not equal Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      }
      "return value passed to value object" in {
        ProjectIRI.create(validProjectIri).toOption.get.value should equal(validProjectIri)
      }
    }
  }

  "ListName value object" when {
    val validListName = "Valid list name"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListName.create("") should equal(Left(BadRequestException(LIST_NAME_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ListName.create("\r") should equal(Left(BadRequestException(LIST_NAME_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ListName.create(validListName) should not equal Left(BadRequestException(LIST_NAME_INVALID_ERROR))
      }
      "return value passed to value object" in {
        ListName.create(validListName).toOption.get.value should equal(validListName)
      }
    }
  }

  "Position value object" when {
    val validPosition = 0

    "created using invalid value" should {
      "throw BadRequestException" in {
        Position.create(-2) should equal(Left(BadRequestException(INVALID_POSITION)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Position.create(validPosition) should not equal Left(BadRequestException(INVALID_POSITION))
      }
      "return value passed to value object" in {
        Position.create(validPosition).toOption.get.value should equal(validPosition)
      }
    }
  }

  "Labels value object" when {
    val validLabels = Seq(StringLiteralV2(value = "New Label", language = Some("en")))
    val invalidLabels = Seq(StringLiteralV2(value = "\r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Labels.create(Seq.empty) should equal(Left(BadRequestException(LABEL_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Labels.create(invalidLabels) should equal(Left(BadRequestException(LABEL_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Labels.create(validLabels) should not equal Left(BadRequestException(LABEL_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Labels.create(validLabels).toOption.get.value should equal(validLabels)
      }
    }
  }

  "Comments value object" when {
    val validComments = Seq(StringLiteralV2(value = "Valid comment", language = Some("en")))
    val invalidComments = Seq(StringLiteralV2(value = "Invalid comment \r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Comments.create(Seq.empty) should equal(Left(BadRequestException(COMMENT_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Comments.create(invalidComments) should equal(Left(BadRequestException(COMMENT_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Comments.create(validComments) should not equal Left(BadRequestException(COMMENT_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Comments.create(validComments).toOption.get.value should equal(validComments)
      }
    }
  }
}
