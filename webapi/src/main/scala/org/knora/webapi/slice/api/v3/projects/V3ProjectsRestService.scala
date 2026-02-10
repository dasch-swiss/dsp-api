package org.knora.webapi.slice.api.v3.projects

import zio.*
import zio.stream.*
import sttp.model.MediaType
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.Forbidden
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.api.AuthorizationRestService

import java.time.Instant

final case class CurrentExport(
  exportId: ExportId,
  projectIri: ProjectIri,
  status: ExportStatus,
  createdBy: User,
  createdAt: Instant,
) {
  def complete(): CurrentExport = this.copy(status = ExportStatus.Completed)
  def fail(): CurrentExport     = this.copy(status = ExportStatus.Failed)
}

object CurrentExport {
  def createNew(projectIri: ProjectIri, createdBy: User): UIO[CurrentExport] =
    Clock.instant.map { now =>
      CurrentExport(
        ExportId(java.util.UUID.randomUUID().toString),
        projectIri,
        ExportStatus.InProgress,
        createdBy,
        now,
      )
    }
}

final class V3ProjectsRestService(auth: AuthorizationRestService, runningExport: Ref[Option[CurrentExport]]) {

  private def failConflict(e: CurrentExport): ZIO[Any, Conflict, Nothing] =
    ZIO.fail(
      Conflict(
        V3ErrorCode.export_in_progress,
        s"An export with id ${e.exportId.value} is already in progress for project ${e.projectIri.value}.",
        Map("exportId" -> e.exportId.value, "projectIri" -> e.projectIri.value),
      ),
    )

  def triggerProjectExportCreate(user: User)(projectIri: ProjectIri): IO[V3ErrorInfo, ExportAcceptedResponse] =
    for {
      _ <- auth.ensureSystemAdmin(user).mapError(e => Forbidden(e.message))
      _ <- runningExport.get.flatMap {
             case Some(e) => failConflict(e)
             case None    => ZIO.unit
           }
      e <- CurrentExport.createNew(projectIri, user)
      _ <- runningExport.set(Some(e))
      // Simulate export processing
      _ <- runningExport.update(_.map(_.complete())).delay(30.seconds).forkDaemon
    } yield ExportAcceptedResponse(e.exportId)

  // Return the export status. Kept unimplemented for now but annotated so compilation succeeds.
  def getProjectExportStatus(
    user: User,
  )(projectIri: ProjectIri, exportId: ExportId): IO[V3ErrorInfo, ExportStatusResponse] = for {
    _         <- auth.ensureSystemAdmin(user).mapError(e => Forbidden(e.message))
    exportOpt <- runningExport.get
    curExp    <- ZIO
                .fromOption(exportOpt.filter(e => e.exportId == exportId && e.projectIri == projectIri))
                .orElseFail(
                  NotFound(
                    V3ErrorCode.export_not_found,
                    s"No export with id ${exportId.value} found for project ${projectIri.value}.",
                    Map("exportId" -> exportId.value, "projectIri" -> projectIri.value),
                  ),
                )
  } yield ExportStatusResponse(
    curExp.exportId,
    curExp.projectIri,
    curExp.status,
    curExp.createdBy.userIri,
    curExp.createdAt,
  )

  // Download the export as a zip stream. Annotated to match the tapir endpoint (stream + media type + filename).
  def downloadProjectExport(
    user: User,
  )(projectIri: ProjectIri, exportId: ExportId): IO[V3ErrorInfo, (String, MediaType, ZStream[Any, Throwable, Byte])] =
    ???
}

object V3ProjectsRestService {
  // Ref.make returns a ZIO effect, so create a layer from that effect
  // This layer requires an AuthorizationRestService to be present in the environment.
  val layer: ZLayer[AuthorizationRestService, Nothing, V3ProjectsRestService] =
    ZLayer.fromZIO(Ref.make[Option[CurrentExport]](None)) >>> ZLayer.derive[V3ProjectsRestService]
}
