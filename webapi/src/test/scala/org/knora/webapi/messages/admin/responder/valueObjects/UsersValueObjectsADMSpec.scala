/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.usersmessages.UserErrorMessagesADM._
import zio.prelude.Validation

/**
 * This spec is used to test the [[UsersValueObjectsADM]] value objects creation.
 */
class UsersValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "UserIRI value object" when {
    val validUserIRI = "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ"

    "created using empty value" should {
      "throw BadRequestException" in {
        UserIRI.make("") should equal(Validation.fail(BadRequestException(USER_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        UserIRI.make("not a user IRI") should equal(Validation.fail(BadRequestException(USER_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestException" in {
        UserIRI.make(validUserIRI) should not equal Validation.fail(BadRequestException(USER_IRI_INVALID_ERROR))
      }
      "return value passed to value object" in {
        UserIRI.make(validUserIRI).toOption.get.value should equal(validUserIRI)
      }
    }
  }

  "Username value object" when {
    val validUsername = "user008"
    val invalidUsername = "user!@#$%^&*()_+"

    "created using empty value" should {
      "throw BadRequestException" in {
        Username.make("") should equal(Validation.fail(BadRequestException(USERNAME_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException for username less than 4 characters long" in {
        Username.make("123") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username containg more than 50 characters" in {
        Username.make("01234567890123456789012345678901234567890123456789011") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username started with underscore" in {
        Username.make("_123") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username ended with underscore" in {
        Username.make("123_") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username started with dot" in {
        Username.make(".123") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username ended with dot" in {
        Username.make("123.") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username with underscore used multiple times in a row" in {
        Username.make("1__23") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username with dot used multiple times in a row" in {
        Username.make("1..23") should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
      "throw BadRequestException for username created with bad forbidden characters" in {
        Username.make(invalidUsername) should equal(
          Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
        )
      }
    }
    "created using valid characters" should {
      "not throw BadRequestExceptions" in {
        Username.make(validUsername) should not equal Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Username.make(validUsername).toOption.get.value should equal(validUsername)
      }
    }
  }

  "Email value object" when {
    val validEmailAddress = "address@ch"
    val invalidEmailAddress = "invalid_email_address"

    "created using empty value" should {
      "throw BadRequestException" in {
        Email.make("") should equal(
          Validation.fail(BadRequestException(EMAIL_MISSING_ERROR))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Email.make(invalidEmailAddress) should equal(
          Validation.fail(BadRequestException(EMAIL_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Email.make(validEmailAddress).toOption.get.value should not equal
          BadRequestException(EMAIL_INVALID_ERROR)
      }
      "return value passed to value object" in {
        Email.make(validEmailAddress).toOption.get.value should equal(validEmailAddress)
      }
    }
  }

  "Password value object" when {
    val validassword = "pass-word"

    "created using empty value" should {
      "throw BadRequestException" in {
        Password.make("") should equal(Validation.fail(BadRequestException(PASSWORD_MISSING_ERROR)))
      }
    }
    "created using valid characters" should {
      "not throw BadRequestExceptions" in {
        Password.make(validassword) should not equal Validation.fail(BadRequestException(USERNAME_INVALID_ERROR))
      }
      "return value passed to value object" in {
        Password.make(validassword).toOption.get.value should equal(validassword)
      }
    }
  }

  "GivenName value object" when {
    val validGivenName = "John"

    "created using empty value" should {
      "throw BadRequestException" in {
        GivenName.make("") should equal(
          Validation.fail(BadRequestException(GIVEN_NAME_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GivenName.make(validGivenName).toOption.get.value should not equal
          BadRequestException(GIVEN_NAME_INVALID_ERROR)
      }
      "return value passed to value object" in {
        GivenName.make(validGivenName).toOption.get.value should equal(validGivenName)
      }
    }
  }

  "FamilyName value object" when {
    val validFamilyName = "Rambo"

    "created using empty value" should {
      "throw BadRequestException" in {
        FamilyName.make("") should equal(
          Validation.fail(BadRequestException(FAMILY_NAME_MISSING_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        FamilyName.make(validFamilyName).toOption.get.value should not equal
          BadRequestException(FAMILY_NAME_INVALID_ERROR)
      }
      "return value passed to value object" in {
        FamilyName.make(validFamilyName).toOption.get.value should equal(validFamilyName)
      }
    }
  }

  "LanguageCode value object" when {
    "created using empty value" should {
      "throw BadRequestException" in {
        LanguageCode.make("") should equal(
          Validation.fail(BadRequestException(LANGUAGE_CODE_MISSING_ERROR))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        LanguageCode.make("kk") should equal(
          Validation.fail(BadRequestException(LANGUAGE_CODE_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        LanguageCode.make("de").toOption.get.value should not equal
          BadRequestException(LANGUAGE_CODE_INVALID_ERROR)
      }
      "return value passed to value object" in {
        LanguageCode.make("en").toOption.get.value should equal("en")
      }
    }
  }

  "SystemAdmin value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        SystemAdmin.make(true).toOption.get.value should equal(true)
        SystemAdmin.make(false).toOption.get.value should equal(false)
      }
    }
  }
}
