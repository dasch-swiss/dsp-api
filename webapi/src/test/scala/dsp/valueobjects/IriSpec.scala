/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test.*
import zio.test.Assertion.*

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri.*

/**
 * This spec is used to test the [[Iri]] value objects creation.
 */
object IriSpec extends ZIOSpecDefault {
  private val uuidVersion3  = "cCmdcpn2MO211YYOplR1hQ"
  private val supportedUuid = "jDEEitJESRi3pDaDjjQ1WQ"

  private val invalidIri = "Invalid IRI"

  private val validListIri            = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
  private val listIriWithUUIDVersion3 = "http://rdfh.ch/lists/0803/6_xROK_UN1S2ZVNSzLlSXQ"

  private val validRoleIri            = "http://rdfh.ch/roles/ZPKPVh8yQs6F7Oyukb8WIQ"
  private val roleIriWithUUIDVersion3 = "http://rdfh.ch/roles/Ul3IYhDMOQ2fyoVY0ePz0w"

  def spec: Spec[Any, Throwable] = listIriTest + uuidTest + roleIriTest

  private val listIriTest = suite("IriSpec - ListIri")(
    test("pass an empty value and return an error") {
      assertTrue(ListIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))) &&
      assertTrue(
        ListIri.make(Some("")) == Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ListIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.ListIriInvalid)
        )
      ) &&
      assertTrue(
        ListIri.make(Some(invalidIri)) == Validation.fail(
          BadRequestException(IriErrorMessages.ListIriInvalid)
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        ListIri.make(listIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      ) &&
      assertTrue(
        ListIri.make(Some(listIriWithUUIDVersion3)) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      val listIri      = ListIri.make(validListIri)
      val maybeListIri = ListIri.make(Some(validListIri))

      (for {
        iri      <- listIri
        maybeIri <- maybeListIri
      } yield assertTrue(iri.value == validListIri) &&
        assert(maybeIri)(isSome(equalTo(iri)))).toZIO
    },
    test("successfully validate passing None") {
      assertTrue(
        ListIri.make(None) == Validation.succeed(None)
      )
    }
  )

  private val uuidTest = suite("IriSpec - Base64Uuid")(
    test("pass an empty value and return an error") {
      assertTrue(Base64Uuid.make("") == Validation.fail(ValidationException(IriErrorMessages.UuidMissing)))
    },
    test("pass an invalid UUID and return an error") {
      assertTrue(
        Base64Uuid.make(invalidIri) == Validation.fail(ValidationException(IriErrorMessages.UuidInvalid(invalidIri)))
      )
    },
    test("pass an valid UUID, which has not supported version 3") {
      assertTrue(
        Base64Uuid.make(uuidVersion3) == Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
      )
    },
    test("pass valid UUID and successfully create value object") {
      (for {
        uuid <- Base64Uuid.make(supportedUuid)
      } yield assertTrue(uuid.value == supportedUuid)).toZIO
    }
  )

  private val roleIriTest = suite("IriSpec - roleIri")(
    test("pass an empty value and return an error") {
      assertTrue(RoleIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.RoleIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        RoleIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.RoleIriInvalid(invalidIri))
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        RoleIri.make(roleIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(RoleIri.make(validRoleIri).toOption.get.value == validRoleIri)
    }
  )
}
