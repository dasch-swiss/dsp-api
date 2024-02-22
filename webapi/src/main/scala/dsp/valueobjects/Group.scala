/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import dsp.errors.BadRequestException
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.{BooleanValue, StringValue}

object Group {

  /**
   * GroupName value object.
   */
  final case class GroupName private (value: String) extends AnyVal with StringValue
  object GroupName extends StringValueCompanion[GroupName] {
    def from(value: String): Either[String, GroupName] =
      value match {
        case _ if value.isEmpty => Left(GroupErrorMessages.GroupNameMissing)
        case _ =>
          Iri
            .toSparqlEncodedString(value)
            .toRight(GroupErrorMessages.GroupNameInvalid)
            .map(GroupName.apply)
      }
  }

  /**
   * GroupDescriptions value object.
   */
  sealed abstract case class GroupDescriptions private (value: Seq[V2.StringLiteralV2])
  object GroupDescriptions { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[BadRequestException, GroupDescriptions] =
      if (value.isEmpty) Validation.fail(BadRequestException(GroupErrorMessages.GroupDescriptionsMissing))
      else {
        val validatedDescriptions = value.map(d =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(d.value))
            .mapError(_ => BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid))
            .map(s => V2.StringLiteralV2(s, d.language))
        )
        Validation.validateAll(validatedDescriptions).map(new GroupDescriptions(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[BadRequestException, Option[GroupDescriptions]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * GroupStatus value object.
   */
  final case class GroupStatus private (value: Boolean) extends AnyVal with BooleanValue
  object GroupStatus {
    val active: GroupStatus               = GroupStatus.from(true)
    val inactive: GroupStatus             = GroupStatus.from(false)
    def from(value: Boolean): GroupStatus = if (value) active else inactive
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
}

object GroupErrorMessages {
  val GroupNameMissing         = "Group name cannot be empty."
  val GroupNameInvalid         = "Group name is invalid."
  val GroupDescriptionsMissing = "Group description cannot be empty."
  val GroupDescriptionsInvalid = "Group description is invalid."
}
