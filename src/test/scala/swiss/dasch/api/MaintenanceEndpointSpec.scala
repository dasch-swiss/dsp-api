/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.MaintenanceEndpoint.MappingEntry
import swiss.dasch.domain.*
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.domain.SipiImageFormat.Jpg
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConstants.Projects.{ existingProject, nonExistentProject }
import swiss.dasch.test.{ SpecConfigurations, SpecConstants }
import zio.*
import zio.http.*
import zio.json.EncoderOps
import zio.nio.file
import zio.nio.file.Files
import zio.test.*

object MaintenanceEndpointSpec extends ZIOSpecDefault {

  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 1.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private val createOriginalsSuite = {
    def createOriginalsRequest(
        shortcode: ProjectShortcode | String,
        body: List[MappingEntry] = List.empty,
      ) =
      Request
        .post(Body.fromString(body.toJson), URL(Root / "maintenance" / "create-originals" / shortcode.toString))
        .updateHeaders(_.addHeader(Header.ContentType(MediaType.application.json)))

    suite("/maintenance/create-originals")(
      test("should return 404 for a non-existent project") {
        val request = createOriginalsRequest(nonExistentProject)
        for {
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("should return 400 for an invalid project shortcode") {
        val request = createOriginalsRequest("invalid-shortcode")
        for {
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("should return 204 for a project shortcode ") {
        val request = createOriginalsRequest(existingProject)
        for {
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.Accepted)
      },
      test("should return 204 for a project shortcode and create originals for jp2 and jpx assets") {
        def doesOrigExist(asset: Asset, format: SipiImageFormat) = StorageService.getAssetDirectory(asset).flatMap {
          // Since the create original maintenance action is forked into the background
          // we need to wait (i.e. `awaitTrue') for the file to be created.
          assetDir => awaitTrue(Files.exists(assetDir / s"${asset.id}.${format.extension}.orig"))
        }

        def loadAssetInfo(asset: Asset) = StorageService.getAssetDirectory(asset).flatMap {
          // Since the create original maintenance action is forked into the background
          // we need to wait (i.e. `awaitTrue') for the file to be created.
          assetDir => awaitTrue(Files.exists(assetDir / s"${asset.id}.info")) *> AssetInfoService.findByAsset(asset)
        }

        val assetJpx    = Asset("aaaa-a-jpx-without-orig".toAssetId, existingProject)
        val assetJp2    = Asset("bbbb-a-jp2-without-orig".toAssetId, existingProject)
        val testMapping = MappingEntry(s"${assetJpx.id}.jpx", "ORIGINAL.jpg")
        val request     = createOriginalsRequest(existingProject, List(testMapping))

        for {
          response         <- MaintenanceEndpointRoutes.app.runZIO(request).logError
          newOrigExistsJpx <- doesOrigExist(assetJpx, SipiImageFormat.Jpg)
          newOrigExistsJp2 <- doesOrigExist(assetJp2, SipiImageFormat.Tif)
          assetInfoJpx     <- loadAssetInfo(assetJpx)
          assetInfoJp2     <- loadAssetInfo(assetJp2)
          checksumsCorrect <-
            (FileChecksumService.verifyChecksum(assetInfoJpx) <&> FileChecksumService.verifyChecksum(assetInfoJp2))
              .map(_ ++ _)
              .filterOrFail(_.size == 4)(new AssertionError("Expected four checksum results"))
              .map(_.forall(_.checksumMatches == true))
        } yield assertTrue(
          response.status == Status.Accepted,
          newOrigExistsJpx,
          newOrigExistsJp2,
          assetInfoJpx.originalFilename.toString == "ORIGINAL.jpg",
          assetInfoJp2.originalFilename.toString == s"${assetJp2.id}.tif",
          checksumsCorrect,
        )
      },
    ) @@ TestAspect.withLiveClock
  }

  private val needsOriginalsSuite =
    suite("/maintenance/needs-originals should")(
      test("should return 204 and create a report") {
        val request = Request.get(URL(Root / "maintenance" / "needs-originals"))
        for {
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
          projects <- loadReport("needsOriginals_images_only.json")
        } yield assertTrue(response.status == Status.Accepted, projects == Chunk("0001"))
      },
      test("should return 204 and create a extended report") {
        val request = Request.get(URL(Root / "maintenance" / "needs-originals").withQueryParams("imagesOnly=false"))
        for {
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
          projects <- loadReport("needsOriginals.json")
        } yield assertTrue(response.status == Status.Accepted, projects == Chunk("0001"))
      },
    ) @@ TestAspect.withLiveClock

  private def loadReport(name: String) =
    StorageService.getTempDirectory().flatMap { tmpDir =>
      val report = tmpDir / "reports" / name
      awaitTrue(Files.exists(report)) *> StorageService.loadJsonFile[Chunk[String]](report)
    }

  private val needsTopleftCorrectionSuite =
    suite("/maintenance/needs-top-left-correction should")(
      test("should return 204 and create a report") {
        val request = Request.get(URL(Root / "maintenance" / "needs-top-left-correction"))
        for {
          _        <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
          response <- MaintenanceEndpointRoutes.app.runZIO(request).logError
          projects <- loadReport("needsTopLeftCorrection.json")
        } yield assertTrue(response.status == Status.Accepted, projects == Chunk("0001"))
      }
    ) @@ TestAspect.withLiveClock

  val spec = suite("MaintenanceEndpoint")(createOriginalsSuite, needsOriginalsSuite, needsTopleftCorrectionSuite)
    .provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ImageServiceLive.layer,
      ProjectServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      SipiClientMock.layer,
    )
}
