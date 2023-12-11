/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import swiss.dasch.domain.*
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.domain.SipiImageFormat.Jpg
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConstants.Projects.{existingProject, nonExistentProject}
import swiss.dasch.test.{SpecConfigurations, SpecConstants}
import zio.*
import zio.http.*
import zio.json.EncoderOps
import zio.nio.file
import zio.nio.file.Files
import zio.test.*

object MaintenanceEndpointsSpec extends ZIOSpecDefault {

  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 1.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private def executeRequest(request: Request) = for {
    app <- ZIO.serviceWith[MaintenanceEndpointsHandler](handler =>
             ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints)
           )
    response <- app.runZIO(request).logError
  } yield response

  private def executeRequestAndAssertStatus(request: Request, expectedStatus: Status) =
    executeRequest(request).map(_.status).map(status => assertTrue(status == expectedStatus))

  private val createOriginalsSuite = {
    def createOriginalsRequest(
      shortcode: ProjectShortcode | String,
      body: List[MappingEntry] = List.empty
    ) =
      Request
        .post(URL(Root / "maintenance" / "create-originals" / shortcode.toString), Body.fromString(body.toJson))
        .updateHeaders(
          _.addHeader(Header.ContentType(MediaType.application.json))
            .addHeader(Header.Authorization.name, "Bearer fakeToken")
        )

    suite("/maintenance/create-originals")(
      test("should return 404 for a non-existent project") {
        executeRequestAndAssertStatus(
          createOriginalsRequest(nonExistentProject),
          Status.NotFound
        )
      },
      test("should return 400 for an invalid project shortcode") {
        executeRequestAndAssertStatus(
          createOriginalsRequest("invalid-shortcode"),
          Status.BadRequest
        )
      },
      test("should return 204 for a project shortcode ") {
        executeRequestAndAssertStatus(
          createOriginalsRequest(existingProject),
          Status.Accepted
        )
      },
      test("should return 204 for a project shortcode and create originals for jp2 and jpx assets") {
        def doesOrigExist(asset: AssetRef, format: SipiImageFormat) = StorageService.getAssetDirectory(asset).flatMap {
          // Since the create original maintenance action is forked into the background
          // we need to wait (i.e. `awaitTrue') for the file to be created.
          assetDir => awaitTrue(Files.exists(assetDir / s"${asset.id}.${format.extension}.orig"))
        }

        def loadAssetInfo(asset: AssetRef) = StorageService.getAssetDirectory(asset).flatMap {
          // Since the create original maintenance action is forked into the background
          // we need to wait (i.e. `awaitTrue') for the file to be created.
          assetDir => awaitTrue(Files.exists(assetDir / s"${asset.id}.info")) *> AssetInfoService.findByAssetRef(asset)
        }

        val assetJpx    = AssetRef("aaaa-a-jpx-without-orig".toAssetId, existingProject)
        val assetJp2    = AssetRef("bbbb-a-jp2-without-orig".toAssetId, existingProject)
        val testMapping = MappingEntry(s"${assetJpx.id}.jpx", "ORIGINAL.jpg")
        val request     = createOriginalsRequest(existingProject, List(testMapping))

        for {
          response         <- executeRequest(request)
          newOrigExistsJpx <- doesOrigExist(assetJpx, SipiImageFormat.Jpg)
          newOrigExistsJp2 <- doesOrigExist(assetJp2, SipiImageFormat.Tif)
          assetInfoJpx     <- loadAssetInfo(assetJpx)
          assetInfoJp2     <- loadAssetInfo(assetJp2)
          checksumsCorrect <-
            (FileChecksumService.verifyChecksum(assetInfoJpx) <&> FileChecksumService.verifyChecksum(assetInfoJp2))
              .map(_ ++ _)
              .filterOrFail(_.size == 4)(new AssertionError("Expected four checksum results"))
              .map(_.forall(_.checksumMatches == true))
          status = response.status
        } yield assertTrue(
          status == Status.Accepted,
          newOrigExistsJpx,
          newOrigExistsJp2,
          assetInfoJpx.originalFilename.toString == "ORIGINAL.jpg",
          assetInfoJp2.originalFilename.toString == s"${assetJp2.id}.tif",
          checksumsCorrect
        )
      }
    ) @@ TestAspect.withLiveClock
  }

  private val needsOriginalsSuite =
    suite("/maintenance/needs-originals should")(
      test("should return 204 and create a report") {
        val request = Request
          .get(URL(Root / "maintenance" / "needs-originals"))
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        for {
          response <- executeRequest(request)
          projects <- loadReport("needsOriginals_images_only.json")
          status    = response.status
        } yield {
          assertTrue(status == Status.Accepted, projects == Chunk("0001"))
        }
      },
      test("should return 204 and create an extended report") {
        val request = Request
          .get(URL(Root / "maintenance" / "needs-originals").queryParams("imagesOnly=false"))
          .addHeader(Header.Authorization.name, "Bearer fakeToken")
        for {
          response <- executeRequest(request)
          projects <- loadReport("needsOriginals.json")
          status    = response.status
        } yield {
          assertTrue(status == Status.Accepted, projects == Chunk("0001"))
        }
      }
    ) @@ TestAspect.withLiveClock

  private def loadReport(name: String) =
    StorageService.getTempDirectory().flatMap { tmpDir =>
      val report = tmpDir / "reports" / name
      awaitTrue(Files.exists(report)) *> StorageService.loadJsonFile[Chunk[String]](report)
    }

  private val needsTopleftCorrectionSuite =
    suite("/maintenance/needs-top-left-correction should")(
      test("should return 204 and create a report") {
        val request = Request
          .get(URL(Root / "maintenance" / "needs-top-left-correction"))
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
    ) @@ TestAspect.withLiveClock

  val spec = suite("MaintenanceEndpoint")(createOriginalsSuite, needsOriginalsSuite, needsTopleftCorrectionSuite)
    .provide(
      AssetInfoServiceLive.layer,
      AuthServiceLive.layer,
      FileChecksumServiceLive.layer,
      StillImageServiceLive.layer,
      BaseEndpoints.layer,
      MaintenanceActionsLive.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandler.layer,
      ProjectServiceLive.layer,
      SipiClientMock.layer,
      SpecConfigurations.storageConfigLayer,
      SpecConfigurations.jwtConfigDisableAuthLayer,
      StorageServiceLive.layer
    )
}
