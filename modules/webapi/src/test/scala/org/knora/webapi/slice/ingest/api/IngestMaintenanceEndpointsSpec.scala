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
import org.knora.webapi.slice.ingest.domain.Exif.Image.OrientationValue
import org.knora.webapi.slice.ingest.infrastructure.CommandExecutorMock
import org.knora.webapi.slice.ingest.repo.*
import org.knora.webapi.slice.ingest.testutil.SipiClientMock
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations
import org.knora.webapi.slice.ingest.testutil.SpecJwtTokens

@RunWith(classOf[DspZTestJUnitRunner])
class IngestMaintenanceEndpointsSpec extends ZIOSpecDefault {
  private val maintenance = Path.root / "ingest" / "maintenance"

  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 3.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[IngestMaintenanceServerEndpoints](se =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(se.serverEndpoints),
           )
    response <- ZIO.scoped(app.runZIO(request)).logError
  } yield response

  private def executeAsAdmin(request: Request) =
    SpecJwtTokens.adminToken().flatMap(t => executeRequest(request.addHeader(Header.Authorization.Bearer(t))))

  private def loadReport(name: String) =
    StorageService.getTempFolder().flatMap { tmpDir =>
      val report = tmpDir / "reports" / name
      awaitTrue(Files.exists(report)) *> StorageService.loadJsonFile[Chunk[String]](report)
    }

  private def awaitLog(fragments: String*) =
    awaitTrue(ZTestLogger.logOutput.map(_.exists(e => fragments.forall(e.message().contains))))

  private val needsTopLeftCorrectionSuite =
    suite("GET /ingest/maintenance/needs-top-left-correction should")(
      test("return 202 and create a report") {
        for {
          _        <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
          response <- executeAsAdmin(Request.get(URL(maintenance / "needs-top-left-correction")))
          projects <- loadReport("needsTopLeftCorrection.json")
        } yield assertTrue(response.status == Status.Accepted, projects == Chunk("0001"))
      },
    ) @@ TestAspect.flaky

  private def postAction(name: String, restrictTo: List[String] = Nil) = {
    val url = restrictTo.foldLeft(URL(maintenance / name))((u, sc) => u.addQueryParam("restrictToProjects", sc))
    Request.post(url, Body.empty)
  }

  private val postMaintenanceActionSuite =
    suite("POST /ingest/maintenance/{name} should")(
      test("reject the removed ImportProjectsToDb action as an unknown action") {
        executeAsAdmin(postAction("ImportProjectsToDb")).map(response =>
          assertTrue(response.status == Status.BadRequest),
        )
      },
      test("run MigrateSizes over all projects for an admin token") {
        for {
          response <- executeAsAdmin(postAction("MigrateSizes"))
          logged   <- awaitLog("MigrateSizes for all projects finished with 0 failed", "AssetSizeMigrationReport(2,")
        } yield assertTrue(response.status == Status.Accepted, logged)
      },
      test("run MigrateSizes restricted to the given projects") {
        for {
          response <- executeAsAdmin(postAction("MigrateSizes", List("0001")))
          logged   <- awaitLog("MigrateSizes for projects 0001 finished", "AssetSizeMigrationReport(2,")
        } yield assertTrue(response.status == Status.Accepted, logged)
      },
      test("run MigrateMimeTypes over all projects for an admin token") {
        for {
          response <- executeAsAdmin(postAction("MigrateMimeTypes"))
          logged   <- awaitLog("MigrateMimeTypes for all projects finished", "AssetMimeTypeMigrationReport(2,")
        } yield assertTrue(response.status == Status.Accepted, logged)
      },
      test("run MigrateMimeTypes restricted to the given projects") {
        for {
          response <- executeAsAdmin(postAction("MigrateMimeTypes", List("0001")))
          logged   <- awaitLog("MigrateMimeTypes for projects 0001 finished", "AssetMimeTypeMigrationReport(2,")
        } yield assertTrue(response.status == Status.Accepted, logged)
      },
      test("refuse the migrations without admin scope, as every other action") {
        for {
          token  <- SpecJwtTokens.tokenWithScope("write:project:0001")
          sizes  <- executeRequest(postAction("MigrateSizes").addHeader(Header.Authorization.Bearer(token)))
          mimes  <- executeRequest(postAction("MigrateMimeTypes").addHeader(Header.Authorization.Bearer(token)))
          body   <- sizes.body.asString
          logged <- awaitLog("finished").map(!_)
        } yield assertTrue(
          sizes.status == Status.Unauthorized,
          mimes.status == Status.Unauthorized,
          body == """{"reason":"Admin permissions required."}""",
          logged,
        )
      },
    )

  val spec = suite("IngestMaintenanceEndpoints")(needsTopLeftCorrectionSuite, postMaintenanceActionSuite)
    .provide(
      AssetInfoServiceLive.layer,
      AssetMimeTypeMigrationService.layer,
      AssetSizeMigrationService.layer,
      CommandExecutorMock.layer,
      FileChecksumServiceLive.layer,
      IngestAuthenticator.layer,
      IngestBaseEndpoints.layer,
      IngestMaintenanceEndpoints.layer,
      IngestMaintenanceRestService.layer,
      IngestMaintenanceServerEndpoints.layer,
      MaintenanceActionsLive.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      OtherFilesService.layer,
      ProjectService.layer,
      SipiClientMock.layer,
      SpecConfigurations.jwtConfigLayer,
      SpecConfigurations.storageConfigLayer,
      StillImageService.layer,
      StorageServiceLive.layer,
    ) @@ TestAspect.withLiveClock
}
