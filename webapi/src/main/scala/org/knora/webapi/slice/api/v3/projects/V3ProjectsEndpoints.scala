/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects;

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
import org.knora.webapi.slice.api.v3.projects.domain.DataTaskId
import org.knora.webapi.slice.api.v3.projects.domain.DataTaskStatus

final case class ExportAcceptedResponse(exportId: DataTaskId)
object ExportAcceptedResponse {
  given JsonCodec[ExportAcceptedResponse] = DeriveJsonCodec.gen[ExportAcceptedResponse]
}

final case class ImportAcceptedResponse(importId: DataTaskId)
object ImportAcceptedResponse {
  given JsonCodec[ImportAcceptedResponse] = DeriveJsonCodec.gen[ImportAcceptedResponse]
}

final case class ExportStatusResponse(
  exportId: DataTaskId,
  projectIri: ProjectIri,
  status: DataTaskStatus,
  createdBy: UserIri,
  createdAt: Instant,
)
object ExportStatusResponse {
  given JsonCodec[ExportStatusResponse] = DeriveJsonCodec.gen[ExportStatusResponse]
}

class V3ProjectsEndpoints(base: V3BaseEndpoint) extends EndpointHelper { self =>

  private val basePath    = ApiV3.V3ProjectsProjectIri
  private val exportsBase = basePath / "exports"

  private val exportIdPathVar = path[DataTaskId]("exportId")

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
    .out(jsonBody[ExportAcceptedResponse])
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
    .out(jsonBody[ExportStatusResponse])
    .description(
      "Checks the status of an export. " +
        "The response will indicate whether the export is still in progress, has completed successfully, or has failed.",
    )

  // delete an export
  val deleteProjectIriExportsExportId = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.export_exists),
      ),
    )
    .delete
    .in(exportsBase / exportIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .description(
      "Deletes an export irrevocably. " +
        "Another export can only be started when the currently existing is not present any more. " +
        "Only exports in state failed or completed can be deleted.",
    )

  // download an export
  val getProjectIriExportsExportIdDownload = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.export_exists),
      ),
    )
    .get
    .in(exportsBase / exportIdPathVar / "download")
    .out(statusCode(StatusCode.Ok))
    .out(header[String]("Content-Disposition"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .description("Download an export")

  // import an export
  val postProjectIriImports = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found),
        conflictVariant(V3ErrorCode.import_exists),
      ),
    )
    .post
    .in(basePath / "imports")
    .in(streamBinaryBody(ZioStreams)(CodecFormat.Zip()).description("The export zip file to import"))
    .out(statusCode(StatusCode.Accepted))
    .out(jsonBody[ImportAcceptedResponse])
    .description(
      "Initiates an import of a project from an export zip file. " +
        "The import will be performed asynchronously, and the response will indicate that the import has been accepted. " +
        "An import can only be triggered when no other import exists.",
    )

  // get the status of an import
  val getProjectIriImportsImportIdStatus = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.import_exists),
      ),
    )
    .get
    .in(basePath / "imports" / path[DataTaskId]("importId"))
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ExportStatusResponse])
    .description(
      "Checks the status of an import. " +
        "The response will indicate whether the import is still in progress, has completed successfully, or has failed.",
    )
}

object V3ProjectsEndpoints {
  val layer = ZLayer.derive[V3ProjectsEndpoints]
}
