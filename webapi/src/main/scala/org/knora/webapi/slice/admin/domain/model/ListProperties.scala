/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.common.IntValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

object ListProperties {

  final case class ListIri private (value: String) extends StringValue

  object ListIri extends StringValueCompanion[ListIri] {

    given JsonCodec[ListIri]                            = ZioJsonCodec.stringCodec(ListIri.from)
    given Codec[String, ListIri, CodecFormat.TextPlain] = TapirCodec.stringCodec(ListIri.from)

    /**
     * Explanation of the list IRI regex:
     * `^` asserts the start of the string.
     * `http://rdfh\.ch/lists/` matches the specified prefix.
     * `p{XDigit}{4}/` matches project shortcode built with 4 hexadecimal digits.
     * `[a-zA-Z0-9_-]{4,84}` matches any alphanumeric character, hyphen, or underscore between 4 and 84 times.
     * TODO: 84 is max length found on production DBs - 25 records in 60-84 range - projects 0801, 0101, 0105
     * `$` asserts the end of the string.
     */
    private val listIriRegEx = """^http://rdfh\.ch/lists/\p{XDigit}{4}/[a-zA-Z0-9_-]{4,84}$""".r

    private def isListIriValid(iri: String) = Iri.isIri(iri) && listIriRegEx.matches(iri)

    def from(value: String): Either[String, ListIri] = value match {
      case _ if value.isEmpty         => Left("List IRI cannot be empty.")
      case _ if isListIriValid(value) => Right(ListIri(value))
      case _                          => Left("List IRI is invalid.")
    }

    /**
     * Creates a new [[ListIri]] within a [[KnoraProject]] generating a new base 64 encoded uuid.
     *
     * @param project The reference to the [[KnoraProject]].
     * @return a new [[ListIri]].
     */
    def makeNew(project: KnoraProject): ListIri = {
      val uuid = UuidUtil.makeRandomBase64EncodedUuid
      unsafeFrom(s"http://rdfh.ch/lists/${project.shortcode.value}/$uuid")
    }
  }

  final case class ListName private (value: String) extends StringValue
  object ListName                                   extends StringValueCompanion[ListName] {
    def from(value: String): Either[String, ListName] =
      if value.isEmpty then Left("List name cannot be empty.")
      else if hasLineBreaks(value) then Left("List name is invalid.")
      else Right(ListName(value))
  }

  private def hasLineBreaks: String => Boolean = str => str.contains('\r') || str.contains('\n')

  final case class Position private (value: Int) extends IntValue {
    def >(other: Int): Boolean = value > other

    def <(other: Int): Boolean = value < other
  }

  object Position extends IntValueCompanion[Position] {
    def from(value: Int): Either[String, Position] =
      if (value >= -1) { Right(Position(value)) }
      else { Left("Invalid position value is given. Position should be either a positive value, 0 or -1.") }
  }

  final case class Labels private (value: Seq[StringLiteralV2]) extends Value[Seq[StringLiteralV2]]

  object Labels extends WithFrom[Seq[StringLiteralV2], Labels] {
    def from(values: Seq[StringLiteralV2]): Either[String, Labels] =
      if values.isEmpty then Left("At least one label needs to be supplied.")
      else if values.exists(lit => hasLineBreaks(lit.value)) then Left("Invalid label.")
      else Right(Labels(values))
  }

  final case class Comments private (value: Seq[StringLiteralV2]) extends Value[Seq[StringLiteralV2]]

  object Comments extends WithFrom[Seq[StringLiteralV2], Comments] {
    def from(values: Seq[StringLiteralV2]): Either[String, Comments] =
      if values.isEmpty then Left("At least one comment needs to be supplied.")
      else if values.exists(lit => hasLineBreaks(lit.value)) then Left("Invalid comment.")
      else Right(Comments(values))
  }
}
