/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

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

  final case class GroupDescriptions private (override val value: Seq[V2.StringLiteralV2])
      extends AnyVal
      with Value[Seq[V2.StringLiteralV2]]
  object GroupDescriptions extends WithFrom[Seq[V2.StringLiteralV2], GroupDescriptions] {
    def from(value: Seq[V2.StringLiteralV2]): Either[String, GroupDescriptions] =
      if (value.isEmpty) Left(GroupErrorMessages.GroupDescriptionsMissing)
      else
        Iri
          .toSparqlEncodedString(value.head.value)
          .toRight(GroupErrorMessages.GroupDescriptionsInvalid)
          .map(_ => GroupDescriptions(value))
  }

  /**
   * GroupStatus value object.
   */
  final case class GroupStatus private (value: Boolean) extends AnyVal with BooleanValue
  object GroupStatus {
    val active: GroupStatus               = new GroupStatus(true)
    val inactive: GroupStatus             = new GroupStatus(false)
    def from(value: Boolean): GroupStatus = if (value) active else inactive
  }

  final case class GroupSelfJoin private (value: Boolean) extends AnyVal with BooleanValue
  object GroupSelfJoin {
    val possible: GroupSelfJoin             = new GroupSelfJoin(true)
    val impossible: GroupSelfJoin           = new GroupSelfJoin(false)
    def from(value: Boolean): GroupSelfJoin = if (value) possible else impossible
  }
}

object GroupErrorMessages {
  val GroupNameMissing         = "Group name cannot be empty."
  val GroupNameInvalid         = "Group name is invalid."
  val GroupDescriptionsMissing = "Group description cannot be empty."
  val GroupDescriptionsInvalid = "Group description is invalid."
}
