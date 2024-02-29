/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral
import zio.json.*

object V2 {

  /**
   * Represents a string with language iso. Allows sorting inside collections by value.
   *
   * @param value    the string value.
   * @param language the language iso.
   */
  case class StringLiteralV2(value: String, language: Option[String]) {
    def toRdfLiteral: StringLiteral =
      language.map(Rdf.literalOfLanguage(value, _)).getOrElse(Rdf.literalOf(value))
  }

  object StringLiteralV2 {
    implicit val codec: JsonCodec[StringLiteralV2] = DeriveJsonCodec.gen[StringLiteralV2]
  }
}
