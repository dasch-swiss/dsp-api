/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * List ListIRI value object.
 */
sealed abstract case class ListIRI private (value: String)
object ListIRI { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, ListIRI] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(LIST_NODE_IRI_MISSING_ERROR))
    } else {
      if (!sf.isKnoraListIriStr(value)) {
        Validation.fail(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Validation(
          sf.validateAndEscapeIri(value, throw BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
        )

        validatedValue.map(new ListIRI(_) {})
      }
    }

  def make(value: Option[String]): Validation[Throwable, Option[ListIRI]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName { self =>
  val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, ListName] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(LIST_NAME_MISSING_ERROR))
    } else {
      val validatedValue = Validation(
        sf.toSparqlEncodedString(value, throw BadRequestException(LIST_NAME_INVALID_ERROR))
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
      Validation.fail(BadRequestException(INVALID_POSITION))
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
      Validation.fail(BadRequestException(LABEL_MISSING_ERROR))
    } else {
      val validatedLabels = Validation(value.map { label =>
        val validatedLabel =
          sf.toSparqlEncodedString(label.value, throw BadRequestException(LABEL_INVALID_ERROR))
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
      Validation.fail(BadRequestException(COMMENT_MISSING_ERROR))
    } else {
      val validatedComments = Validation(value.map { comment =>
        val validatedComment =
          sf.toSparqlEncodedString(comment.value, throw BadRequestException(COMMENT_INVALID_ERROR))
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
