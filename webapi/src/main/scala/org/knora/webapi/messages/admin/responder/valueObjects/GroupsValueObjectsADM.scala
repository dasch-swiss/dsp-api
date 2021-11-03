package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * GroupName value object.
 */
sealed abstract case class GroupName private (value: String)
object GroupName {
  def create(value: String): Either[Throwable, GroupName] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing group name"))
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
      Validation.fail(BadRequestException("Missing group description"))
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
