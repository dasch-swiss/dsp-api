/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import swiss.dasch.domain.*
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.util.TestUtils
import zio.*
import zio.http.*
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ReportEndpointsSpec extends ZIOSpecDefault {

  private def postAssetOverview(token: Option[String]) = {
    val request = Request.post(URL(Path.root / "report" / "asset-overview"), Body.empty)
    ZIO.scoped(for {
      app <- ZIO.serviceWith[ReportEndpointsHandler](handler =>
               ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints),
             )
      response <- app.runZIO(token.fold(request)(t => request.addHeader(Header.Authorization.name, s"Bearer $t")))
      body     <- response.body.asString
    } yield (response.status, body))
  }

  // The report job is forked: wait until its writer has closed the report (a non-empty file), or the job would
  // outlive the scoped temp storage.
  private val assetOverviewReportWritten =
    StorageService.getTempFolder().flatMap { tmpDir =>
      val reports   = tmpDir / "reports"
      val hasReport = Files
        .list(reports)
        .filter(_.filename.toString.startsWith("asset_overview_report"))
        .mapZIO(Files.size)
        .runCollect
        .map(_.exists(_ > 0))
      (Files.exists(reports) && hasReport)
        .repeat(Schedule.recurUntil[Boolean](identity) && Schedule.spaced(20.millis))
        .map(_._1)
        .timeout(3.seconds)
        .map(_.getOrElse(false))
    }

  private val assetOverviewSuite = suite("POST /report/asset-overview should")(
    test("return 401 without a token") {
      for {
        (status, _) <- postAssetOverview(None)
      } yield assertTrue(status == Status.Unauthorized)
    },
    test("return 401 for a token without admin scope") {
      for {
        token          <- SpecJwtTokens.createToken(scope = Some("write:project:0001"))
        (status, body) <- postAssetOverview(Some(token))
      } yield assertTrue(status == Status.Unauthorized, body.contains("Admin permissions required."))
    },
    test("accept the request for a token with admin scope") {
      for {
        token          <- SpecJwtTokens.createToken(scope = Some("admin"))
        (status, body) <- postAssetOverview(Some(token))
        reportWritten  <- assetOverviewReportWritten
      } yield assertTrue(status == Status.Accepted, body == "work in progress", reportWritten)
    },
  ) @@ TestAspect.withLiveClock

  val spec = suite("ReportEndpoints")(assetOverviewSuite)
    .provide(
      AssetInfoServiceLive.layer,
      AuthServiceLive.layer,
      AuthorizationHandlerLive.layer,
      BaseEndpoints.layer,
      CsvService.layer,
      FileChecksumServiceLive.layer,
      MimeTypeGuesser.layer,
      ProjectRepositoryLive.layer,
      ProjectService.layer,
      ReportEndpoints.layer,
      ReportEndpointsHandler.layer,
      ReportService.layer,
      SpecConfigurations.jwtConfigLayer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      TestUtils.testDbLayerWithEmptyDb,
    )
}
