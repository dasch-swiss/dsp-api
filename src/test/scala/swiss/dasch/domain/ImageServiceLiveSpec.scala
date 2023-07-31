/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.SipiClientMockMethodInvocation.ApplyTopLeftCorrection
import swiss.dasch.api.{ SipiClientMock, SipiClientMockMethodInvocation }
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.*
import zio.nio.file.Files
import zio.test.*

object ImageServiceLiveSpec extends ZIOSpecDefault {

  private val imageAsset = Asset("needs-topleft-correction".toAssetId, "0001".toProjectShortcode)
  private val imageFile  = StorageService.getAssetDirectory(imageAsset).map(_ / s"${imageAsset.id}.jp2")
  private val backupFile = imageFile.map(image => image.parent.map(_ / s"${image.filename}.bak").orNull)

  val spec =
    suite("ImageServiceLiveSpec")(
      test("apply top left should apply correction, create backup and update info file") {
        for {
          _                 <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
          image             <- imageFile
          backup            <- backupFile
          info              <- AssetInfoService.findByAsset(imageAsset)
          infoFile          <- AssetInfoService.getInfoFilePath(imageAsset)
          _                 <- StorageService.saveJsonFile[AssetInfoFileContent](
                                 infoFile,
                                 AssetInfoFileContent(
                                   internalFilename = info.derivative.file.filename.toString,
                                   originalInternalFilename = info.original.file.filename.toString,
                                   originalFilename = info.originalFilename.toString,
                                   checksumOriginal = info.original.checksum.toString,
                                   checksumDerivative = "this-should-be-updated",
                                 ),
                               )
          _                 <- ImageService.applyTopLeftCorrection(image)
          backupExists      <- Files.exists(backup)
          correctionApplied <- SipiClientMock.wasInvoked(ApplyTopLeftCorrection(image, image))
          checksumUpdated   <- FileChecksumService.verifyChecksumDerivative(imageAsset)
        } yield assertTrue(backupExists, correctionApplied, checksumUpdated)
      },
      test("not apply top left if not necessary") {
        for {
          _                    <- SipiClientMock.setOrientation(OrientationValue.Horizontal)
          image                <- imageFile
          backup               <- backupFile
          _                    <- ImageService.applyTopLeftCorrection(image)
          backupNotCreated     <- Files.exists(backup).negate
          correctionNotApplied <- SipiClientMock.wasInvoked(ApplyTopLeftCorrection(image, image)).negate
        } yield assertTrue(backupNotCreated, correctionNotApplied)
      },
    ).provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ImageServiceLive.layer,
      SipiClientMock.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
    )
}
