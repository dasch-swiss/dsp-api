/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}


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
