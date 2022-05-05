/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

/**
 * Tests [[StringLiteralSequenceV2]].
 */
class StringLiteralSequenceV2Spec extends CoreSpec() {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val literalSeq: StringLiteralSequenceV2 = StringLiteralSequenceV2(
    Vector(
      StringLiteralV2(
        value = "Film und Foto",
        language = Some("de")
      ),
      StringLiteralV2(
        value = "Film and Photo",
        language = Some("en")
      ),
      StringLiteralV2(
        value = "Film e Fotografia",
        language = Some("it")
      )
    )
  )

  private val emptySeq = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])

  "The StringLiteralSequenceV2 case class" should {

    "return the German literal of a given StringLiteralSequenceV2" in {

      val germanLiteral: Option[String] = literalSeq.getPreferredLanguage("de", "en")

      assert(germanLiteral.nonEmpty && germanLiteral.get == "Film und Foto")

    }

    "return the Italian literal of a given StringLiteralSequenceV2" in {

      val italianLiteral: Option[String] = literalSeq.getPreferredLanguage("it", "en")

      assert(italianLiteral.nonEmpty && italianLiteral.get == "Film e Fotografia")

    }

    "return the literal in the fallback language of a given StringLiteralSequenceV2" in {

      // no Spanish literal is available, the Ebglish version should be returned
      val defaultLanguageLiteral: Option[String] = literalSeq.getPreferredLanguage("es", "en")

      assert(defaultLanguageLiteral.nonEmpty && defaultLanguageLiteral.get == "Film and Photo")

    }

    "return a literal for a given StringLiteralSequenceV2 if neither of the requested languages exists" in {

      // the behaviour is deterministic because string literals are sorted by their language tag
      val anyLanguageLiteral: Option[String] = literalSeq.getPreferredLanguage("es", "ru")

      assert(anyLanguageLiteral.nonEmpty && anyLanguageLiteral.get == "Film und Foto")

    }

    "request a literal from an empty list" in {

      val noLiteral: Option[String] = emptySeq.getPreferredLanguage("en", "de")

      assert(noLiteral.isEmpty)

    }

  }

}
