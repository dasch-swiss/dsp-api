/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

import java.net.URI

object LegalInfoModelSpec extends ZIOSpecDefault {

  private val authorshipSuite = suite("Authorship")(
    test("pass a valid object and successfully create value object") {
      val validAuthorship = "Jane Doe"
      assertTrue(Authorship.from(validAuthorship).map(_.value).contains(validAuthorship))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = "Jane \n Doe"
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship must not contain line breaks."))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = "a" * 1001
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship must be maximum 1000 characters long."))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = ""
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship cannot be empty."))
    },
  )

  private val licenseIriSuite = suite("LicenseIri")(
    test("pass a valid object and successfully create value object") {
      val validIri = "http://rdfh.ch/licenses/i6xBpZn4RVOdOIyTezEumw"
      assertTrue(LicenseIri.from(validIri).map(_.value).contains(validIri))
    },
    test("pass an invalid object and return an error") {
      val invalidIri = "http://rdfh.ch/licenses/invalid"
      assertTrue(LicenseIri.from(invalidIri) == Left("Invalid license IRI"))
    },
    test("make new returns a valid IRI") {
      val newIri = LicenseIri.makeNew
      assertTrue(LicenseIri.from(newIri.value).contains(newIri))
    },
  )

  private val licenseSuite = suiteAll("License") {
    val validIri   = LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0")
    val validUri   = URI.create("https://creativecommons.org/licenses/by/4.0/")
    val invalidUri = URI.create("./invalid")
    val validLabel = "CC BY 4.0"

    test("pass a valid object and successfully create value object") {
      val actual = License.from(validIri, validUri, validLabel)
      assertTrue(
        actual.map(_.id).contains(validIri),
        actual.map(_.uri).contains(validUri),
        actual.map(_.labelEn).contains(validLabel),
      )
    }
    test("pass a relative URI and return an error") {
      val actual = License.from(validIri, invalidUri, validLabel)
      assertTrue(actual == Left("License: URI must be absolute"))
    }
    test("pass an empty label and return an error") {
      val actual = License.from(validIri, validUri, "")
      assertTrue(actual == Left("License: Label en cannot be empty"))
    }
    test("pass a new line in labelEn and return an error") {
      val actual = License.from(validIri, validUri, "some\nlabel")
      assertTrue(actual == Left("License: Label en must not contain line breaks"))
    }
    test("pass a too long labelEn and return an error") {
      val actual = License.from(validIri, validUri, "s" * 256)
      assertTrue(actual == Left("License: Label en must be maximum 255 characters long"))
    }
    test("errors are combined") {
      val actual = License.from(validIri, invalidUri, "s\n" * 256)
      assertTrue(
        actual == Left(
          "License: URI must be absolute, Label en must be maximum 255 characters long; must not contain line breaks",
        ),
      )
    }
    test("pass in a predefined invalid license") {
      val actual = License.from(
        LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0"),
        URI.create("http://rdfh.ch/licenses/cc-by-4.0"),
        "Wrong label",
      )
      assertTrue(actual.isLeft)
    }
  }

  val spec: Spec[Any, Nothing] = suite("Copyright And Licenses Model")(
    authorshipSuite,
    licenseIriSuite,
    licenseSuite,
  )
}
