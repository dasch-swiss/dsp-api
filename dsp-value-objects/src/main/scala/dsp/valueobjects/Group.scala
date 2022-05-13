/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.StringFormatter.UUID_INVALID_ERROR
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * GroupName value object.
 */
sealed abstract case class GroupName private (value: String)
object GroupName { self =>
  private val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, GroupName] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(GROUP_NAME_MISSING_ERROR))
    } else {
      val validatedValue = Validation(
        sf.toSparqlEncodedString(value, throw BadRequestException(GROUP_NAME_INVALID_ERROR))
      )

      validatedValue.map(new GroupName(_) {})
    }

  def make(value: Option[String]): Validation[Throwable, Option[GroupName]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * GroupDescriptions value object.
 */
sealed abstract case class GroupDescriptions private (value: Seq[StringLiteralV2])
object GroupDescriptions { self =>
  private val sf: StringFormatter = StringFormatter.getGeneralInstance

  def make(value: Seq[StringLiteralV2]): Validation[Throwable, GroupDescriptions] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException(GROUP_DESCRIPTION_MISSING_ERROR))
    } else {
      val validatedDescriptions = Validation(value.map { description =>
        val validatedDescription =
          sf.toSparqlEncodedString(description.value, throw BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR))
        StringLiteralV2(value = validatedDescription, language = description.language)
      })
      validatedDescriptions.map(new GroupDescriptions(_) {})
    }

  def make(value: Option[Seq[StringLiteralV2]]): Validation[Throwable, Option[GroupDescriptions]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * GroupStatus value object.
 */
sealed abstract case class GroupStatus private (value: Boolean)
object GroupStatus { self =>
  def make(value: Boolean): Validation[Throwable, GroupStatus] =
    Validation.succeed(new GroupStatus(value) {})
  def make(value: Option[Boolean]): Validation[Throwable, Option[GroupStatus]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}

/**
 * GroupSelfJoin value object.
 */
sealed abstract case class GroupSelfJoin private (value: Boolean)
object GroupSelfJoin { self =>
  def make(value: Boolean): Validation[Throwable, GroupSelfJoin] =
    Validation.succeed(new GroupSelfJoin(value) {})
  def make(value: Option[Boolean]): Validation[Throwable, Option[GroupSelfJoin]] =
    value match {
      case Some(v) => self.make(v).map(Some(_))
      case None    => Validation.succeed(None)
    }
}
