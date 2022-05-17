/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName { self =>
  def make(value: String): Validation[Throwable, ListName] =
    if (value.isEmpty) {
      Validation.fail(V2.BadRequestException(ListErrorMessages.ListNameMissing))
    } else {
      val validatedValue = Validation(
        V2IriValidation.toSparqlEncodedString(value, throw V2.BadRequestException(ListErrorMessages.ListNameInvalid))
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
      Validation.fail(V2.BadRequestException(ListErrorMessages.InvalidPosition))
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
      Validation.fail(V2.BadRequestException(ListErrorMessages.LabelMissing))
    } else {
      val validatedLabels = Validation(value.map { label =>
        val validatedLabel =
          V2IriValidation.toSparqlEncodedString(
            label.value,
            throw V2.BadRequestException(ListErrorMessages.LabelInvalid)
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
      Validation.fail(V2.BadRequestException(ListErrorMessages.CommentMissing))
    } else {
      val validatedComments = Validation(value.map { comment =>
        val validatedComment =
          V2IriValidation.toSparqlEncodedString(
            comment.value,
            throw V2.BadRequestException(ListErrorMessages.CommentInvalid)
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

object ListErrorMessages {
  val ListNameMissing          = "List name cannot be empty."
  val ListNameInvalid          = "List name is invalid."
  val LabelMissing             = "At least one label needs to be supplied."
  val LabelInvalid             = "Invalid label."
  val CommentMissing           = "At least one comment needs to be supplied."
  val CommentInvalid           = "Invalid comment."
  val ListCreatePermission     = "A list can only be created by the project or system administrator."
  val ListNodeCreatePermission = "A list node can only be created by the project or system administrator."
  val ListChangePermission     = "A list can only be changed by the project or system administrator."
  val UpdateRequestEmptyLabel  = "List labels cannot be empty."
  val InvalidPosition          = "Invalid position value is given, position should be either a positive value or -1."
}
