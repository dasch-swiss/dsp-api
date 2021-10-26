/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  COMMENT_MISSING_ERROR,
  INVALID_POSITION,
  LABEL_MISSING_ERROR,
  LIST_NAME_MISSING_ERROR,
  LIST_NODE_IRI_INVALID_ERROR,
  LIST_NODE_IRI_MISSING_ERROR,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.{IRI, UnitSpec}

/**
 * This spec is used to test the creation of value objects of the [[ListsValueObjectsADM]].
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
        ListIRI.create("123") should equal(Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "return value object that value equals to the value used to its creation" in {
//        TODO: this probably doesn't make sense
        ListIRI.create(validListIri).map(_.value should equal(validListIri))
      }
    }
  }

  "ProjectIRI value object" when {
//    TODO: check string formatter project iri validation because passing just "http://rdfh.ch/projects/@@@@@@" works
    val validProjectIri = "http://rdfh.ch/projects/0001"

    "created using empty value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("") should equal(Left(BadRequestException(PROJECT_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("123") should equal(Left(BadRequestException(PROJECT_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
//        ProjectIRI.create(validProjectIri).map(_.value should equal(validProjectIri))
        ProjectIRI.create(validProjectIri) should not equal Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      }
    }
  }

  "RootNodeIRI value object" when {
//    TODO: check string formatter list iri validation because passing just "http://rdfh.ch" works
    val validRootNodeIRI = "http://rdfh.ch/lists/0001/yWQEGXl53Z4C4DYJ-S2c5A"

    "created using empty value" should {
      "throw BadRequestException" in {
        RootNodeIRI.create("") should equal(Left(BadRequestException("Missing root node IRI")))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        RootNodeIRI.create("123") should equal(Left(BadRequestException("Invalid root node IRI")))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        RootNodeIRI.create(validRootNodeIRI) should not equal Left(BadRequestException("Invalid root node IRI"))
      }
    }
  }

  "ListName value object" when {
    val validListName = "It's valid list name example"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListName.create("") should equal(Left(BadRequestException(LIST_NAME_MISSING_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ListName.create(validListName) should not equal Left(BadRequestException(LIST_NAME_MISSING_ERROR))
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
    }
  }

  "Labels value object" when {
    val validLabels = Seq(StringLiteralV2(value = "New Label", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Labels.create(Seq.empty) should equal(Left(BadRequestException(LABEL_MISSING_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Labels.create(validLabels) should not equal Left(BadRequestException(LABEL_MISSING_ERROR))
      }
    }
  }

  "Comments value object" when {
    val validComments = Seq(StringLiteralV2(value = "New Comment", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Comments.create(Seq.empty) should equal(Left(BadRequestException(COMMENT_MISSING_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Comments.create(validComments) should not equal Left(BadRequestException(COMMENT_MISSING_ERROR))
      }
    }
  }
}
