/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.*
import zio.test.*

import java.net.URI

import org.knora.webapi.slice.admin.domain.model.IsDaschRecommended
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri

object LicenseRepositorySpec extends ZIOSpecDefault {
  private val repo = ZIO.serviceWithZIO[LicenseRepo]
  val spec         = suite("LicenseRepository")(
    test("findAll returns nine supported licenses") {
      repo(_.findAll()).map(actual => assertTrue(actual.size == 14))
    },
    test("should find license by id") {
      for {
        actual <- repo(_.findById(LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0")))
      } yield {
        assertTrue(
          actual.contains(
            License.unsafeFrom(
              LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0"),
              URI.create("https://creativecommons.org/licenses/by/4.0/"),
              "CC BY 4.0",
              IsDaschRecommended.Yes,
            ),
          ),
        )
      }
    },
    test("should not find license by unknown id") {
      repo(_.findById(LicenseIri.makeNew)).map(actual => assertTrue(actual.isEmpty))
    },
  ).provide(LicenseRepo.layer)
}
