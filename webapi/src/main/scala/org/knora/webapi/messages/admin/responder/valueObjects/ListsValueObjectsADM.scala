package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.{
  COMMENT_INVALID_ERROR,
  COMMENT_MISSING_ERROR,
  INVALID_POSITION,
  LABEL_INVALID_ERROR,
  LABEL_MISSING_ERROR,
  LIST_NAME_INVALID_ERROR,
  LIST_NAME_MISSING_ERROR,
  LIST_NODE_IRI_INVALID_ERROR,
  LIST_NODE_IRI_MISSING_ERROR,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

import scala.util.{Failure, Success, Try}

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
          case Success(iri) => Right(new ListIRI(iri) {})
          case Failure(_)   => Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
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
      if (value.nonEmpty && !stringFormatter.isKnoraProjectIriStr(value)) {
        Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(PROJECT_IRI_INVALID_ERROR))
        )

        validatedValue match {
          case Success(iri) => Right(new ProjectIRI(iri) {})
          case Failure(_)   => Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
        }
      }
    }
}

/**
 * List ListName value object.
 */
sealed abstract case class ListName private (value: String)
object ListName {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, ListName] =
    if (value.isEmpty) {
      Left(BadRequestException(LIST_NAME_MISSING_ERROR))
    } else {
      val validatedValue = Try(
        stringFormatter.toSparqlEncodedString(value, throw BadRequestException(LIST_NAME_INVALID_ERROR))
      )

      validatedValue match {
        case Success(name) => Right(new ListName(name) {})
        case Failure(_)    => Left(BadRequestException(LIST_NAME_INVALID_ERROR))
      }
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
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: Seq[StringLiteralV2]): Either[Throwable, Labels] =
    if (value.isEmpty) {
      Left(BadRequestException(LABEL_MISSING_ERROR))
    } else {
      val validatedLabels = Try(value.map { label =>
        val validatedLabel =
          stringFormatter.toSparqlEncodedString(label.value, throw BadRequestException(LABEL_INVALID_ERROR))
        StringLiteralV2(value = validatedLabel, language = label.language)
      })

      validatedLabels match {
        case Success(valid) => Right(new Labels(valid) {})
        case Failure(_)     => Left(BadRequestException(LABEL_INVALID_ERROR))
      }
    }
}

/**
 * List Comments value object.
 */
sealed abstract case class Comments private (value: Seq[StringLiteralV2])
object Comments {
  val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: Seq[StringLiteralV2]): Either[Throwable, Comments] =
    if (value.isEmpty) {
      Left(BadRequestException(COMMENT_MISSING_ERROR))
    } else {
      val validatedComments = Try(value.map { comment =>
        val validatedComment =
          stringFormatter.toSparqlEncodedString(comment.value, throw BadRequestException(COMMENT_INVALID_ERROR))
        StringLiteralV2(value = validatedComment, language = comment.language)
      })

      validatedComments match {
        case Success(valid) => Right(new Comments(valid) {})
        case Failure(_)     => Left(BadRequestException(COMMENT_INVALID_ERROR))
      }
    }
}
