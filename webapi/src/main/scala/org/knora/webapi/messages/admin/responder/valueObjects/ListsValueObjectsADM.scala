package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  INVALID_POSITION,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

/**
 * ListName value object.
 */
sealed abstract case class ProjectIRI private (value: String)
object ProjectIRI {
  def create(value: String): Either[Throwable, ProjectIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(PROJECT_IRI_MISSING_ERROR))
    } else {
      Right(new ProjectIRI(value) {})
    }
}

/**
 * ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName {
  def create(value: String): Either[Throwable, ListName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing List Name"))
    } else {
      Right(new ListName(value) {})
    }
}

/**
 * Position value object.
 */
sealed abstract case class Position private (value: Int)
object Position {
  def create(value: Int): Either[Throwable, Position] =
    if (value < -1) { // TODO: what should be the criteria?
      Left(BadRequestException(INVALID_POSITION))
    } else {
      Right(new Position(value) {})
    }
}

/**
 * Labels value object.
 */
sealed abstract case class Labels private (value: Seq[StringLiteralV2])
object Labels {
  def create(value: Seq[StringLiteralV2]): Either[Throwable, Labels] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing labels"))
    } else {
      Right(new Labels(value) {})
    }
}

/**
 * Comments value object.
 */
sealed abstract case class Comments private (value: Seq[StringLiteralV2])
object Comments {
  def create(value: Seq[StringLiteralV2]): Either[Throwable, Comments] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing comments"))
    } else {
      Right(new Comments(value) {})
    }
}
