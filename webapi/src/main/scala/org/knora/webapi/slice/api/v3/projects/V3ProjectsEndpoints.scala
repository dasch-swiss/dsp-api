/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.capabilities.zio.ZioStreams
import sttp.model.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.header
import zio.*
import zio.json.*

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.api.v3.projects.domain.CurrentDataTask
import org.knora.webapi.slice.api.v3.projects.domain.DataTaskId
import org.knora.webapi.slice.api.v3.projects.domain.DataTaskStatus

final case class DataTaskStatusResponse(
  id: DataTaskId,
  projectIri: ProjectIri,
  status: DataTaskStatus,
  createdBy: UserIri,
  createdAt: Instant,
)
object DataTaskStatusResponse {
  given JsonCodec[DataTaskStatusResponse] = DeriveJsonCodec.gen[DataTaskStatusResponse]

  def from(state: CurrentDataTask): DataTaskStatusResponse =
    DataTaskStatusResponse(
      state.id,
      state.projectIri,
      state.status,
      state.createdBy.userIri,
      state.createdAt,
    )
}

class V3ProjectsEndpoints(base: V3BaseEndpoint) extends EndpointHelper { self =>

  private val basePath    = ApiV3.V3ProjectsProjectIri
  private val exportsBase = basePath / "exports"
  private val importsBase = basePath / "imports"

  private val exportIdPathVar = path[DataTaskId]("exportId")
  private val importIdPathVar = path[DataTaskId]("importId")

  // trigger an export
  val postProjectIriExports = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found),
        conflictVariant(V3ErrorCode.export_exists),
      ),
    )
    .post
    .in(exportsBase)
    .out(statusCode(StatusCode.Accepted))
    .out(jsonBody[DataTaskStatusResponse])
    .description(
      "Initiates an export of the project. " +
        "The export will be performed asynchronously, and the response will contain an export ID that can be used to check the status of the export. " +
        "An export can only be triggered when no other export exists.",
    )

  // get the status of an export
  val getProjectIriExportsExportId = self.base
    .secured(oneOf(notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found)))
    .get
    .in(exportsBase / exportIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[DataTaskStatusResponse])
    .description(
      "Checks the status of an export. " +
        "The response will indicate whether the export is still in progress, has completed successfully, or has failed.",
    )

  // delete an export
  val deleteProjectIriExportsExportId = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.export_in_progress),
      ),
    )
    .delete
    .in(exportsBase / exportIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .description(
      "Deletes an export irrevocably. " +
        "Only exports in state failed or completed can be deleted.",
    )

  // download an export
  val getProjectIriExportsExportIdDownload = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.export_in_progress, V3ErrorCode.export_failed),
      ),
    )
    .get
    .in(exportsBase / exportIdPathVar / "download")
    .out(statusCode(StatusCode.Ok))
    .out(header[String]("Content-Disposition"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .description(
      "Download an export " +
        "An export can only be downloaded when it has completed successfully. " +
        "If it is still in progress or has failed, the response will be 409 Conflict.",
    )

  // import an export
  val postProjectIriImports = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found),
        conflictVariant(V3ErrorCode.import_exists),
      ),
    )
    .post
    .in(importsBase)
    .in(streamBinaryBody(ZioStreams)(CodecFormat.Zip()).description("The export zip file to import"))
    .out(statusCode(StatusCode.Accepted))
    .out(jsonBody[DataTaskStatusResponse])
    .description(
      "Initiates an import of a project from an export zip file. " +
        "The import will be performed asynchronously, and the response will indicate that the import has been accepted. " +
        "An import can only be triggered when no other import exists.",
    )

  // get the status of an import
  val getProjectIriImportsImportId = self.base
    .secured(oneOf(notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.import_not_found)))
    .get
    .in(importsBase / importIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[DataTaskStatusResponse])
    .description(
      "Checks the status of an import. " +
        "The response will indicate whether the import is still in progress, has completed successfully, or has failed.",
    )

  // delete an import
  val deleteProjectIriImportsImportId = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.import_not_found),
        conflictVariant(V3ErrorCode.import_exists),
      ),
    )
    .delete
    .in(importsBase / importIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .description(
      "Deletes an import. " +
        "Only imports in state failed or completed can be deleted." +
        "If it is still in progress, the response will be 409 Conflict.",
    )
}

object V3ProjectsEndpoints {
  val layer = ZLayer.derive[V3ProjectsEndpoints]
}
