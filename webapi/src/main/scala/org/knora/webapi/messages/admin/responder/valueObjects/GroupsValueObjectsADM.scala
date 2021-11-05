/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM.{
  GROUP_DESCRIPTION_INVALID_ERROR,
  GROUP_DESCRIPTION_MISSING_ERROR,
  GROUP_IRI_INVALID_ERROR,
  GROUP_IRI_MISSING_ERROR,
  GROUP_NAME_INVALID_ERROR,
  GROUP_NAME_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

import scala.util.{Failure, Success, Try}

//  TODO-mpro: try resolve Option on value objects side

sealed abstract case class GroupIRI private (value: String)
object GroupIRI {
  private val sf = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, GroupIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(GROUP_IRI_MISSING_ERROR))
    } else {
      if (value.nonEmpty && !sf.isKnoraGroupIriStr(value)) {
        Left(BadRequestException(GROUP_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          sf.validateAndEscapeIri(value, throw BadRequestException(GROUP_IRI_INVALID_ERROR))
        )

        validatedValue match {
          case Success(iri) => Right(new GroupIRI(iri) {})
          case Failure(_)   => Left(BadRequestException(GROUP_IRI_INVALID_ERROR))
        }
      }
    }
}

/**
 * GroupName value object.
 */
sealed abstract case class GroupName private (value: String)
object GroupName {
  private val sf = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, GroupName] =
    if (value.isEmpty) {
      Left(BadRequestException(GROUP_NAME_MISSING_ERROR))
    } else {
      val validatedValue = Try(
        sf.toSparqlEncodedString(value, throw BadRequestException(GROUP_NAME_INVALID_ERROR))
      )

      validatedValue match {
        case Success(value) => Right(new GroupName(value) {})
        case Failure(_)     => Left(BadRequestException(GROUP_NAME_INVALID_ERROR))
      }
    }
}

/**
 * GroupDescription value object.
 */
sealed abstract case class GroupDescription private (value: Seq[StringLiteralV2])
object GroupDescription {
  private val sf = StringFormatter.getGeneralInstance

  def make(value: Seq[StringLiteralV2]): Validation[Throwable, GroupDescription] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(GROUP_DESCRIPTION_MISSING_ERROR))
    } else {
      val validatedDescriptions = Try(value.map { description =>
        val validatedDescription =
          sf.toSparqlEncodedString(description.value, throw BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR))
        StringLiteralV2(value = validatedDescription, language = description.language)
      })
      validatedDescriptions match {
        case Success(value) => Validation.succeed(new GroupDescription(value) {})
        case Failure(_)     => Validation.fail(BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR))
      }
    }
}

/**
 * GroupStatus value object.
 */
sealed abstract case class GroupStatus private (value: Boolean)
object GroupStatus {
  def make(value: Boolean): Validation[Throwable, GroupStatus] =
    Validation.succeed(new GroupStatus(value) {})
}

/**
 * GroupSelfJoin value object.
 */
sealed abstract case class GroupSelfJoin private (value: Boolean)
object GroupSelfJoin {
  def make(value: Boolean): Validation[Throwable, GroupSelfJoin] =
    Validation.succeed(new GroupSelfJoin(value) {})
}
