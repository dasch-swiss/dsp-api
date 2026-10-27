/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.repo.*
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations
import org.knora.webapi.slice.ingest.testutil.SpecJwtTokens

@RunWith(classOf[DspZTestJUnitRunner])
class IngestReportEndpointsSpec extends ZIOSpecDefault {
  private val url = URL(Path.root / "ingest" / "report" / "asset-overview")

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[IngestReportServerEndpoints](se =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(se.serverEndpoints),
           )
    response <- ZIO.scoped(app.runZIO(request)).logError
  } yield response

  private val reportWritten = StorageService.getTempFolder().flatMap { tmp =>
    Files.list(tmp / "reports").runCollect.map(_.nonEmpty).orElseSucceed(false)
  }

  val spec = suite("IngestReportEndpoints")(
    test("POST /ingest/report/asset-overview answers 202 and writes the report") {
      for {
        token    <- SpecJwtTokens.adminToken()
        response <- executeRequest(Request.post(url, Body.empty).addHeader(Header.Authorization.Bearer(token)))
        body     <- response.body.asString
        written  <- reportWritten
                     .repeat(Schedule.recurUntil[Boolean](identity) && Schedule.spaced(50.millis))
                     .map(_._1)
                     .timeout(3.seconds)
                     .map(_.getOrElse(false))
      } yield assertTrue(response.status == Status.Accepted, body == "work in progress", written)
    },
    test("POST /ingest/report/asset-overview answers 401 without a token") {
      executeRequest(Request.post(url, Body.empty)).map(response => assertTrue(response.status == Status.Unauthorized))
    },
  ).provide(
    AssetInfoServiceLive.layer,
    SpecConfigurations.ingestExecutorLayer,
    CsvService.layer,
    FileChecksumServiceLive.layer,
    IngestAuthenticator.layer,
    IngestBaseEndpoints.layer,
    IngestReportEndpoints.layer,
    IngestReportRestService.layer,
    IngestReportServerEndpoints.layer,
    MimeTypeGuesser.layer,
    ProjectService.layer,
    ReportService.layer,
    SpecConfigurations.jwtConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  ) @@ TestAspect.withLiveClock
}
