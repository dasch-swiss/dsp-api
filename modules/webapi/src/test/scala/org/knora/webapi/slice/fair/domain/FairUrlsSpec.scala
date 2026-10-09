/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class FairUrlsSpec extends ZIOSpecDefault {

  private val facts = ResourceFactsFixture.facts

  override val spec: Spec[Any, Nothing] = suite("FairUrlsSpec")(
    test("representations are built from the canonical IRI, JSON-LD first") {
      val expected = List(
        Candidate(FairUrls.JsonLd, "https://api.example.org/fair/resources/0803/0b03b0a6e6be/metadata.jsonld"),
        Candidate(FairUrls.DataCite, "https://api.example.org/fair/resources/0803/0b03b0a6e6be/metadata.datacite.json"),
      )
      assertTrue(FairUrls.representations("https://api.example.org", facts) == expected)
    },
    test("a trailing slash on the base URL is tolerated") {
      assertTrue(
        FairUrls.representations("https://api.example.org/", facts) ==
          FairUrls.representations("https://api.example.org", facts),
      )
    },
  )
}
