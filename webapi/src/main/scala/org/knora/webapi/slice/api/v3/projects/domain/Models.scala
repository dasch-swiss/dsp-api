/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain

import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.*
import zio.Clock
import zio.Random
import zio.json.JsonCodec

import java.time.Instant
import java.util.UUID
import scala.util.Try

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec.StringCodec
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

final case class DataTaskId private (value: String) extends StringValue
object DataTaskId                                   extends StringValueCompanion[DataTaskId] {
  given JsonCodec[DataTaskId]   = ZioJsonCodec.stringCodec(from)
  given StringCodec[DataTaskId] = TapirCodec.stringCodec(from)
  given Schema[DataTaskId]      = Schema.string.description("A unique identifier.")

  def from(str: String): Either[String, DataTaskId] =
    Try(UUID.fromString(str)).toEither.left
      .map(_ => s"Invalid id: '$str' is not a valid UUID.")
      .map(_ => DataTaskId(str))

  def makeNew: UIO[DataTaskId] = Random.nextUUID.map(_.toString).map(DataTaskId(_))
}

enum DataTaskStatus(val responseStr: String) {
  case InProgress extends DataTaskStatus("in_progress")
  case Completed  extends DataTaskStatus("completed")
  case Failed     extends DataTaskStatus("failed")
}

object DataTaskStatus extends WithFrom[String, DataTaskStatus] {

  private val expectedValues = s"'${values.map(_.responseStr).mkString(",")}'"

  given JsonCodec[DataTaskStatus]   = JsonCodec.string.transformOrFail(from, _.responseStr)
  given StringCodec[DataTaskStatus] = TapirCodec.stringCodec(from, _.responseStr)
  given Schema[DataTaskStatus]      = Schema.string
    .description(s"The status of a task. One of $expectedValues expected.")
    .validate(Validator.enumeration(values.toList))

  def from(str: String): Either[String, DataTaskStatus] =
    DataTaskStatus.values
      .find(_.responseStr == str.toLowerCase)
      .toRight(s"Unknown status $str, expected one of $expectedValues.")
}

final case class CurrentDataTask private (
  id: DataTaskId,
  projectIri: ProjectIri,
  status: DataTaskStatus,
  createdBy: User,
  createdAt: Instant,
) {
  def complete(): CurrentDataTask = this.copy(status = DataTaskStatus.Completed)
  def fail(): CurrentDataTask     = this.copy(status = DataTaskStatus.Failed)
  def isInProgress: Boolean       = status == DataTaskStatus.InProgress
  def isFailed: Boolean           = status == DataTaskStatus.Failed
  def isCompleted: Boolean        = status == DataTaskStatus.Completed
}

object CurrentDataTask {
  def makeNew(projectIri: ProjectIri, createdBy: User): UIO[CurrentDataTask] =
    for {
      exportId <- DataTaskId.makeNew
      now      <- Clock.instant
    } yield CurrentDataTask(exportId, projectIri, DataTaskStatus.InProgress, createdBy, now)
}
