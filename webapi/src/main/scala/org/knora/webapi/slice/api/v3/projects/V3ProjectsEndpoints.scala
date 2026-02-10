package org.knora.webapi.slice.api.v3.projects;

import org.knora.webapi.slice.api.v3.*
import zio.*
import sttp.model.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.json.*
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec.StringCodec
import org.knora.webapi.slice.admin.domain.model.UserIri
import java.time.Instant
import sttp.capabilities.zio.ZioStreams

final case class ExportId(val value: String) extends StringValue
object ExportId {
  given JsonCodec[ExportId]   = ZioJsonCodec.stringCodec(str => Right(ExportId(str)))
  given StringCodec[ExportId] = TapirCodec.stringCodec(str => Right(ExportId(str)))
}

final case class ExportAcceptedResponse(exportId: ExportId)
object ExportAcceptedResponse {
  given JsonCodec[ExportAcceptedResponse] = DeriveJsonCodec.gen[ExportAcceptedResponse]
}

case class ExportStatus(val value: String) extends StringValue

object ExportStatus extends StringValueCompanion[ExportStatus] {
  val InProgress = ExportStatus("in_progress")
  val Completed  = ExportStatus("completed")
  val Failed     = ExportStatus("failed")

  def from(str: String): Either[String, ExportStatus] =
    str.toLowerCase match {
      case "in_progress" => Right(InProgress)
      case "completed"   => Right(Completed)
      case "failed"      => Right(Failed)
      case _             => Left("Unknown export status")
    }

  given JsonCodec[ExportStatus] = ZioJsonCodec.stringCodec(ExportStatus.from)
}

final case class ExportStatusResponse(
  val exportId: ExportId,
  projectIri: ProjectIri,
  status: ExportStatus,
  createdBy: UserIri,
  createdAt: Instant,
)
object ExportStatusResponse {
  given JsonCodec[ExportStatusResponse] = DeriveJsonCodec.gen[ExportStatusResponse]
}

class V3ProjectsEndpoints(base: V3BaseEndpoint) extends EndpointHelper { self =>

  private val basePath   = ApiV3.V3ProjectsProjectIri
  private val exportBase = basePath / ""

  private val exportIdPathVar = path[ExportId]("exportId")

  // trigger an export
  val postProjectIriExports = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found),
        conflictVariant(V3ErrorCode.export_in_progress),
      ),
    )
    .post
    .in(exportBase)
    .out(statusCode(StatusCode.Accepted))
    .out(jsonBody[ExportAcceptedResponse])
    .description(
      "Initiates an export of the project. The export will be performed asynchronously, and the response will contain an export ID that can be used to check the status of the export.",
    )

  // get the status of an export
  val getProjectIriExportsExportId = self.base
    .secured(oneOf(notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found)))
    .get
    .in(exportBase / exportIdPathVar)
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ExportStatusResponse])
    .description(
      "Checks the status of an export. The response will indicate whether the export is still in progress, has completed successfully, or has failed.",
    )

  // download an export
  val getProjectIriExportsExportIdDownload = self.base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.project_not_found, V3ErrorCode.export_not_found),
        conflictVariant(V3ErrorCode.export_in_progress),
      ),
    )
    .get
    .in(exportBase / exportIdPathVar / "download")
    .out(statusCode(StatusCode.Ok))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .description("Download an export")
}

object V3ProjectsEndpoints {
  val layer = ZLayer.derive[V3ProjectsEndpoints]
}
