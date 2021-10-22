package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  INVALID_POSITION,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

/**
 * List CustomID value object.
 */
sealed abstract case class CustomID private (value: IRI)
object CustomID {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: IRI): Either[Throwable, CustomID] =
    if (value.isEmpty) {
      Left(BadRequestException("Invalid custom node IRI"))
    } else {
      val validatedValue = stringFormatter.validateAndEscapeIri(
        value,
        throw BadRequestException(s"Invalid custom node IRI")
      )
      Right(new CustomID(validatedValue) {})
    }
}

/**
 * List ProjectIRI value object.
 */
sealed abstract case class ProjectIRI private (value: IRI)
object ProjectIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: IRI): Either[Throwable, ProjectIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(PROJECT_IRI_MISSING_ERROR))
    } else {
      val validatedValue = stringFormatter.validateAndEscapeProjectIri(
        value,
        throw BadRequestException(PROJECT_IRI_INVALID_ERROR)
      )
      Right(new ProjectIRI(validatedValue) {})
    }
}

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName {
  def create(value: String): Either[Throwable, ListName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing list name"))
    } else {
      Right(new ListName(value) {})
    }
}

/**
 * List Position value object.
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
 * List Labels value object.
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
 * List Comments value object.
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
