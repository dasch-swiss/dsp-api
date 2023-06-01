/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import dsp.errors.BadRequestException

object List {

  /**
   * List ListName value object.
   */
  sealed abstract case class ListName private (value: String)
  object ListName { self =>
    def make(value: String): Validation[BadRequestException, ListName] =
      if (value.isEmpty) Validation.fail(BadRequestException(ListErrorMessages.ListNameMissing))
      else
        Validation
          .fromOption(Iri.toSparqlEncodedString(value))
          .mapError(_ => BadRequestException(ListErrorMessages.ListNameInvalid))
          .map(new ListName(_) {})

    def make(value: Option[String]): Validation[BadRequestException, Option[ListName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * List Position value object.
   */
  sealed abstract case class Position private (value: Int)
  object Position { self =>
    def make(value: Int): Validation[BadRequestException, Position] =
      if (value < -1) Validation.fail(BadRequestException(ListErrorMessages.InvalidPosition))
      else Validation.succeed(new Position(value) {})

    def make(value: Option[Int]): Validation[BadRequestException, Option[Position]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * List Labels value object.
   */
  sealed abstract case class Labels private (value: Seq[V2.StringLiteralV2])
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
        Validation.validateAll(validatedLabels).map(new Labels(_) {})
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
  sealed abstract case class Comments private (value: Seq[V2.StringLiteralV2])
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
        Validation.validateAll(validatedComments).map(new Comments(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[BadRequestException, Option[Comments]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object ListErrorMessages {
  val ListNameMissing          = "List name cannot be empty."
  val ListNameInvalid          = "List name is invalid."
  val LabelsMissing            = "At least one label needs to be supplied."
  val LabelsInvalid            = "Invalid label."
  val CommentsMissing          = "At least one comment needs to be supplied."
  val CommentsInvalid          = "Invalid comment."
  val ListCreatePermission     = "A list can only be created by the project or system administrator."
  val ListNodeCreatePermission = "A list node can only be created by the project or system administrator."
  val ListChangePermission     = "A list can only be changed by the project or system administrator."
  val UpdateRequestEmptyLabel  = "List labels cannot be empty."
  val InvalidPosition          = "Invalid position value is given. Position should be either a positive value, 0 or -1."
}
