/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.{ HeaderNames, StatusCode }
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import sttp.tapir.{ CodecFormat, EndpointInput }
import swiss.dasch.api.ProjectsEndpoints.shortcodePathVar
import swiss.dasch.api.ProjectsEndpointsResponses.{ AssetCheckResultResponse, ProjectResponse, UploadResponse }
import swiss.dasch.domain.{ AssetInfo, ChecksumResult, ProjectShortcode, Report }
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }
import zio.{ Chunk, ZLayer }

object ProjectsEndpointsResponses {
  final case class ProjectResponse(id: String)

  object ProjectResponse {
    def make(shortcode: ProjectShortcode): ProjectResponse = ProjectResponse(shortcode.value)

    given schema: Schema[ProjectResponse]   = DeriveSchema.gen[ProjectResponse]
    given codec: JsonCodec[ProjectResponse] = DeriveJsonCodec.gen[ProjectResponse]
  }

  final case class SingleFileCheckResultResponse(filename: String, checksumMatches: Boolean)

  private object SingleFileCheckResultResponse {
    def make(result: ChecksumResult): SingleFileCheckResultResponse =
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

    def make(assetInfo: AssetInfo, results: Chunk[ChecksumResult]): AssetCheckResultEntry =
      AssetCheckResultEntry(
        assetInfo.asset.id.toString,
        assetInfo.originalFilename.toString,
        results.map(SingleFileCheckResultResponse.make).toList,
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

    def make(report: Report): AssetCheckResultResponse = {
      val reportResults = report.results
      val results       = reportResults.map { case (info, checksum) => AssetCheckResultEntry.make(info, checksum) }.toList
      val summary       = AssetCheckResultSummary(
        reportResults.keys.size,
        reportResults.values.map(_.size).sum,
        reportResults.values.map(_.count(_.checksumMatches)).sum,
      )
      AssetCheckResultResponse(summary, results)
    }
  }

  case class UploadResponse(status: String = "ok")

  object UploadResponse {
    given schema: Schema[UploadResponse] = DeriveSchema.gen[UploadResponse]

    given codec: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]
  }
}

final case class ProjectsEndpoints(base: BaseEndpoints) {

  private val projects = "projects"

  val getProjectsEndpoint = base
    .secureEndpoint
    .get
    .in(projects)
    .out(jsonBody[Chunk[ProjectResponse]])
    .out(header[String](HeaderNames.ContentRange))
    .tag(projects)

  val getProjectByShortcodeEndpoint = base
    .secureEndpoint
    .get
    .in(projects / shortcodePathVar)
    .out(jsonBody[ProjectResponse])
    .tag(projects)

  val getProjectsChecksumReport = base
    .secureEndpoint
    .get
    .in(projects / shortcodePathVar / "checksumreport")
    .out(jsonBody[AssetCheckResultResponse])
    .tag(projects)

  val postBulkIngest = base
    .secureEndpoint
    .post
    .in(projects / shortcodePathVar / "bulk-ingest")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .out(statusCode(StatusCode.Accepted))
    .description(
      "Triggers an ingest on the project with the given shortcode. " +
        "Currently only supports ingest of images. " +
        "The files are expected to be in the `tmp/<project_shortcode>` directory."
    )
    .tag(projects)

  val postExport = base
    .secureEndpoint
    .post
    .in(projects / shortcodePathVar / "export")
    .out(header[String]("Content-Disposition"))
    .out(header[String]("Content-Type"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .tag(projects)

  val getImport = base
    .secureEndpoint
    .in(projects / shortcodePathVar / "import")
    .in(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .in(header("Content-Type", "application/zip"))
    .out(jsonBody[UploadResponse])
    .tag(projects)

  val endpoints =
    List(
      getProjectsEndpoint,
      getProjectByShortcodeEndpoint,
      getProjectsChecksumReport,
      postBulkIngest,
      postExport,
      getImport,
    )
}

object ProjectsEndpoints {

  val shortcodePathVar: EndpointInput.PathCapture[ProjectShortcode] = path[ProjectShortcode]
    .name("shortcode")
    .description("The shortcode of the project must be an exactly 4 characters long hexadecimal string.")
    .example(ProjectShortcode.from("0001").getOrElse(throw Exception("Invalid shortcode.")))

  val layer = ZLayer.derive[ProjectsEndpoints]
}
