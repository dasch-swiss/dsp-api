/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.junit.runner.RunWith
import zio.test.*

import dsp.valueobjects.Iri
import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkUriSpec extends ZIOSpecDefault {

  private val waybackUrls = List(
    "https://web.archive.org/web/20260616062143/https://schneidernzunft-basel.ch/zunftgeschichte/intarsiensaal2",
    "https://web.archive.org/web/20260717023745/https://www.burgroetteln.de/presse/pressemeldungen/pressemeldungen-detailansicht/6495",
    "https://web.archive.org/web/20260224073346/https://www.nb.admin.ch/snl/de/home/ueber-uns/gs.html",
    "https://web.archive.org/web/20250210135735/https://artifiche.com/plakate/binaca-zahnpaste/",
  )

  def spec: Spec[Any, Nothing] = suite("StandoffLinkUri.validate should")(
    test("accept Wayback Machine URLs with // in the path") {
      assertTrue(waybackUrls.forall(url => StandoffLinkUri.validate(url).contains(url)))
    },
    test("leave Iri.validateAndEscapeIri rejecting // in the path") {
      assertTrue(waybackUrls.forall(url => Iri.validateAndEscapeIri(url).toOption.isEmpty))
    },
    test("escape a URL as Iri.validateAndEscapeIri does") {
      val urls = List(
        "https://example.org/a b/c?q=ä&r=1#frag",
        "http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2Fabc",
      )
      assertTrue(urls.forall(url => StandoffLinkUri.validate(url) == Iri.validateAndEscapeIri(url).toOption))
    },
    test("escape a URL with // in the path") {
      assertTrue(
        StandoffLinkUri.validate("https://web.archive.org/web/2026/https://example.org/a b") ==
          Some("https://web.archive.org/web/2026/https://example.org/a%20b"),
      )
    },
    test("reject a URL that is not http or https, or that is malformed") {
      assertTrue(
        StandoffLinkUri.validate("javascript:alert(1)").isEmpty,
        StandoffLinkUri.validate("ftp://example.org/x").isEmpty,
        StandoffLinkUri.validate("https://exa mple.org").isEmpty,
        StandoffLinkUri.validate("not a url").isEmpty,
      )
    },
  )
}
