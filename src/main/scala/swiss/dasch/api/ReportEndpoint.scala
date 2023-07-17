/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.*
import swiss.dasch.domain.*
import zio.Chunk
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.Endpoint
import zio.http.*
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }

import scala.collection.immutable.Map

object ReportEndpoint {

  final case class SingleFileCheckResultResponse(filename: String, checksumMatches: Boolean)
  private object SingleFileCheckResultResponse {
    implicit val encoder: JsonEncoder[SingleFileCheckResultResponse] =
      DeriveJsonEncoder.gen[SingleFileCheckResultResponse]
    implicit val schema: Schema[SingleFileCheckResultResponse]       = DeriveSchema.gen[SingleFileCheckResultResponse]
    def make(result: ChecksumResult): SingleFileCheckResultResponse  =
      SingleFileCheckResultResponse(result.file.filename.toString, result.checksumMatches)
  }

  final case class AssetCheckResultEntry(
      assetId: String,
      originalFilename: String,
      results: List[SingleFileCheckResultResponse],
    )
  private object AssetCheckResultEntry         {
    implicit val encoder: JsonEncoder[AssetCheckResultEntry]                              = DeriveJsonEncoder.gen[AssetCheckResultEntry]
    implicit val schema: Schema[AssetCheckResultEntry]                                    = DeriveSchema.gen[AssetCheckResultEntry]
    def make(assetInfo: AssetInfo, results: Chunk[ChecksumResult]): AssetCheckResultEntry =
      AssetCheckResultEntry(
        assetInfo.asset.id.toString,
        assetInfo.originalFilename.toString,
        results.map(SingleFileCheckResultResponse.make).toList,
      )
  }
  final case class AssetCheckResultSummary(
      numberOfAssets: Int,
      numberOfFiles: Int,
      numberOfChecksumMatches: Int,
    )
  private object AssetCheckResultSummary       {
    implicit val encoder: JsonEncoder[AssetCheckResultSummary] = DeriveJsonEncoder.gen[AssetCheckResultSummary]
    implicit val schema: Schema[AssetCheckResultSummary]       = DeriveSchema.gen[AssetCheckResultSummary]
  }
  final case class AssetCheckResultResponse(summary: AssetCheckResultSummary, results: List[AssetCheckResultEntry])

  private object AssetCheckResultResponse {
    implicit val encoder: JsonEncoder[AssetCheckResultResponse] = DeriveJsonEncoder.gen[AssetCheckResultResponse]
    implicit val schema: Schema[AssetCheckResultResponse]       = DeriveSchema.gen[AssetCheckResultResponse]

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
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  val app = endpoint
    .implement((shortcode: String) =>
      ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap {
        ReportService.checksumReport(_).mapBoth(ApiProblem.internalError, AssetCheckResultResponse.make)
      }
    )
    .toApp
}
