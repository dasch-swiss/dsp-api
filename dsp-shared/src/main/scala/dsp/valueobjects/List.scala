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
    def make(value: String): Validation[Throwable, ListName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ListErrorMessages.ListNameMissing))
      } else {
        val validatedValue = Validation(
          Iri.toSparqlEncodedString(value, throw BadRequestException(ListErrorMessages.ListNameInvalid))
        )

        validatedValue.map(new ListName(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListName]] =
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
    def make(value: Int): Validation[Throwable, Position] =
      if (value < -1) {
        Validation.fail(BadRequestException(ListErrorMessages.InvalidPosition))
      } else {
        Validation.succeed(new Position(value) {})
      }

    def make(value: Option[Int]): Validation[Throwable, Option[Position]] =
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
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, Labels] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ListErrorMessages.LabelsMissing))
      } else {
        val validatedLabels = Validation(value.map { label =>
          val validatedLabel =
            Iri.toSparqlEncodedString(
              label.value,
              throw BadRequestException(ListErrorMessages.LabelsInvalid)
            )
          V2.StringLiteralV2(value = validatedLabel, language = label.language)
        })

        validatedLabels.map(new Labels(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[Labels]] =
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
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, Comments] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ListErrorMessages.CommentsMissing))
      } else {
        val validatedComments = Validation(value.map { comment =>
          val validatedComment =
            Iri.toSparqlEncodedString(
              comment.value,
              throw BadRequestException(ListErrorMessages.CommentsInvalid)
            )
          V2.StringLiteralV2(value = validatedComment, language = comment.language)
        })

        validatedComments.map(new Comments(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[Comments]] =
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
