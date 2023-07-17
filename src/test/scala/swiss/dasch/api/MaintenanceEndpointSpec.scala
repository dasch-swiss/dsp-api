/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.*
import swiss.dasch.test.SpecConstants.Projects.{ existingProject, nonExistentProject }
import swiss.dasch.test.{ SpecConfigurations, SpecConstants }
import swiss.dasch.test.SpecConstants.*
import zio.http.*
import zio.nio.file
import zio.nio.file.Files
import zio.test.*
import zio.*

object MaintenanceEndpointSpec extends ZIOSpecDefault {

  private def awaitTrue[R, E](awaitThis: ZIO[R, E, Boolean], timeout: Duration = 1.seconds): ZIO[R, E, Boolean] =
    awaitThis.repeatUntil(identity).timeout(timeout).map(_.getOrElse(false))

  private val createOriginalsSuite = {
    def createOriginalsRequest(shortcode: ProjectShortcode | String) =
      Request.post(Body.empty, URL(Root / "maintenance" / "create-originals" / shortcode.toString))
    suite("/maintenance/create-originals")(
      test("should return 404 for a non-existent project") {
        val request = createOriginalsRequest(nonExistentProject)
        for {
          response <- MaintenanceEndpoint.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.NotFound)
      },
      test("should return 400 for an invalid project shortcode") {
        val request = createOriginalsRequest("invalid-shortcode")
        for {
          response <- MaintenanceEndpoint.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("should return 204 for a project shortcode ") {
        val request = createOriginalsRequest(existingProject)
        for {
          response <- MaintenanceEndpoint.app.runZIO(request).logError
        } yield assertTrue(response.status == Status.Accepted)
      },
      test("should return 204 for a project shortcode and create original") {
        val asset   = Asset("1ACilM7l8UQ-EGONbx28BUW".toAssetId, existingProject)
        val request = createOriginalsRequest(asset.belongsToProject)
        for {
          response      <- MaintenanceEndpoint.app.runZIO(request).logError
          assetDir      <- StorageService.getAssetDirectory(asset)
          newOrigExists <- awaitTrue(Files.exists(assetDir / s"${asset.id}.tif.orig"))
        } yield assertTrue(response.status == Status.Accepted, newOrigExists)
      },
    ) @@ TestAspect.withLiveClock
  }

  val spec = suite("MaintenanceEndpoint")(createOriginalsSuite)
    .provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ProjectServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      ZLayer.succeed(SipiClientMock()),
    )
}

final case class SipiClientMock() extends SipiClient {
  override def transcodeImageFile(
      fileIn: file.Path,
      fileOut: file.Path,
      outputFormat: SipiImageFormat,
    ): Task[SipiOutput] = Files.createFile(fileOut).as(SipiOutput("", ""))
}
