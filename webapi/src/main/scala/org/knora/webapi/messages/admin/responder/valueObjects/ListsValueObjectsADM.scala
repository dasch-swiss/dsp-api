package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  COMMENT_MISSING_ERROR,
  INVALID_POSITION,
  LABEL_MISSING_ERROR,
  LIST_NAME_MISSING_ERROR,
  LIST_NODE_IRI_INVALID_ERROR,
  LIST_NODE_IRI_MISSING_ERROR,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

import scala.util.{Failure, Success, Try}
//TODO: sprawdzic na koncu wszystkie czeki w messangerze czy poktrywaja sie tu i tak samo tresci bledow
//TODO: possible to somehow merge all IRI related value objects? what about diff exception messages
// either use below checks in route to throw custom exceptions for one IRI value object
// or bring these matches here

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
      if (value.nonEmpty && !stringFormatter.isKnoraListIriStr(value)) {
        Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeIri(value, throw BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
        )

        validatedValue match {
          case Success(_) => Right(new ListIRI(value) {})
          case Failure(_) => Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
        }
      }
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
      if (value.nonEmpty && !stringFormatter.isKnoraProjectIriStr((value))) {
        Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )

        validatedValue match {
          case Success(_) => Right(new ProjectIRI(value) {})
          case Failure(_) => Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
        }
      }
    }
}

/**
 * List RootNodeIRI value object.
 */
sealed abstract case class RootNodeIRI private (value: String)
object RootNodeIRI {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, RootNodeIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(s"Missing root node IRI"))
    } else {
      if (value.nonEmpty && !stringFormatter.isKnoraListIriStr(value)) {
        Left(BadRequestException(s"Invalid root node IRI"))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeIri(
            value,
            throw BadRequestException(s"Invalid root node IRI")
          )
        )

        validatedValue match {
          case Success(_) => Right(new RootNodeIRI(value) {})
          case Failure(_) => Left(BadRequestException(s"Invalid root node IRI"))
        }
      }
    }
}

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName {
  def create(value: String): Either[Throwable, ListName] =
    if (value.isEmpty) {
      Left(BadRequestException(LIST_NAME_MISSING_ERROR))
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
    if (value < -1) {
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
      Left(BadRequestException(COMMENT_MISSING_ERROR))
    } else {
      Right(new Comments(value) {})
    }
}
