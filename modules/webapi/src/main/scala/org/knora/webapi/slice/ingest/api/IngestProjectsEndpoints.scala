/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.HeaderNames
import sttp.model.StatusCode
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.EndpointInput
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import zio.Chunk
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.schema.DeriveSchema
import zio.schema.Schema

import org.knora.webapi.slice.ingest.api.IngestProjectsEndpoints.assetIdPathVar
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpoints.shortcodePathVar
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpointsResponses.*
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.AugmentedPath.ProjectFolder
import org.knora.webapi.slice.ingest.repo.*

object IngestProjectsEndpointsResponses {
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
    given schema: Schema[SingleFileCheckResultResponse]   = DeriveSchema.gen[SingleFileCheckResultResponse]
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
    given schema: Schema[AssetCheckResultEntry]   = DeriveSchema.gen[AssetCheckResultEntry]
  }

  final case class AssetCheckResultSummary(numberOfAssets: Int, numberOfFiles: Int, numberOfChecksumMatches: Int)

  private object AssetCheckResultSummary {
    given codec: JsonCodec[AssetCheckResultSummary] = DeriveJsonCodec.gen[AssetCheckResultSummary]
    given schema: Schema[AssetCheckResultSummary]   = DeriveSchema.gen[AssetCheckResultSummary]
  }

  final case class AssetCheckResultResponse(summary: AssetCheckResultSummary, results: List[AssetCheckResultEntry])

  object AssetCheckResultResponse {
    given codec: JsonCodec[AssetCheckResultResponse] = DeriveJsonCodec.gen[AssetCheckResultResponse]
    given schema: Schema[AssetCheckResultResponse]   = DeriveSchema.gen[AssetCheckResultResponse]

    def from(report: ChecksumReport): AssetCheckResultResponse = {
      val reportResults = report.results
      val results       = reportResults.map { case (info, checksum) => AssetCheckResultEntry.from(info, checksum) }.toList
      val summary       = AssetCheckResultSummary(
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
    sizeOriginal: Option[Long] = None,
    sizeDerivative: Option[Long] = None,
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
        assetInfo.original.size.map(_.value),
        assetInfo.derivative.size.map(_.value),
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
}

final class IngestProjectsEndpoints(base: IngestBaseEndpoints) {
  private val projects = "projects"

  private val writeScopeRequired =
    "Authorization: write:project:1234 scope required " +
      "(SystemAdmins + ProjectAdmins + users with ProjectResourceCreate permissions)."

  private val readScopeRequired =
    "Authorization: read:project:1234 scope required " +
      "(SystemAdmins + ProjectAdmins + users with read/write permissions)."

  val getProjects = base.securedEndpoint.get
    .in(projects)
    .out(jsonBody[Chunk[ProjectResponse]])
    .out(header[String](HeaderNames.ContentRange))
    .tag(projects)
    .description("Authorization: admin scope required (SystemAdmins only).")

  val getProjectByShortcode = base.securedEndpoint.get
    .in(projects / shortcodePathVar)
    .out(jsonBody[ProjectResponse])
    .tag(projects)
    .description(readScopeRequired)

  val getProjectChecksumReport = base.securedEndpoint.get
    .in(projects / shortcodePathVar / "checksumreport")
    .out(jsonBody[AssetCheckResultResponse])
    .tag(projects)
    .description(readScopeRequired)

  val deleteProjectErase = base.securedEndpoint.delete
    .in(projects / shortcodePathVar / "erase")
    .out(jsonBody[ProjectResponse])
    .tag(projects)
    .description(
      """|!ATTENTION! Erase a project with the given shortcode.
         |This will permanently and irrecoverably remove the project and all of its assets.
         |Authorization: admin scope required (SystemAdmins only).""".stripMargin,
    )

  val getAssetInfo = base.securedEndpoint.get
    .in(projects / shortcodePathVar / "assets" / assetIdPathVar)
    .out(jsonBody[AssetInfoResponse])
    .tag("assets")
    .description(readScopeRequired)

  val getAssetOriginal = base.withPrincipalEndpoint.get
    .in(projects / shortcodePathVar / "assets" / assetIdPathVar / "original")
    .out(header[String]("Content-Disposition"))
    .out(header[String]("Content-Type"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .tag("assets")
    .description(
      """|Offers the original file for download, provided the API permisisons allow.
         |Authorization: JWT bearer token (any authenticated user).""".stripMargin,
    )

  private given filenameCodec: Codec[String, AssetFilename, CodecFormat.TextPlain] =
    Codec.string.mapEither(AssetFilename.from)(_.value)

  val postAssetIngest = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "assets" / "ingest" / path[AssetFilename]("filename"))
    .in(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .out(jsonBody[AssetInfoResponse])
    .tag("assets")
    .description(s"Ingest a single asset file to the project. $writeScopeRequired")

  val postBulkIngest = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .out(statusCode(StatusCode.Accepted))
    .description(
      "Triggers an ingest on the project with the given shortcode. " +
        "The files are expected to be in the `tmp/<project_shortcode>` directory. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        writeScopeRequired,
    )
    .tag("bulk-ingest")

  val postBulkIngestFinalize = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest" / "finalize")
    .out(jsonBody[ProjectResponse].example(ProjectResponse("0001")))
    .description(
      "Finalizes the bulk ingest. " +
        "This will remove the files from the `tmp/<project_shortcode>` directory and the directory itself. " +
        "This will remove also the mapping.csv file. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        writeScopeRequired,
    )
    .tag("bulk-ingest")

  val getBulkIngestMappingCsv = base.securedEndpoint.get
    .in(projects / shortcodePathVar / "bulk-ingest" / "mapping.csv")
    .description(
      "Get the current result of the bulk ingest. " +
        "The result is a csv with the following structure: `original,derivative`. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        writeScopeRequired,
    )
    .out(stringBody)
    .out(header(HeaderNames.ContentType, "text/csv"))
    .out(header(HeaderNames.ContentDisposition, "attachment; filename=mapping.csv"))
    .tag("bulk-ingest")

  val postBulkIngestUpload = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "bulk-ingest" / "ingest" / path[String]("file"))
    .in(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
    .out(jsonBody[UploadResponse])
    .description(
      "Uploads a file for consumption with the bulk-ingest route. " +
        "Will return 409 Conflict if a bulk-ingest is currently running for the project. " +
        writeScopeRequired,
    )
    .tag("bulk-ingest")

  val postExport = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "export")
    .out(header[String]("Content-Disposition"))
    .out(header[String]("Content-Type"))
    .out(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .tag("import/export")
    .description("Authorization: admin scope required (SystemAdmins only).")

  val postImport = base.securedEndpoint.post
    .in(projects / shortcodePathVar / "import")
    .in(streamBinaryBody(ZioStreams)(CodecFormat.Zip()))
    .in(header("Content-Type", "application/zip"))
    .out(jsonBody[UploadResponse])
    .tag("import/export")
    .description("Authorization: admin scope required (SystemAdmins only).")
}

object IngestProjectsEndpoints {
  val shortcodePathVar: EndpointInput.PathCapture[ProjectShortcode] = path[ProjectShortcode]
    .name("shortcode")
    .description("The shortcode of the project must be an exactly 4 characters long hexadecimal string.")
    .example(ProjectShortcode.unsafeFrom("0001"))

  val assetIdPathVar: EndpointInput.PathCapture[AssetId] = path[AssetId]
    .name("assetId")
    .description("The id of the asset")
    .example(AssetId.unsafeFrom("5RMOnH7RmAY-qKzgr431bg7"))

  val layer = ZLayer.derive[IngestProjectsEndpoints]
}
