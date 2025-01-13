/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object CopyrightAndLicensesModelSpec extends ZIOSpecDefault {

  private val licenseUriTest = suite("LicenseUri")(
    test("pass a valid object and successfully create value object") {
      val validUri = "https://www.apache.org/licenses/LICENSE-2.0.html"
      assertTrue(LicenseUri.from(validUri).map(_.value).contains(validUri))
    },
    test("pass an invalid object and return an error") {
      val invalidUri = "not a uri"
      assertTrue(LicenseUri.from(invalidUri) == Left("License URI is not a valid URI."))
    },
  )

  val spec = suite("Copyright And Licenses Model")(
    licenseUriTest,
  )
}
