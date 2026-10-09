/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class NegotiationSpec extends ZIOSpecDefault {

  private val JsonLd      = "application/ld+json"
  private val DataCite    = "application/vnd.datacite.datacite+json"
  private val jsonLdUrl   = "https://example.test/dpe/projects/0001/metadata.jsonld"
  private val dataCiteUrl = "https://example.test/dpe/projects/0001/metadata.datacite.json"

  private val candidates = List(Candidate(JsonLd, jsonLdUrl), Candidate(DataCite, dataCiteUrl))

  private def decision(accept: Option[String]): Decision = Negotiation.decide(accept, candidates)
  private def decision(accept: String): Decision         = decision(Some(accept))

  private val jsonld   = Decision.Redirect(jsonLdUrl)
  private val datacite = Decision.Redirect(dataCiteUrl)
  private val html     = Decision.Html

  private def allHtml(accepts: List[String]) =
    assertTrue(accepts.forall(a => decision(a) == html))

  override val spec: Spec[Any, Nothing] = suite("NegotiationSpec")(
    test("a browser and an absent header get the page") {
      assertTrue(
        decision(None) == html,
        decision("*/*") == html,
        decision("text/html,*/*;q=0.8") == html,
      )
    },
    test("a json ld request is redirected") {
      assertTrue(decision(JsonLd) == jsonld)
    },
    test("an rdf accept list lands on the json ld") {
      assertTrue(
        decision("text/turtle, application/rdf+xml, application/n-triples, application/ld+json") == jsonld,
      )
    },
    test("a datacite request is redirected") {
      assertTrue(decision(DataCite) == datacite)
    },
    test("a type nobody offers gets the page") {
      assertTrue(decision("text/turtle") == html)
    },
    test("a neighbouring json or xml type is not an alias") {
      allHtml(List("application/json", "application/xml", "application/*"))
    },
    test("an explicit tie with html gets the page") {
      assertTrue(decision("text/html;q=1, application/ld+json;q=1") == html)
    },
    test("a header asking for nothing or saying nothing gets the page") {
      allHtml(List("*/*;q=0", ";;;", "", "q=0.5", "application/ld+json;q=bogus"))
    },
    test("a bad entry does not cost the good one beside it") {
      assertTrue(
        decision("///;q=nope, application/ld+json") == jsonld,
        decision("application/ld+json, text/html;q=bogus") == jsonld,
      )
    },
    test("htmls quality is the maximum over the ranges covering it") {
      assertTrue(
        decision("text/html;q=0.5, text/*;q=0.9") == html,
        decision("text/html;q=0.5, text/*;q=0.9, application/ld+json;q=0.95") == jsonld,
        decision("text/html;q=0.5, text/*;q=0.9, application/ld+json;q=0.9") == html,
      )
    },
    test("a parameter other than q is ignored") {
      assertTrue(
        decision("text/html;level=1;q=0.7") == html,
        decision("text/html;level=1;q=0.7, application/ld+json;q=0.8") == jsonld,
      )
    },
    test("a missing q counts as one") {
      assertTrue(decision("application/ld+json, text/html;q=0.9") == jsonld)
    },
    test("a repeated q in one entry is read to the end") {
      assertTrue(
        decision("application/ld+json;q=0.1;q=0.9, text/html;q=0.5") == jsonld,
        decision("application/ld+json;q=0.9;q=0.1, text/html;q=0.5") == html,
      )
    },
    test("a q outside the range skips its entry") {
      assertTrue(
        decision("application/ld+json;q=2") == html,
        decision("application/ld+json;q=-1") == html,
      )
    },
    test("media types compare case insensitively") {
      assertTrue(
        decision("APPLICATION/LD+JSON") == jsonld,
        decision("TEXT/HTML, application/ld+json") == html,
      )
    },
    test("the first listed candidate wins a tie between candidates") {
      assertTrue(
        decision(s"$DataCite, $JsonLd") == datacite,
        decision(s"$JsonLd, $DataCite") == jsonld,
      )
    },
    test("a higher q beats the header order") {
      assertTrue(decision(s"$DataCite;q=0.5, $JsonLd;q=0.9") == jsonld)
    },
    test("a header over the byte limit is treated as absent") {
      val header = Iterator.continually(", text/plain").scanLeft(JsonLd)(_ + _).dropWhile(_.length <= 2048).next()
      assertTrue(decision(header) == html)
    },
    test("thousands of media ranges cost bounded work and yield html") {
      assertTrue(decision(List.fill(5000)("text/plain").mkString(",")) == html)
    },
    test("a candidate past the range limit is not read") {
      val header = (List.fill(20)("text/plain") :+ JsonLd).mkString(",")
      assertTrue(header.length <= 2048, decision(header) == html)
    },
    test("an empty candidate list can only yield html") {
      assertTrue(Negotiation.decide(Some(JsonLd), Nil) == html)
    },
    test("a q that only Java would read as a number drops its entry") {
      allHtml(List("application/ld+json;q=1f", "application/ld+json;q=1d", "application/ld+json;q=0x1p0")) &&
      assertTrue(decision("application/ld+json;q= 1") == jsonld)
    },
    test("a q with a stray leading character other than whitespace drops its entry") {
      allHtml(List("application/ld+json;q=\u00001", "application/ld+json;q=1_0"))
    },
    test("a non ascii case fold does not match") {
      assertTrue(
        decision("application/ld+json".replace("js", "ſs")) == html,
        decision("APPLICATION/LD+JSON".replace("L", "K")) == html,
        decision("Kext/html, application/ld+json") == jsonld,
      )
    },
    test("a header of exactly 2048 bytes is read and 2049 bytes is absent") {
      // "é" is 2 bytes and 1 char, so byte length and char length differ.
      val base    = JsonLd + ", x/"
      val padding = (2048 - base.length) / 2
      val exact   = base + "é" * padding + "x" * ((2048 - base.length) % 2)
      val oneOver = exact + "x"
      assertTrue(
        exact.getBytes("UTF-8").length == 2048,
        exact.length < 2048,
        oneOver.getBytes("UTF-8").length == 2049,
        decision(exact) == jsonld,
        decision(oneOver) == html,
      )
    },
  )
}
