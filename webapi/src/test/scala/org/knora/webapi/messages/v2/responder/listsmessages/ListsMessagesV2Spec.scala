/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.listsmessages

import zio.test.*

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
 * Tests for the new helpers on [[ListResponderResponseV2]] that drive
 * `?allLanguages=true` emission for `rdfs:label` and `rdfs:comment`.
 *
 * Decisions defended:
 *   - D2 (omission): an empty / untagged-only sequence yields `None` in all-languages mode.
 *   - D3 (sort): output array is sorted by BCP-47 tag, regardless of input order.
 *   - D5 (skip untagged): `PlainStringLiteralV2` entries are dropped.
 *   - D6 (last-wins dedup): repeated tags collapse via `.toMap`, last entry wins.
 *   - D8 (legacy unchanged): with `allLanguages=false` the helper preserves the
 *     single-string shape and language selection of the legacy code path.
 */
object ListsMessagesV2Spec extends ZIOSpecDefault {

  // Expose protected helpers for unit testing.
  private object Helpers extends ListResponderResponseV2 {
    def callStringLiteralsToLangMap(seq: StringLiteralSequenceV2): Map[String, String] =
      stringLiteralsToLangMap(seq)

    def callLabelOrCommentJson(
      seq: StringLiteralSequenceV2,
      allLanguages: Boolean,
      userLang: String,
      fallbackLang: String,
    ) = labelOrCommentJson(seq, allLanguages, userLang, fallbackLang)
  }

  private def lit(value: String, lang: LanguageCode): StringLiteralV2 =
    LanguageTaggedStringLiteralV2(value, lang)

  private def plain(value: String): StringLiteralV2 = PlainStringLiteralV2(value)

  private def seqOf(literals: StringLiteralV2*): StringLiteralSequenceV2 =
    StringLiteralSequenceV2(literals.toVector)

  def spec: Spec[Any, Any] = suite("ListsMessagesV2Spec")(
    stringLiteralsToLangMapTests,
    labelOrCommentJsonAllLanguagesTests,
    labelOrCommentJsonLegacyTests,
  )

  private val stringLiteralsToLangMapTests = suite("stringLiteralsToLangMap")(
    test("skips PlainStringLiteralV2 and applies last-wins dedup on repeated tags (D5, D6)") {
      // Mixed input: two German entries (de wins last), one English, one untagged.
      val input = seqOf(
        lit("a", LanguageCode.DE),
        lit("b", LanguageCode.EN),
        plain("c"),
        lit("a2", LanguageCode.DE),
      )
      val actual = Helpers.callStringLiteralsToLangMap(input)
      assertTrue(
        actual == Map("de" -> "a2", "en" -> "b"),
      )
    },
    test("empty sequence yields empty map") {
      val actual = Helpers.callStringLiteralsToLangMap(StringLiteralSequenceV2.empty)
      assertTrue(actual.isEmpty)
    },
    test("untagged-only sequence yields empty map (D5)") {
      val input  = seqOf(plain("only-untagged"))
      val actual = Helpers.callStringLiteralsToLangMap(input)
      assertTrue(actual.isEmpty)
    },
  )

  private val labelOrCommentJsonAllLanguagesTests = suite("labelOrCommentJson (allLanguages=true)")(
    test("non-alphabetical input is sorted by language tag (D3 — defeats Learning #7)") {
      // Input order [fr, de, en] must produce output sorted as [de, en, fr].
      val input = seqOf(
        lit("Bonjour", LanguageCode.FR),
        lit("Hallo", LanguageCode.DE),
        lit("Hello", LanguageCode.EN),
      )
      val actual = Helpers.callLabelOrCommentJson(
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
    test("empty sequence returns None (D2 omission)") {
      val actual = Helpers.callLabelOrCommentJson(
        seq = StringLiteralSequenceV2.empty,
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      assertTrue(actual.isEmpty)
    },
    test("untagged-only sequence returns None (D2 + D5)") {
      val actual = Helpers.callLabelOrCommentJson(
        seq = seqOf(plain("untagged")),
        allLanguages = true,
        userLang = "en",
        fallbackLang = "en",
      )
      assertTrue(actual.isEmpty)
    },
  )

  private val labelOrCommentJsonLegacyTests = suite("labelOrCommentJson (allLanguages=false)")(
    test("returns a single JsonLDString picked by userLang (D8 unchanged)") {
      val input = seqOf(
        lit("Hallo", LanguageCode.DE),
        lit("Hello", LanguageCode.EN),
        lit("Bonjour", LanguageCode.FR),
      )
      val actual = Helpers.callLabelOrCommentJson(
        seq = input,
        allLanguages = false,
        userLang = "de",
        fallbackLang = "en",
      )
      assertTrue(actual.contains(JsonLDString("Hallo")))
    },
    test("falls back to fallbackLang when userLang is missing (D8 unchanged)") {
      val input = seqOf(
        lit("Hello", LanguageCode.EN),
        lit("Bonjour", LanguageCode.FR),
      )
      val actual = Helpers.callLabelOrCommentJson(
        seq = input,
        allLanguages = false,
        userLang = "de",
        fallbackLang = "en",
      )
      assertTrue(actual.contains(JsonLDString("Hello")))
    },
  )
}
