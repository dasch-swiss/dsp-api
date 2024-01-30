/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.V2
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

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
  final case class Position private (value: Int)
  object Position { self =>
    def make(value: Int): Validation[BadRequestException, Position] =
      if (value < -1) Validation.fail(BadRequestException(ListErrorMessages.InvalidPosition))
      else Validation.succeed(Position(value))

    def make(value: Option[Int]): Validation[BadRequestException, Option[Position]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * List Labels value object.
   */
  final case class Labels private (value: Seq[V2.StringLiteralV2])
  object Labels { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[BadRequestException, Labels] =
      if (value.isEmpty) Validation.fail(BadRequestException(ListErrorMessages.LabelsMissing))
      else {
        val validatedLabels = value.map(l =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(l.value))
            .mapError(_ => BadRequestException(ListErrorMessages.LabelsInvalid))
            .map(s => V2.StringLiteralV2(s, l.language))
        )
        Validation.validateAll(validatedLabels).map(Labels.apply)
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[BadRequestException, Option[Labels]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * List Comments value object.
   */
  final case class Comments private (value: Seq[V2.StringLiteralV2])
  object Comments { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[BadRequestException, Comments] =
      if (value.isEmpty) Validation.fail(BadRequestException(ListErrorMessages.CommentsMissing))
      else {
        val validatedComments = value.map(c =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(c.value))
            .mapError(_ => BadRequestException(ListErrorMessages.CommentsInvalid))
            .map(s => V2.StringLiteralV2(s, c.language))
        )
        Validation.validateAll(validatedComments).map(Comments.apply)
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[BadRequestException, Option[Comments]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object ListErrorMessages {
  val LabelsMissing            = "At least one label needs to be supplied."
  val LabelsInvalid            = "Invalid label."
  val CommentsMissing          = "At least one comment needs to be supplied."
  val CommentsInvalid          = "Invalid comment."
  val ListCreatePermission     = "A list can only be created by the project or system administrator."
  val ListNodeCreatePermission = "A list node can only be created by the project or system administrator."
  val ListChangePermission     = "A list can only be changed by the project or system administrator."
  val InvalidPosition          = "Invalid position value is given. Position should be either a positive value, 0 or -1."
}
