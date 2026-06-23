/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.listsmessages

import zio.test.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.JsonLDArray
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.slice.common.domain.LanguageCode

/**
 * Tests the language-handling helpers on [[ListResponderResponseV2]] that drive
 * `?allLanguages=true` emission for `rdfs:label` and `rdfs:comment`.
 *
 * Behaviours covered:
 *   - all-languages mode omits the field when no language-tagged literals exist
 *   - all-languages output is sorted by BCP-47 tag
 *   - untagged (`PlainStringLiteralV2`) entries are skipped
 *   - repeated tags collapse via `.toMap`, last entry wins
 *   - legacy single-string mode preserves the existing language-selection behaviour
 *   - `labelEntry` / `commentEntry` attach the correct predicate IRI
 */
object ListsMessagesV2Spec extends ZIOSpecDefault {

  // The helpers under test are `private[listsmessages]` on the trait; instantiate
  // it once here so the spec can drive them directly without an access shim.
  private val sut: ListResponderResponseV2 = new ListResponderResponseV2 {}

  private def lit(value: String, lang: LanguageCode): StringLiteralV2 =
    LanguageTaggedStringLiteralV2(value, lang)

  private def plain(value: String): StringLiteralV2 = PlainStringLiteralV2(value)

  private def seqOf(literals: StringLiteralV2*): StringLiteralSequenceV2 =
    StringLiteralSequenceV2(literals.toVector)

  def spec: Spec[Any, Any] = suite("lists v2 label/comment emission helpers")(
    labelOrCommentJsonAllLanguagesTests,
    labelOrCommentJsonLegacyTests,
    entryHelperTests,
  )

  private val labelOrCommentJsonAllLanguagesTests = suite("labelOrCommentJson (allLanguages=true)")(
    test("non-alphabetical input is sorted by language tag") {
      val input = seqOf(
        lit("Bonjour", LanguageCode.FR),
        lit("Hallo", LanguageCode.DE),
        lit("Hello", LanguageCode.EN),
      )
      val actual = sut.labelOrCommentJson(
        seq = input,
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      val expected = JsonLDArray(
        Seq(
          JsonLDObject(
            Map(JsonLDKeywords.VALUE -> JsonLDString("Hallo"), JsonLDKeywords.LANGUAGE -> JsonLDString("de")),
          ),
          JsonLDObject(
            Map(JsonLDKeywords.VALUE -> JsonLDString("Hello"), JsonLDKeywords.LANGUAGE -> JsonLDString("en")),
          ),
          JsonLDObject(
            Map(JsonLDKeywords.VALUE -> JsonLDString("Bonjour"), JsonLDKeywords.LANGUAGE -> JsonLDString("fr")),
          ),
        ),
      )
      assertTrue(actual.contains(expected))
    },
    test("untagged literals are skipped and last-wins applies on repeated tags") {
      val input = seqOf(
        lit("a", LanguageCode.DE),
        lit("b", LanguageCode.EN),
        plain("c"),
        lit("a2", LanguageCode.DE),
      )
      val actual = sut.labelOrCommentJson(
        seq = input,
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      val expected = JsonLDArray(
        Seq(
          JsonLDObject(
            Map(JsonLDKeywords.VALUE -> JsonLDString("a2"), JsonLDKeywords.LANGUAGE -> JsonLDString("de")),
          ),
          JsonLDObject(
            Map(JsonLDKeywords.VALUE -> JsonLDString("b"), JsonLDKeywords.LANGUAGE -> JsonLDString("en")),
          ),
        ),
      )
      assertTrue(actual.contains(expected))
    },
    test("empty sequence returns None (field is omitted)") {
      val actual = sut.labelOrCommentJson(
        seq = StringLiteralSequenceV2.empty,
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      assertTrue(actual.isEmpty)
    },
    test("untagged-only sequence returns None") {
      val actual = sut.labelOrCommentJson(
        seq = seqOf(plain("untagged")),
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      assertTrue(actual.isEmpty)
    },
  )

  private val labelOrCommentJsonLegacyTests = suite("labelOrCommentJson (allLanguages=false)")(
    test("returns a single JsonLDString picked by userLang") {
      val input = seqOf(
        lit("Hallo", LanguageCode.DE),
        lit("Hello", LanguageCode.EN),
        lit("Bonjour", LanguageCode.FR),
      )
      val actual = sut.labelOrCommentJson(
        seq = input,
        allLanguages = false,
        userLang = "de",
        fallbackLang = "en",
      )
      assertTrue(actual.contains(JsonLDString("Hallo")))
    },
    test("falls back to fallbackLang when userLang is missing") {
      val input = seqOf(
        lit("Hello", LanguageCode.EN),
        lit("Bonjour", LanguageCode.FR),
      )
      val actual = sut.labelOrCommentJson(
        seq = input,
        allLanguages = false,
        userLang = "de",
        fallbackLang = "en",
      )
      assertTrue(actual.contains(JsonLDString("Hello")))
    },
  )

  private val entryHelperTests = suite("labelEntry / commentEntry")(
    test("labelEntry attaches rdfs:label as the key") {
      val input  = seqOf(lit("Hello", LanguageCode.EN))
      val actual = sut.labelEntry(input, allLanguages = false, userLang = "en", fallbackLang = "en")
      assertTrue(actual.map(_._1).contains(OntologyConstants.Rdfs.Label))
    },
    test("commentEntry attaches rdfs:comment as the key") {
      val input  = seqOf(lit("note", LanguageCode.EN))
      val actual = sut.commentEntry(input, allLanguages = false, userLang = "en", fallbackLang = "en")
      assertTrue(actual.map(_._1).contains(OntologyConstants.Rdfs.Comment))
    },
    test("labelEntry returns None when no value would be emitted") {
      val actual = sut.labelEntry(StringLiteralSequenceV2.empty, allLanguages = true, "en", "en")
      assertTrue(actual.isEmpty)
    },
    test("commentEntry returns None when no value would be emitted") {
      val actual = sut.commentEntry(StringLiteralSequenceV2.empty, allLanguages = true, "en", "en")
      assertTrue(actual.isEmpty)
    },
  )
}
