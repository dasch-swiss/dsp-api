/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.net.URI

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.slice.admin.domain.model.IsDaschRecommended
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri

@RunWith(classOf[DspZTestJUnitRunner])
class LicenseRepositorySpec extends ZIOSpecDefault {
  private val repo = ZIO.serviceWithZIO[LicenseRepo]

  private def configLayer(allowPlaceholder: Boolean): ULayer[Unit] =
    TestAppConfig.layer("app.features.allow-placeholder" -> allowPlaceholder)

  private val placeholderAllowedSuite = suite("with allow-placeholder = true")(
    test("findAll returns all supported licenses including the placeholder") {
      repo(_.findAll()).map(actual =>
        assertTrue(actual.size == License.BUILT_IN.size, actual.map(_.id).contains(LicenseIri.PLACEHOLDER)),
      )
    },
    test("findById should return the license for a known IRI") {
      for {
        actual <- repo(_.findById(LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0")))
      } yield assertTrue(
        actual.contains(
          License.unsafeFrom(
            LicenseIri.unsafeFrom("http://rdfh.ch/licenses/cc-by-4.0"),
            URI.create("https://creativecommons.org/licenses/by/4.0/"),
            "CC BY 4.0",
            IsDaschRecommended.Yes,
          ),
        ),
      )
    },
    test("findById should return the placeholder license") {
      repo(_.findById(LicenseIri.PLACEHOLDER)).map(actual => assertTrue(actual.exists(_.id == LicenseIri.PLACEHOLDER)))
    },
    test("findById should return None for an unknown IRI") {
      repo(_.findById(LicenseIri.makeNew)).map(actual => assertTrue(actual.isEmpty))
    },
  ).provide(configLayer(true) >>> LicenseRepo.layer)

  private val placeholderDisabledSuite = suite("with allow-placeholder = false")(
    test("findAll should omit the placeholder license") {
      repo(_.findAll()).map(actual =>
        assertTrue(
          actual.size == License.BUILT_IN.size - 1,
          !actual.map(_.id).contains(LicenseIri.PLACEHOLDER),
        ),
      )
    },
    test("findById should return None for the placeholder IRI") {
      repo(_.findById(LicenseIri.PLACEHOLDER)).map(actual => assertTrue(actual.isEmpty))
    },
    test("findRecommendedLicenses should omit the placeholder license") {
      repo(_.findRecommendedLicenses()).map(actual => assertTrue(!actual.map(_.id).contains(LicenseIri.PLACEHOLDER)))
    },
  ).provide(configLayer(false) >>> LicenseRepo.layer)

  val spec = suite("LicenseRepository")(placeholderAllowedSuite, placeholderDisabledSuite)
}
