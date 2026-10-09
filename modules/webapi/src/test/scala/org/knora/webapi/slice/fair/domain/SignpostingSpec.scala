/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.junit.runner.RunWith
import zio.test.*

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

@RunWith(classOf[DspZTestJUnitRunner])
class SignpostingSpec extends ZIOSpecDefault {

  private val ccBy     = "https://creativecommons.org/licenses/by/4.0/"
  private val oaiBase  = "https://oai.example.test/dpe/oai"
  private val oaiCodes = Set(Shortcode.unsafeFrom("0803"))
  private val facts    = ResourceFactsFixture.facts
  private val reps     = FairUrls.representations("https://api.example.test", facts)

  private def links(f: ResourceFacts, codes: Set[Shortcode] = Set.empty) =
    Signposting.landingLinks(f, reps, oaiBase, codes)
  private def hrefs(ls: List[Link], rel: String) = ls.filter(_.rel == rel).map(_.href)
  private val withLicence                        = facts.copy(licence = Some(ResourceLicence(Some(ccBy), "CC BY", Some("CC-BY-4.0"))))

  override val spec: Spec[Any, Nothing] = suite("SignpostingSpec")(
    test("cite-as is the ARK exactly once") {
      assertTrue(hrefs(links(facts), "cite-as") == List(facts.ark))
    },
    test("both type links are emitted, Dataset then AboutPage") {
      assertTrue(hrefs(links(facts), "type") == List("https://schema.org/Dataset", "https://schema.org/AboutPage"))
    },
    test("the header string is RFC 8288") {
      val ls = List(
        Link("https://ark.example.test/ark:/1/2", "cite-as", None),
        Link("https://example.test/r.jsonld", "describedby", Some("application/ld+json")),
      )
      assertTrue(
        Signposting.header(ls) ==
          """<https://ark.example.test/ark:/1/2>; rel="cite-as", <https://example.test/r.jsonld>; rel="describedby"; type="application/ld+json"""",
      )
    },
    test("field syntax in an href cannot forge a second link value") {
      val pid    = """https://ark.example.test/1>; rel="author", <https://evil.test/"""
      val ls     = links(facts.copy(ark = pid))
      val header = Signposting.header(ls)
      assertTrue(
        header.startsWith(
          """<https://ark.example.test/1%3E%3B%20rel=%22author%22%2C%20%3Chttps://evil.test/>; rel="cite-as"""",
        ),
        header.count(_ == '<') == ls.size,
        !header.contains("""rel="author""""),
        ls.head.href == pid,
      )
    },
    test("a newline in an href is left for the HTTP layer") {
      val header = Signposting.header(links(facts.copy(ark = "https://ark.example.test/1\r\nX-Injected: 1")))
      assertTrue(header.contains("\r\n"))
    },
    test("every describedby carries its media type") {
      val ls = links(facts, oaiCodes).filter(_.rel == "describedby")
      assertTrue(
        ls.map(_.mediaType) == List(
          Some(FairUrls.JsonLd),
          Some(FairUrls.DataCite),
          Some("application/xml"),
          Some("application/xml"),
        ),
      )
    },
    test("one license link for a URI licence, none for a declaration or no licence") {
      val declaration = facts.copy(licence = Some(ResourceLicence(None, "All rights reserved", None)))
      assertTrue(
        hrefs(links(withLicence), "license") == List(ccBy),
        hrefs(links(declaration), "license").isEmpty,
        hrefs(links(facts), "license").isEmpty,
      )
    },
    test("there is never an author link") {
      assertTrue(links(withLicence, oaiCodes).forall(_.rel != "author"))
    },
    test("a representation describes its landing page") {
      assertTrue(
        Signposting.header(Signposting.representationLinks("https://example.test/p")) ==
          """<https://example.test/p>; rel="describes"""",
      )
    },
    test("the link elements carry the same relations and targets, in order, as the header") {
      val ls       = links(withLicence, oaiCodes)
      val elements = Signposting.linkElements(ls)
      assertTrue(
        elements.size == ls.size,
        elements.zip(ls).forall { case (e, l) => e.startsWith(s"""<link rel="${l.rel}" href="""") },
        elements.head == s"""<link rel="cite-as" href="${facts.ark}">""",
        elements(3) == s"""<link rel="describedby" href="${reps.head.url}" type="application/ld+json">""",
      )
    },
    test("link elements escape attribute syntax and keep the raw href") {
      val e = Signposting.linkElements(List(Link("""https://x.test/?a=1&b="2"""", "cite-as", None)))
      assertTrue(e == List("""<link rel="cite-as" href="https://x.test/?a=1&amp;b=&quot;2&quot;">"""))
    },
    test("OAI links appear only for listed shortcodes") {
      val other = facts.copy(shortcode = Shortcode.unsafeFrom("081C"))
      assertTrue(
        links(facts).size == 5,
        links(other, oaiCodes).size == 5,
        links(facts, oaiCodes).size == 7,
      )
    },
    test("the OAI identifier is the ARK from ark:/ onward") {
      val oai   = links(facts, oaiCodes).filter(_.mediaType.contains("application/xml"))
      val query = oai.map(l => URI.create(l.href).getRawQuery.split('&').toList)
      val id    = query.map(q =>
        URLDecoder.decode(q.find(_.startsWith("identifier=")).get.stripPrefix("identifier="), StandardCharsets.UTF_8),
      )
      assertTrue(
        id == List.fill(2)("oai:dasch.swiss:ark:/72163/1/0803/lklK7rVuVOmpBZYWrF8o=gh"),
        oai.map(_.href.split("metadataPrefix=").last) == List("oai_datacite", "oai_dc"),
        oai.forall(_.href.startsWith(s"$oaiBase?verb=GetRecord&identifier=oai%3Adasch.swiss%3Aark%3A%2F72163")),
      )
    },
    test("the full link order") {
      assertTrue(
        links(withLicence, oaiCodes).map(_.rel) ==
          List("cite-as", "type", "type", "describedby", "describedby", "describedby", "describedby", "license"),
      )
    },
  )
}
