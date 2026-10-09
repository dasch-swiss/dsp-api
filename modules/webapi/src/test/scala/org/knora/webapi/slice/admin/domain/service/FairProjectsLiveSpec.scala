/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.fair.ports.FairLicence

@RunWith(classOf[DspZTestJUnitRunner])
class FairProjectsLiveSpec extends ZIOSpecDefault {

  private def licence(iri: String) = FairProjectsLive.toFairLicence(iri)

  val spec = suite("FairProjectsLive.toFairLicence")(
    test("maps a Creative Commons licence to its URI and label") {
      assertTrue(
        licence(LicenseIri.CC_BY_4_0.value) ==
          Some(FairLicence.Licence("https://creativecommons.org/licenses/by/4.0/", "CC BY 4.0")),
      )
    },
    test("maps the placeholder to Placeholder") {
      assertTrue(licence(LicenseIri.PLACEHOLDER.value) == Some(FairLicence.Placeholder))
    },
    test("maps unknown, AI-generated and public domain to declarations") {
      assertTrue(
        licence(LicenseIri.UNKNOWN.value).exists(_.isInstanceOf[FairLicence.Declaration]),
        licence(LicenseIri.AI_GENERATED.value).exists(_.isInstanceOf[FairLicence.Declaration]),
        licence(LicenseIri.PUBLIC_DOMAIN.value).exists(_.isInstanceOf[FairLicence.Declaration]),
      )
    },
    test("returns None for a custom-shaped IRI that is not built in") {
      assertTrue(licence("http://rdfh.ch/licenses/abcdefghijklmnopqrstuv").isEmpty)
    },
    test("returns None for garbage") {
      assertTrue(licence("not a licence").isEmpty)
    },
  )
}
