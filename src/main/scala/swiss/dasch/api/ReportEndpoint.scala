/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.*
import swiss.dasch.api.ApiProblem.{ BadRequest, InternalServerError, NotFound }
import swiss.dasch.domain.*
import zio.Chunk
import zio.http.*
import zio.http.codec.*
import zio.http.endpoint.Endpoint
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }

import scala.collection.immutable.Map

object ReportEndpoint {

  final case class SingleFileCheckResultResponse(filename: String, checksumMatches: Boolean)
  private object SingleFileCheckResultResponse {

    def make(result: ChecksumResult): SingleFileCheckResultResponse =
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

    def make(assetInfo: AssetInfo, results: Chunk[ChecksumResult]): AssetCheckResultEntry =
      AssetCheckResultEntry(
        assetInfo.asset.id.toString,
        assetInfo.originalFilename.toString,
        results.map(SingleFileCheckResultResponse.make).toList,
      )

    given codec: JsonCodec[AssetCheckResultEntry] = DeriveJsonCodec.gen[AssetCheckResultEntry]
    given schema: Schema[AssetCheckResultEntry]   = DeriveSchema.gen[AssetCheckResultEntry]
  }

  final case class AssetCheckResultSummary(
      numberOfAssets: Int,
      numberOfFiles: Int,
      numberOfChecksumMatches: Int,
    )
  private object AssetCheckResultSummary {
    given codec: JsonCodec[AssetCheckResultSummary] = DeriveJsonCodec.gen[AssetCheckResultSummary]
    given schema: Schema[AssetCheckResultSummary]   = DeriveSchema.gen[AssetCheckResultSummary]
  }
  final case class AssetCheckResultResponse(summary: AssetCheckResultSummary, results: List[AssetCheckResultEntry])

  private object AssetCheckResultResponse {
    given codec: JsonCodec[AssetCheckResultResponse] = DeriveJsonCodec.gen[AssetCheckResultResponse]
    given schema: Schema[AssetCheckResultResponse]   = DeriveSchema.gen[AssetCheckResultResponse]

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

  private val endpoint = Endpoint
    .get(projects / shortcodePathVar / "checksumreport")
    .out[AssetCheckResultResponse]
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )

  val app = endpoint
    .implement((shortcode: String) =>
      ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap {
        ReportService.checksumReport(_).mapBoth(ApiProblem.InternalServerError(_), AssetCheckResultResponse.make)
      }
    )
    .toApp
}
