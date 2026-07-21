/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.common.domain.LanguageCode.*

/**
 * Tests [[StringLiteralSequenceV2]].
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StringLiteralSequenceV2Spec extends ZIOSpecDefault {

  private val literalSeq: StringLiteralSequenceV2 = StringLiteralSequenceV2(
    Vector(
      StringLiteralV2.from("Film und Foto", DE),
      StringLiteralV2.from("Film and Photo", EN),
      StringLiteralV2.from("Film e Fotografia", IT),
    ),
  )

  private val emptySeq = StringLiteralSequenceV2.empty

  val spec: Spec[Any, Nothing] = suite("The StringLiteralSequenceV2 case class")(
    test("return the German literal of a given StringLiteralSequenceV2") {
      val germanLiteral: Option[String] = literalSeq.getPreferredLanguage("de", "en")
      assertTrue(germanLiteral.contains("Film und Foto"))
    },
    test("return the Italian literal of a given StringLiteralSequenceV2") {
      val italianLiteral: Option[String] = literalSeq.getPreferredLanguage("it", "en")
      assertTrue(italianLiteral.contains("Film e Fotografia"))
    },
    test("return the literal in the fallback language of a given StringLiteralSequenceV2") {
      // no Spanish literal is available, the English version should be returned
      val defaultLanguageLiteral: Option[String] = literalSeq.getPreferredLanguage("es", "en")
      assertTrue(defaultLanguageLiteral.contains("Film and Photo"))
    },
    test("return a literal for a given StringLiteralSequenceV2 if neither of the requested languages exists") {
      // the behaviour is deterministic because string literals are sorted by their language tag
      val anyLanguageLiteral: Option[String] = literalSeq.getPreferredLanguage("es", "ru")
      assertTrue(anyLanguageLiteral.contains("Film und Foto"))
    },
    test("request a literal from an empty list") {
      val noLiteral: Option[String] = emptySeq.getPreferredLanguage("en", "de")
      assertTrue(noLiteral.isEmpty)
    },
  )
}
