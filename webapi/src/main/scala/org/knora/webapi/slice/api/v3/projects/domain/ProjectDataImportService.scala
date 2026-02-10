package org.knora.webapi.slice.api.v3.projects.domain
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import zio.*
import zio.stream.ZStream

import java.time.Instant

final case class CurrentDataImport private (
  id: DataExportId,
  projectIri: ProjectIri,
  status: DataExportStatus,
  createdBy: User,
  createdAt: Instant,
) {
  def complete(): CurrentDataImport = this.copy(status = DataExportStatus.Completed)
  def fail(): CurrentDataImport     = this.copy(status = DataExportStatus.Failed)
  def isInProgess: Boolean          = status == DataExportStatus.InProgress
}
object CurrentDataImport {
  def makeNew(projectIri: ProjectIri, createdBy: User): UIO[CurrentDataImport] =
    for {
      exportId <- DataExportId.makeNew
      now      <- Clock.instant
    } yield CurrentDataImport(exportId, projectIri, DataExportStatus.InProgress, createdBy, now)
}

// This error is used to indicate that an import is already in progress for a project when a new import request is made.
final case class ImportInProgressError(value: CurrentDataImport)

final class ProjectDataImportService(
  currentImport: Ref[Option[CurrentDataImport]],
) { self =>

  def importDataExport(
    projectIri: ProjectIri,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportInProgressError, CurrentDataImport] = for {
    existingImport <- self.currentImport.get
    curExp         <- existingImport match {
                case Some(exp) => ZIO.fail(ImportInProgressError(exp))
                case None      =>
                  CurrentDataImport
                    .makeNew(projectIri, createdBy)
                    .tap(cde => self.currentImport.set(Some(cde)))
              }
    // In a real implementation, we would process the stream here and update the import status accordingly.
    _  = stream
    _ <- (
           /// Simulate a long-running export process by completing the export after a delay.
           // In a real implementation, this would be where the actual export logic goes.
           self.currentImport.getAndUpdateSome { case Some(exp) => Some(exp.complete()) }.delay(10.seconds)
         ).forkDaemon
  } yield curExp

  def getImportStatus(importId: DataExportId): IO[Option[Nothing], CurrentDataImport] =
    self.currentImport.get.flatMap(ZIO.fromOption).filterOrFail(_.id == importId)(None)
}

object ProjectDataImportService {
  val layer = ZLayer.fromZIO(Ref.make[Option[CurrentDataImport]](None)) >>> ZLayer.derive[ProjectDataImportService]
}
