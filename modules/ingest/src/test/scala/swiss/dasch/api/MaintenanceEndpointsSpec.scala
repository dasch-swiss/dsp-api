/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import swiss.dasch.config.Configuration
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.http.*
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.Exif.Image.OrientationValue
import org.knora.webapi.slice.ingest.infrastructure.CommandExecutorMock
import org.knora.webapi.slice.ingest.repo.*

@RunWith(classOf[DspZTestJUnitRunner])
class MaintenanceEndpointsSpec extends ZIOSpecDefault {
  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 3.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[MaintenanceEndpointsHandler](handler =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints),
           )
    response <- app.runZIO(request).logError
  } yield response

  private def loadReport(name: String) =
    StorageService.getTempFolder().flatMap { tmpDir =>
      val report = tmpDir / "reports" / name
      awaitTrue(Files.exists(report)) *> StorageService.loadJsonFile[Chunk[String]](report)
    }

  private val needsTopleftCorrectionSuite =
    suite("/maintenance/needs-top-left-correction should")(
      test("should return 204 and create a report") {
        ZIO.scoped {
          val request = Request
            .get(URL(Path.root / "maintenance" / "needs-top-left-correction"))
            .addHeader(Header.Authorization.name, "Bearer fakeToken")
          for {
            _        <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
            response <- executeRequest(request)
            projects <- loadReport("needsTopLeftCorrection.json")
            status    = response.status
          } yield {
            assertTrue(status == Status.Accepted, projects == Chunk("0001"))
          }
        }
      },
    ) @@ TestAspect.withLiveClock @@ TestAspect.flaky

  private val postMaintenanceActionSuite =
    suite("POST /maintenance/{name} should")(
      test("reject the removed ImportProjectsToDb action as an unknown action") {
        val request = Request
          .post(URL(Path.root / "maintenance" / "ImportProjectsToDb"), Body.empty)
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        ZIO.scoped(executeRequest(request)).map(response => assertTrue(response.status == Status.BadRequest))
      },
    )

  val spec = suite("MaintenanceEndpoint")(needsTopleftCorrectionSuite, postMaintenanceActionSuite)
    .provide(
      AssetInfoServiceLive.layer,
      AuthServiceLive.layer,
      AuthorizationHandlerLive.layer,
      BaseEndpoints.layer,
      CommandExecutorMock.layer,
      FileChecksumServiceLive.layer,
      Configuration.ingestExecutorLayer,
      MaintenanceActionsLive.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandler.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      OtherFilesService.layer,
      ProjectService.layer,
      SipiClientMock.layer,
      SpecConfigurations.jwtConfigDisableAuthLayer,
      SpecConfigurations.storageConfigLayer,
      StillImageService.layer,
      StorageServiceLive.layer,
    )
}
