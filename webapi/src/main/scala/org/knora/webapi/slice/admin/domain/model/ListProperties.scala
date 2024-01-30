/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import dsp.valueobjects.{Iri, V2}
import org.knora.webapi.slice.common.Value.{IntValue, StringValue}
import org.knora.webapi.slice.common.{IntValueCompanion, StringValueCompanion, Value, WithFrom}
import zio.prelude.Validation

object ListProperties {

  /**
   * List ListName value object.
   */
  final case class ListName private (value: String) extends AnyVal with StringValue
  object ListName extends StringValueCompanion[ListName] {
    def from(value: String): Either[String, ListName] =
      if (value.isEmpty) Left("List name cannot be empty.")
      else Iri.toSparqlEncodedString(value).toRight("List name is invalid.").map(ListName.apply)
  }

  /**
   * List Position value object.
   */
  final case class Position private (value: Int) extends AnyVal with IntValue

  object Position extends IntValueCompanion[Position] {
    def from(value: Int): Either[String, Position] =
      if (value >= -1) { Right(Position(value)) }
      else { Left("Invalid position value is given. Position should be either a positive value, 0 or -1.") }
  }

  /**
   * List Labels value object.
   */
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

  /**
   * List Comments value object.
   */
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

object ListErrorMessages {
  val ListCreatePermission     = "A list can only be created by the project or system administrator."
  val ListNodeCreatePermission = "A list node can only be created by the project or system administrator."
  val ListChangePermission     = "A list can only be changed by the project or system administrator."
}
