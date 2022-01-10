/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import zio.prelude.Validation

/**
 * This spec is used to test the [[ProjectsValueObjectsADM]] value objects creation.
 */
class ProjectsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "ProjectIRI value object" when {
//    TODO: check string formatter project iri validation because passing just "http://rdfh.ch/projects/@@@@@@" works
    val validProjectIri = "http://rdfh.ch/projects/0001"

    "created using empty value" should {
      "throw BadRequestException" in {
        ProjectIRI.make("") should equal(Validation.fail(BadRequestException(PROJECT_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ProjectIRI.make("not a project IRI") should equal(
          Validation.fail(BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ProjectIRI.make(validProjectIri) should not equal Validation.fail(
          BadRequestException(PROJECT_IRI_INVALID_ERROR)
        )
      }
      "return value passed to value object" in {
        ProjectIRI.make(validProjectIri).toOption.get.value should equal(validProjectIri)
      }
    }
  }
}
