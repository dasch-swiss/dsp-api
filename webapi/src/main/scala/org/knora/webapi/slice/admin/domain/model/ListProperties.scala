/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.prelude.Validation

import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.isListIri
import dsp.valueobjects.Iri.validateAndEscapeIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import dsp.valueobjects.V2
import org.knora.webapi.messages.StringFormatter.IriDomain
import org.knora.webapi.slice.common.IntValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

object ListProperties {

  final case class ListIri private (value: String) extends AnyVal with StringValue

  object ListIri extends StringValueCompanion[ListIri] {

    /**
     * Creates a new [[ListIri]] within a [[KnoraProject]] generating a new base 64 encoded uuid.
     *
     * @param project The reference to the [[KnoraProject]].
     * @return a new [[ListIri]].
     */
    def makeNew(project: KnoraProject): ListIri = {
      val uuid = UuidUtil.makeRandomBase64EncodedUuid
      unsafeFrom(s"http://$IriDomain/lists/${project.shortcode.value}/$uuid")
    }

    def from(value: String): Either[String, ListIri] =
      if (value.isEmpty) Left("List IRI cannot be empty.")
      else {
        val isUuid: Boolean = UuidUtil.hasValidLength(value.split("/").last)

        if (!isListIri(value))
          Left("List IRI is invalid")
        else if (isUuid && !UuidUtil.hasSupportedVersion(value))
          Left(IriErrorMessages.UuidVersionInvalid)
        else
          validateAndEscapeIri(value)
            .mapError(_ => "List IRI is invalid")
            .map(ListIri.apply)
            .toEitherWith(_.head)
      }
  }

  final case class ListName private (value: String) extends AnyVal with StringValue
  object ListName extends StringValueCompanion[ListName] {
    def from(value: String): Either[String, ListName] =
      if (value.isEmpty) Left("List name cannot be empty.")
      else Iri.toSparqlEncodedString(value).toRight("List name is invalid.").map(ListName.apply)
  }

  final case class Position private (value: Int) extends AnyVal with IntValue {
    def >(other: Int): Boolean = value > other

    def <(other: Int): Boolean = value < other
  }

  object Position extends IntValueCompanion[Position] {
    def from(value: Int): Either[String, Position] =
      if (value >= -1) { Right(Position(value)) }
      else { Left("Invalid position value is given. Position should be either a positive value, 0 or -1.") }
  }

  final case class Labels private (value: Seq[V2.StringLiteralV2]) extends Value[Seq[V2.StringLiteralV2]]

  object Labels extends WithFrom[Seq[V2.StringLiteralV2], Labels] {
    def from(value: Seq[V2.StringLiteralV2]): Either[String, Labels] =
      if (value.isEmpty) Left("At least one label needs to be supplied.")
      else {
        val validatedLabels = value.map(l =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(l.value))
            .mapError(_ => "Invalid label.")
            .map(V2.StringLiteralV2(_, l.language))
        )
        Validation.validateAll(validatedLabels).map(Labels.apply).toEitherWith(_.head)
      }
  }

  final case class Comments private (value: Seq[V2.StringLiteralV2]) extends Value[Seq[V2.StringLiteralV2]]

  object Comments extends WithFrom[Seq[V2.StringLiteralV2], Comments] {
    def from(value: Seq[V2.StringLiteralV2]): Either[String, Comments] =
      if (value.isEmpty) Left("At least one comment needs to be supplied.")
      else {
        val validatedComments = value.map(c =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(c.value))
            .mapError(_ => "Invalid comment.")
            .map(s => V2.StringLiteralV2(s, c.language))
        )
        Validation.validateAll(validatedComments).map(Comments.apply).toEitherWith(_.head)
      }
  }
}
