package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  INVALID_POSITION,
  LABEL_MISSING_ERROR,
  LIST_NODE_IRI_INVALID_ERROR,
  LIST_NODE_IRI_MISSING_ERROR,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
//TODO: sprawdzic na koncu wszystkie czeki w messangerze czy poktrywaja sie tu i tak samo tresci bledow
//TODO: possible to somehow merge all IRI related value objects? what about diff exception messages
// either use below checks in route to throw custom exceptions for one IRI value object
// or bring these matches here
//case Some(value) => Some(Comments.create(value).fold(e => throw e, v => v))
//case None        => None

/**
 * List ListIRI value object.
 */
sealed abstract case class ListIRI private (value: String)
object ListIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, ListIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(LIST_NODE_IRI_MISSING_ERROR))
    } else {
      val validatedValue = stringFormatter.validateAndEscapeIri(
        value,
        throw BadRequestException(LIST_NODE_IRI_INVALID_ERROR)
      )
      Right(new ListIRI(validatedValue) {})
    }
}

/**
 * List CustomID value object.
 */
sealed abstract case class CustomID private (value: String)
object CustomID {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, CustomID] =
    if (value.isEmpty) {
//      TODO: if id is only optional empty condition shouldn't exist
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
sealed abstract case class ProjectIRI private (value: String)
object ProjectIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, ProjectIRI] =
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
 * List RootNodeIRI value object.
 */
sealed abstract case class RootNodeIRI private (value: String)
object RootNodeIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, RootNodeIRI] =
    //      TODO: if id is only optional empty condition shouldn't exist
    if (value.isEmpty) {
      Left(BadRequestException(s"Missing root node IRI"))
    } else {
      val validatedValue = stringFormatter.validateAndEscapeIri(
        value,
        throw BadRequestException(s"Invalid root node IRI")
      )
      Right(new RootNodeIRI(validatedValue) {})
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
      Left(BadRequestException(LABEL_MISSING_ERROR))
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
