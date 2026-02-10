/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain

import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.*
import zio.Clock
import zio.IO
import zio.Random
import zio.Ref
import zio.ZLayer
import zio.json.JsonCodec
import zio.stream.ZStream

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

final case class DataExportId private (value: String) extends StringValue
object DataExportId                                   extends StringValueCompanion[DataExportId] {
  given JsonCodec[DataExportId]   = ZioJsonCodec.stringCodec(from)
  given StringCodec[DataExportId] = TapirCodec.stringCodec(from)
  given Schema[DataExportId]      = Schema.string.description("A unique identifier for an export.")

  def from(str: String): Either[String, DataExportId] =
    Try(UUID.fromString(str)).toEither.left
      .map(_ => s"Invalid DataExportId: $str is not a valid UUID.")
      .map(_ => DataExportId(str))

  def makeNew: UIO[DataExportId] = Random.nextUUID.map(uuid => DataExportId(uuid.toString))
}

enum DataExportStatus(val responseStr: String) {
  case InProgress extends DataExportStatus("in_progress")
  case Completed  extends DataExportStatus("completed")
  case Failed     extends DataExportStatus("failed")
}

object DataExportStatus extends WithFrom[String, DataExportStatus] {

  private val expectedValues = s"'${values.map(_.responseStr).mkString(",")}'"

  given JsonCodec[DataExportStatus]   = JsonCodec.string.transformOrFail(from, _.responseStr)
  given StringCodec[DataExportStatus] = TapirCodec.stringCodec(from, _.responseStr)
  given Schema[DataExportStatus]      = Schema.string
    .description(s"The status of an export. One of $expectedValues expected.")
    .validate(Validator.enumeration(values.toList))

  def from(str: String): Either[String, DataExportStatus] =
    DataExportStatus.values
      .find(_.responseStr == str.toLowerCase)
      .toRight(s"Unknown export status $str, expected one of $expectedValues.")
}

final case class CurrentDataExport private (
  id: DataExportId,
  projectIri: ProjectIri,
  status: DataExportStatus,
  createdBy: User,
  createdAt: Instant,
) {
  def complete(): CurrentDataExport = this.copy(status = DataExportStatus.Completed)
  def fail(): CurrentDataExport     = this.copy(status = DataExportStatus.Failed)
  def isInProgess: Boolean          = status == DataExportStatus.InProgress
}

object CurrentDataExport {
  def makeNew(projectIri: ProjectIri, createdBy: User): UIO[CurrentDataExport] =
    for {
      exportId <- DataExportId.makeNew
      now      <- Clock.instant
    } yield CurrentDataExport(exportId, projectIri, DataExportStatus.InProgress, createdBy, now)
}

// This error is used to indicate that an export is already in progress
// when trying to create a new export.
case class ExportInProgressError(value: CurrentDataExport)

// This error is used to indicate that an export is already in progress
// when trying to create a new export.
case class ExportFailedError(value: CurrentDataExport)

final class ProjectDataExportService(currentExp: Ref[Option[CurrentDataExport]]) { self =>

  def createExport(projectIri: ProjectIri, createdBy: User): IO[ExportInProgressError, CurrentDataExport] =
    for {
      existingExport <- self.currentExp.get
      curExp         <- existingExport match {
                  case Some(exp) => ZIO.fail(ExportInProgressError(exp))
                  case None      =>
                    CurrentDataExport
                      .makeNew(projectIri, createdBy)
                      .tap(cde => self.currentExp.set(Some(cde)))
                }
      _ <- (
             /// Simulate a long-running export process by completing the export after a delay.
             // In a real implementation, this would be where the actual export logic goes.
             self.currentExp.getAndUpdateSome { case Some(exp) => Some(exp.complete()) }.delay(10.seconds)
           ).forkDaemon
    } yield curExp

  // Delete the export in question.
  // Do not delete the export if it is still in progress.
  // Will delete the export if it has completed or failed.
  // Return:
  // * ZIO.fail(None) - if the export was not found
  // * ZIO.fail(Some(ExportInProgressError)) - if the export is still in progress
  // * ZIO.unit - if the export was successfully deleted,
  def deleteExport(exportId: DataExportId): IO[Option[ExportInProgressError], Unit] =
    for {
      existingExport <- self.currentExp.get
      _              <- existingExport match {
             case Some(exp) => {
               exp match {
                 case exp if exp.id == exportId && exp.isInProgess => ZIO.fail(Some(ExportInProgressError(exp)))
                 case exp if exp.id == exportId                    => self.currentExp.set(None)
                 case _                                            => ZIO.fail(None)
               }
             }
             case _ => ZIO.fail(None)
           }
    } yield ()

  def getExportStatus(exportId: DataExportId): IO[Option[Nothing], CurrentDataExport] =
    currentExp.get.filterOrFail(_.exists(_.id == exportId))(None).flatMap(ZIO.fromOption)

  def downloadExport(
    exportId: DataExportId,
  ): IO[Option[ExportInProgressError | ExportFailedError], (String, ZStream[Any, Throwable, Byte])] =
    for {
      existingExport <- self.currentExp.get
      exp            <- existingExport match {
               case Some(exp) =>
                 exp match {
                   case exp if exp.id == exportId && exp.isInProgess                       => ZIO.fail(Some(ExportInProgressError(exp)))
                   case exp if exp.id == exportId && exp.status == DataExportStatus.Failed =>
                     ZIO.fail(Some(ExportFailedError(exp)))
                   case exp if exp.id == exportId => ZIO.succeed(exp)
                   case _                         => ZIO.fail(None)
                 }
               case _ => ZIO.fail(None)
             }
      // Simulate a file download by returning a stream of bytes.
      // In a real implementation, this would be where the actual file streaming logic goes.
      fileName    = s"export-${exp.id}.zip"
      fileContent = ZStream.empty
    } yield (fileName, fileContent)
}

object ProjectDataExportService {
  val layer: ZLayer[Any, Nothing, ProjectDataExportService] =
    ZLayer.fromZIO(Ref.make[Option[CurrentDataExport]](None)) >>> ZLayer.derive[ProjectDataExportService]
}
