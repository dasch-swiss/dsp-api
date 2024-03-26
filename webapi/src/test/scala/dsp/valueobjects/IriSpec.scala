/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test._

import dsp.errors.ValidationException
import dsp.valueobjects.Iri._

/**
 * This spec is used to test the [[Iri]] value objects creation.
 */
object IriSpec extends ZIOSpecDefault {
  private val uuidVersion3  = "cCmdcpn2MO211YYOplR1hQ"
  private val supportedUuid = "jDEEitJESRi3pDaDjjQ1WQ"

  private val invalidIri = "Invalid IRI"

  def spec: Spec[Any, Throwable] = uuidTest + roleIriTest

  private val uuidTest = suite("IriSpec - Base64Uuid")(
    test("pass an empty value and return an error") {
      assertTrue(Base64Uuid.make("") == Validation.fail(ValidationException(IriErrorMessages.UuidMissing)))
    },
    test("pass an invalid UUID and return an error") {
      assertTrue(
        Base64Uuid.make(invalidIri) == Validation.fail(ValidationException(IriErrorMessages.UuidInvalid(invalidIri))),
      )
    },
    test("pass an valid UUID, which has not supported version 3") {
      assertTrue(
        Base64Uuid.make(uuidVersion3) == Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid)),
      )
    },
    test("pass valid UUID and successfully create value object") {
      (for {
        uuid <- Base64Uuid.make(supportedUuid)
      } yield assertTrue(uuid.value == supportedUuid)).toZIO
    },
  )

  private val roleIriTest = suite("RoleIri should")(
    test("not be created from an empty value") {
      assertTrue(RoleIri.from("") == Left("Role IRI cannot be empty."))
    },
    test("be created from a valid value") {
      val validIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/roles/40-characters-iri-for-testing-purposes-1",
          "http://rdfh.ch/roles/ZPKPVh8yQs6F7Oyukb8WIQ",
          "http://rdfh.ch/roles/1234",
        ),
      )
      check(validIris)(i => assertTrue(RoleIri.from(i).isRight))
    },
    test("not be created from an invalid value") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/roles/123",
          "http://rdfh.ch/roles/41-characters-iri-for-testing-purposes-12",
          "http://rdfh.ch/roles/(DEEitJESRi3pDaDjjQ1WQ",
        ),
      )
      check(invalidIris)(i => assertTrue(RoleIri.from(i) == Left(s"Role IRI is invalid.")))
    },
  )
}
