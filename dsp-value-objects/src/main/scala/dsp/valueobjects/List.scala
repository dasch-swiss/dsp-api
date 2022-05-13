/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.StringFormatter.IriErrorMessages.UuidInvalid
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, ListName] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(ListErrorMessages.ListNameMissing))
    } else {
      val validatedValue = Validation(
        sf.toSparqlEncodedString(value, throw BadRequestException(ListErrorMessages.ListNameInvalid))
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
sealed abstract case class Labels private (value: Seq[StringLiteralV2])
object Labels { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: Seq[StringLiteralV2]): Validation[Throwable, Labels] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(ListErrorMessages.LabelMissing))
    } else {
      val validatedLabels = Validation(value.map { label =>
        val validatedLabel =
          sf.toSparqlEncodedString(label.value, throw BadRequestException(ListErrorMessages.LabelInvalid))
        StringLiteralV2(value = validatedLabel, language = label.language)
      })

      validatedLabels.map(new Labels(_) {})
    }

  def make(value: Option[Seq[StringLiteralV2]]): Validation[Throwable, Option[Labels]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * List Comments value object.
 */
sealed abstract case class Comments private (value: Seq[StringLiteralV2])
object Comments { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: Seq[StringLiteralV2]): Validation[Throwable, Comments] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(ListErrorMessages.CommentMissing))
    } else {
      val validatedComments = Validation(value.map { comment =>
        val validatedComment =
          sf.toSparqlEncodedString(comment.value, throw BadRequestException(ListErrorMessages.CommentInvalid))
        StringLiteralV2(value = validatedComment, language = comment.language)
      })

      validatedComments.map(new Comments(_) {})
    }

  def make(value: Option[Seq[StringLiteralV2]]): Validation[Throwable, Option[Comments]] =
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
