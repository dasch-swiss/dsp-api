/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM.{
  GROUP_DESCRIPTION_MISSING_ERROR,
  GROUP_IRI_INVALID_ERROR,
  GROUP_IRI_MISSING_ERROR,
  GROUP_NAME_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

import scala.util.{Failure, Success, Try}

sealed abstract case class GroupIRI private (value: String)
object GroupIRI {
  private val stringFormatter = StringFormatter.getGeneralInstance

  def create(value: String): Either[Throwable, GroupIRI] =
    if (value.isEmpty) {
      Left(BadRequestException(GROUP_IRI_MISSING_ERROR))
    } else {
      if (value.nonEmpty && !stringFormatter.isKnoraGroupIriStr(value)) {
        Left(BadRequestException(GROUP_IRI_INVALID_ERROR))
      } else {
        val validatedValue = Try(
          stringFormatter.validateAndEscapeIri(value, throw BadRequestException(GROUP_IRI_INVALID_ERROR))
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
  def create(value: String): Either[Throwable, GroupName] =
    if (value.isEmpty) {
      Left(BadRequestException(GROUP_NAME_MISSING_ERROR))
    } else {
      Right(new GroupName(value) {})
    }
}

/**
 * GroupDescription value object.
 */
sealed abstract case class GroupDescription private (value: Seq[StringLiteralV2])
object GroupDescription {
  def make(value: Seq[StringLiteralV2]): Validation[Throwable, GroupDescription] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(GROUP_DESCRIPTION_MISSING_ERROR))
    } else {
      Validation.succeed(new GroupDescription(value) {})
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
