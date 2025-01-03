/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import sttp.tapir.{Codec, CodecFormat, EndpointInput}
import swiss.dasch.api.ProjectsEndpoints.{assetIdPathVar, shortcodePathVar}
import swiss.dasch.api.ProjectsEndpointsResponses.{
  AssetCheckResultResponse,
  AssetInfoResponse,
  ProjectResponse,
  UploadResponse,
}
import swiss.dasch.domain.*
import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.{Chunk, ZLayer}

object ProjectsEndpointsResponses {
  final case class ProjectResponse(id: String)

  object ProjectResponse {
    def from(shortcode: ProjectShortcode): ProjectResponse = ProjectResponse(shortcode.value)
    def from(folder: ProjectFolder): ProjectResponse       = ProjectResponse(folder.shortcode.value)

    given schema: Schema[ProjectResponse]   = DeriveSchema.gen[ProjectResponse]
    given codec: JsonCodec[ProjectResponse] = DeriveJsonCodec.gen[ProjectResponse]
  }

  final case class SingleFileCheckResultResponse(filename: String, checksumMatches: Boolean)

  private object SingleFileCheckResultResponse {
    def from(result: ChecksumResult): SingleFileCheckResultResponse =
      SingleFileCheckResultResponse(result.file.filename.toString, result.checksumMatches)

    given codec: JsonCodec[SingleFileCheckResultResponse] = DeriveJsonCodec.gen[SingleFileCheckResultResponse]

    given schema: Schema[SingleFileCheckResultResponse] = DeriveSchema.gen[SingleFileCheckResultResponse]
  }

  final case class AssetCheckResultEntry(
    assetId: String,
    originalFilename: String,
    results: List[SingleFileCheckResultResponse],
  )

  private object AssetCheckResultEntry {

    def from(assetInfo: AssetInfo, results: Chunk[ChecksumResult]): AssetCheckResultEntry =
      AssetCheckResultEntry(
        assetInfo.assetRef.id.value,
        assetInfo.originalFilename.toString,
        results.map(SingleFileCheckResultResponse.from).toList,
      )

    given codec: JsonCodec[AssetCheckResultEntry] = DeriveJsonCodec.gen[AssetCheckResultEntry]

    given schema: Schema[AssetCheckResultEntry] = DeriveSchema.gen[AssetCheckResultEntry]
  }

  final case class AssetCheckResultSummary(
    numberOfAssets: Int,
    numberOfFiles: Int,
    numberOfChecksumMatches: Int,
  )

  private object AssetCheckResultSummary {
    given codec: JsonCodec[AssetCheckResultSummary] = DeriveJsonCodec.gen[AssetCheckResultSummary]

    given schema: Schema[AssetCheckResultSummary] = DeriveSchema.gen[AssetCheckResultSummary]
  }

  final case class AssetCheckResultResponse(summary: AssetCheckResultSummary, results: List[AssetCheckResultEntry])

  object AssetCheckResultResponse {
    given codec: JsonCodec[AssetCheckResultResponse] = DeriveJsonCodec.gen[AssetCheckResultResponse]

    given schema: Schema[AssetCheckResultResponse] = DeriveSchema.gen[AssetCheckResultResponse]

    def from(report: ChecksumReport): AssetCheckResultResponse = {
      val reportResults = report.results
      val results       = reportResults.map { case (info, checksum) => AssetCheckResultEntry.from(info, checksum) }.toList
      val summary = AssetCheckResultSummary(
        reportResults.keys.size,
        reportResults.values.map(_.size).sum,
        reportResults.values.map(_.count(_.checksumMatches)).sum,
      )
      AssetCheckResultResponse(summary, results)
    }
  }

  final case class AssetInfoResponse(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    duration: Option[Double] = None,
    fps: Option[Double] = None,
    internalMimeType: Option[String] = None,
    originalMimeType: Option[String] = None,
  )
  object AssetInfoResponse {

    def from(assetInfo: AssetInfo): AssetInfoResponse = {
      val metadata = assetInfo.metadata
      val dim      = metadata.dimensionsOpt
      AssetInfoResponse(
        assetInfo.derivative.filename.toString,
        assetInfo.original.filename.toString,
        assetInfo.originalFilename.toString,
        assetInfo.original.checksum.toString,
        assetInfo.derivative.checksum.toString,
        dim.map(_.width.value),
        dim.map(_.height.value),
        metadata.durationOpt.map(_.value),
        metadata.fpsOpt.map(_.value),
        metadata.internalMimeType.map(_.stringValue),
        metadata.originalMimeType.map(_.stringValue),
      )
    }

    given codec: JsonCodec[AssetInfoResponse] = DeriveJsonCodec.gen[AssetInfoResponse]
    given schema: Schema[AssetInfoResponse]   = DeriveSchema.gen[AssetInfoResponse]
  }

  case class UploadResponse(status: String = "ok")

  object UploadResponse {
    given schema: Schema[UploadResponse] = DeriveSchema.gen[UploadResponse]

    given codec: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]
  }
}

final case class ProjectsEndpoints(base: BaseEndpoints) {
  private val projects = "projects"

  val getProjectsEndpoint = base.secureEndpoint.get
    .in(projects)
    .out(jsonBody[Chunk[ProjectResponse]])
    .out(header[String](HeaderNames.ContentRange))
    .tag(projects)
    .description("Authorization: admin scope required.")

  val getProjectByShortcodeEndpoint = base.secureEndpoint.get
    .in(projects / shortcodePathVar)
    .out(jsonBody[ProjectResponse])
    .tag(projects)
    .description("Authorization: read:project:1234 scope required.")

  val getProjectsChecksumReport = base.secureEndpoint.get
    .in(projects / shortcodePathVar / "checksumreport")
    .out(jsonBody[AssetCheckResultResponse])
    .tag(projects)
    .description("Authorization: read:project:1234 scope required.")

  val deleteProjectsErase = base.secureEndpoint.delete
    .in(projects / shortcodePathVar / "erase")
    .out(jsonBody[ProjectResponse])
    .tag(projects)
    .description(
      """|!ATTENTION! Erase a project with the given shortcode.
         |This will permanently and irrecoverably remove the project and all of its assets.
         |Authorization: admin scope required.""".stripMargin,
    )

  val getProjectsAssetsInfo = base.secureEndpoint.get
    .in(projects / shortcodePathVar / "assets" / assetIdPathVar)
    .out(jsonBody[AssetInfoResponse])
    .tag("assets")
    .description("Authorization: read:project:1234 scope required.")

  val getProjectsAssetsOriginal = base.withUserEndpoint.get
    .in(projects / shortcodePathVar / "assets" / assetIdPathVar / "original")
    .out(header[String]("Content-Disposition"))
    .out(header[String]("Content-Type"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .tag("assets")
    .description(
      """|Offers the original file for download, provided the API permisisons allow.
         |Authorization: JWT bearer token.""".stripMargin,
    )

  given filenameCodec: Codec[String, AssetFilename, CodecFormat.TextPlain] =
    Codec.string.mapEither(AssetFilename.from)(_.value)
  val postProjectAsset = base.secureEndpoint.post
    .in(projects / shortcodePathVar / "assets" / "ingest" / path[AssetFilename]("filename"))
    .in(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .out(jsonBody[AssetInfoResponse])
    .tag("assets")

  val postBulkIngest = base.secureEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .out(statusCode(StatusCode.Accepted))
    .description(
      "Triggers an ingest on the project with the given shortcode. " +
        "The files are expected to be in the `tmp/<project_shortcode>` directory. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        "Authorization: admin scope required.",
    )
    .tag("bulk-ingest")

  val postBulkIngestFinalize = base.secureEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest" / "finalize")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .description(
      "Finalizes the bulk ingest. " +
        "This will remove the files from the `tmp/<project_shortcode>` directory and the directory itself. " +
        "This will remove also the mapping.csv file. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        "Authorization: admin scope required.",
    )
    .tag("bulk-ingest")

  val getBulkIngestMappingCsv = base.secureEndpoint.get
    .in(projects / shortcodePathVar / "bulk-ingest" / "mapping.csv")
    .description(
      "Get the current result of the bulk ingest. " +
        "The result is a csv with the following structure: `original,derivative`. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project." +
        "Authorization: admin scope required.",
    )
    .out(stringBody)
    .out(header(HeaderNames.ContentType, "text/csv"))
    .out(header(HeaderNames.ContentDisposition, "attachment; filename=mapping.csv"))
    .tag("bulk-ingest")

  val postBulkIngestUpload = base.secureEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest" / "ingest" / path[String]("file"))
    .in(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .out(jsonBody[UploadResponse])
    .description(
      "Uploads a file for consumption with the bulk-ingest route." +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project." +
        "Authorization: admin scope required.",
    )
    .tag("bulk-ingest")

  val postExport = base.secureEndpoint.post
    .in(projects / shortcodePathVar / "export")
    .out(header[String]("Content-Disposition"))
    .out(header[String]("Content-Type"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .tag("import/export")
    .description("Authorization: admin scope required.")

  val getImport = base.secureEndpoint
    .in(projects / shortcodePathVar / "import")
    .in(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .in(header("Content-Type", "application/zip"))
    .out(jsonBody[UploadResponse])
    .tag("import/export")
    .description("Authorization: admin scope required.")

  val endpoints =
    List(
      getProjectsEndpoint,
      getProjectByShortcodeEndpoint,
      getProjectsChecksumReport,
      deleteProjectsErase,
      getProjectsAssetsInfo,
      getProjectsAssetsOriginal,
      postProjectAsset,
      postBulkIngest,
      postBulkIngestFinalize,
      getBulkIngestMappingCsv,
      postBulkIngestUpload,
      postExport,
      getImport,
    )
}

object ProjectsEndpoints {
  val shortcodePathVar: EndpointInput.PathCapture[ProjectShortcode] = path[ProjectShortcode]
    .name("shortcode")
    .description("The shortcode of the project must be an exactly 4 characters long hexadecimal string.")
    .example(ProjectShortcode.from("0001").getOrElse(throw Exception("Invalid shortcode.")))

  val assetIdPathVar: EndpointInput.PathCapture[AssetId] = path[AssetId]
    .name("assetId")
    .description("The id of the asset")
    .example(AssetId.from("5RMOnH7RmAY-qKzgr431bg7").getOrElse(throw Exception("Invalid AssetId.")))

  val layer = ZLayer.derive[ProjectsEndpoints]
}
