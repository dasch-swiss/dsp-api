/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.*
import swiss.dasch.domain.*
import zio.http.Header.{ ContentDisposition, ContentType }
import zio.http.HttpError.*
import zio.http.Path.Segment.Root
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.EndpointMiddleware.None
import zio.http.endpoint.{ Endpoint, Routes }
import zio.http.{ Header, * }
import zio.json.{ DeriveJsonEncoder, JsonEncoder, JsonError }
import zio.nio.file
import zio.schema.{ DeriveSchema, Schema }
import zio.stream.ZStream
import zio.{ Chunk, Exit, Scope, URIO, ZIO, ZNothing }

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

  final case class AssetCheckResultResponse(assetId: String, results: List[SingleFileCheckResultResponse])
  private object AssetCheckResultResponse {
    implicit val encoder: JsonEncoder[AssetCheckResultResponse] = DeriveJsonEncoder.gen[AssetCheckResultResponse]
    implicit val schema: Schema[AssetCheckResultResponse]       = DeriveSchema.gen[AssetCheckResultResponse]

    def make(report: Report): List[AssetCheckResultResponse]                               =
      report.results.map { case (info, results) => AssetCheckResultResponse.to(info.asset, results) }.toList
    private def to(asset: Asset, results: Chunk[ChecksumResult]): AssetCheckResultResponse =
      AssetCheckResultResponse(asset.id.toString, results.map(SingleFileCheckResultResponse.make).toList)
  }

  private val endpoint = Endpoint
    .get(projects / shortcodePathVar / "checksumreport")
    .out[List[AssetCheckResultResponse]]
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
