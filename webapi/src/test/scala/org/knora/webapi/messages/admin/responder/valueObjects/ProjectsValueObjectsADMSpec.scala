/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter.UUID_INVALID_ERROR
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * This spec is used to test the [[ProjectsValueObjectsADM]] value objects creation.
 */
class ProjectsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "ProjectIRI value object" when {
    val validProjectIri = "http://rdfh.ch/projects/0001"
    val projectIRIWithUUIDVersion3 = "http://rdfh.ch/projects/tZjZhGSZMeCLA5VeUmwAmg"

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
        ProjectIRI.make(projectIRIWithUUIDVersion3) should equal(
          Validation.fail(BadRequestException(UUID_INVALID_ERROR))
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

  "Shortcode value object" when {
    val validShortcode = "1234"
    val invalidShortcode = "12345"

    "created using empty value" should {
      "throw BadRequestException" in {
        Shortcode.make("") should equal(
          Validation.fail(BadRequestException(SHORTCODE_MISSING_ERROR))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Shortcode.make(invalidShortcode) should equal(
          Validation.fail(BadRequestException(SHORTCODE_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Shortcode.make(validShortcode).toOption.get.value should not equal
          BadRequestException(SHORTCODE_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Shortcode.make(validShortcode).toOption.get.value should equal(validShortcode)
      }
    }
  }

  "Shortname value object" when {
    val validShortname = "validShortname"
    val invalidShortname = "~!@#$%^&*()_+"

    "created using empty value" should {
      "throw BadRequestException" in {
        Shortname.make("") should equal(
          Validation.fail(BadRequestException(SHORTNAME_MISSING_ERROR))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Shortname.make(invalidShortname) should equal(
          Validation.fail(BadRequestException(SHORTNAME_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Shortname.make(validShortname).toOption.get.value should not equal
          BadRequestException(SHORTNAME_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Shortname.make(validShortname).toOption.get.value should equal(validShortname)
      }
    }
  }

  "Longname value object" when {
    val validLongname = "That's the project longname"

    "created using empty value" should {
      "throw BadRequestException" in {
        Longname.make("") should equal(
          Validation.fail(BadRequestException(LONGNAME_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Longname.make(validLongname).toOption.get.value should not equal
          BadRequestException(LONGNAME_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Longname.make(validLongname).toOption.get.value should equal(validLongname)
      }
    }
  }

  "ProjectDescription value object" when {
    val validProjectDescription = Seq(StringLiteralV2(value = "Valid description", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        ProjectDescription.make(Seq.empty) should equal(
          Validation.fail(BadRequestException(PROJECT_DESCRIPTION_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ProjectDescription.make(validProjectDescription).toOption.get.value should not equal
          BadRequestException(PROJECT_DESCRIPTION_INVALID_ERROR)
      }
      "return value passed to value object" in {
        ProjectDescription.make(validProjectDescription).toOption.get.value should equal(validProjectDescription)
      }
    }
  }

  "Keywords value object" when {
    val validKeywords = Seq("key", "word")

    "created using empty value" should {
      "throw BadRequestException" in {
        Keywords.make(Seq.empty) should equal(
          Validation.fail(BadRequestException(KEYWORDS_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Keywords.make(validKeywords).toOption.get.value should not equal
          BadRequestException(KEYWORDS_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Keywords.make(validKeywords).toOption.get.value should equal(validKeywords)
      }
    }
  }

  "Logo value object" when {
    val validLogo = "/fu/bar/baz.jpg"

    "created using empty value" should {
      "throw BadRequestException" in {
        Logo.make("") should equal(
          Validation.fail(BadRequestException(LOGO_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Logo.make(validLogo).toOption.get.value should not equal
          BadRequestException(LOGO_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Logo.make(validLogo).toOption.get.value should equal(validLogo)
      }
    }
  }

  "ProjectSelfJoin value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        ProjectSelfJoin.make(false).toOption.get.value should equal(false)
        ProjectSelfJoin.make(true).toOption.get.value should equal(true)
      }
    }
  }

  "ProjectStatus value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        ProjectStatus.make(true).toOption.get.value should equal(true)
        ProjectStatus.make(false).toOption.get.value should equal(false)
      }
    }
  }
}
