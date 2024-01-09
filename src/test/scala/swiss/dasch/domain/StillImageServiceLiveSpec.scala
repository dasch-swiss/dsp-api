/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.*
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.*
import eu.timepit.refined.numeric.Greater.greaterValidate
import swiss.dasch.api.SipiClientMockMethodInvocation.ApplyTopLeftCorrection
import swiss.dasch.api.{SipiClientMock, SipiClientMockMethodInvocation}
import swiss.dasch.domain.DerivativeFile.JpxDerivativeFile
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.domain.RefinedHelper.positiveFrom
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.*
import zio.Exit
import zio.nio.file.{Files, Path}
import zio.test.*
import zio.test.Assertion.{equalTo, fails, hasMessage, isSubtype}

import java.io.IOException

object StillImageServiceLiveSpec extends ZIOSpecDefault {

  private val asset      = AssetRef("needs-topleft-correction".toAssetId, "0001".toProjectShortcode)
  private val imageFile  = StorageService.getAssetDirectory(asset).map(_ / s"${asset.id}.jp2")
  private val backupFile = imageFile.map(image => image.parent.map(_ / s"${image.filename}.bak").orNull)

  val spec = suite("StillImageServiceLive")(
    test("apply top left should apply correction, create backup and update info file") {
      for {
        _        <- SipiClientMock.setOrientation(OrientationValue.Rotate270CW)
        image    <- imageFile
        backup   <- backupFile
        info     <- AssetInfoService.findByAssetRef(asset).map(_.head)
        infoFile <- AssetInfoService.getInfoFilePath(asset)
        _ <- StorageService.saveJsonFile[AssetInfoFileContent](
               infoFile,
               AssetInfoFileContent(
                 internalFilename = info.derivative.filename,
                 originalInternalFilename = info.original.filename,
                 originalFilename = info.originalFilename,
                 checksumOriginal = info.original.checksum,
                 checksumDerivative = // this should not be updated
                   Sha256Hash.unsafeFrom("3c9194324cc5921bef9a19fc8f9f7874114904cc25d43801cdb9364cfa363412")
               )
             )
        _                 <- StillImageService.applyTopLeftCorrection(image)
        backupExists      <- Files.exists(backup)
        correctionApplied <- SipiClientMock.wasInvoked(ApplyTopLeftCorrection(image, image))
        checksumUpdated   <- FileChecksumService.verifyChecksumDerivative(asset)
      } yield assertTrue(backupExists, correctionApplied, checksumUpdated)
    },
    test("not apply top left if not necessary") {
      for {
        _                    <- SipiClientMock.setOrientation(OrientationValue.Horizontal)
        image                <- imageFile
        backup               <- backupFile
        _                    <- StillImageService.applyTopLeftCorrection(image)
        backupNotCreated     <- Files.exists(backup).negate
        correctionNotApplied <- SipiClientMock.wasInvoked(ApplyTopLeftCorrection(image, image)).negate
      } yield assertTrue(backupNotCreated, correctionNotApplied)
    },
    test("createDerivative should create a jpx file with correct name") {
      for {
        assetId    <- AssetId.makeNew
        assetDir   <- StorageService.getAssetDirectory(AssetRef(assetId, "0001".toProjectShortcode))
        _          <- Files.createDirectories(assetDir)
        image       = assetDir / s"$assetId.jp2.orig"
        _          <- Files.createFile(image)
        derivative <- StillImageService.createDerivative(OriginalFile.unsafeFrom(image))
        fileExists <- Files.exists(derivative.toPath)
      } yield assertTrue(fileExists, derivative.toPath.filename.toString == s"$assetId.jpx")
    },
    test("createDerivative should fail if Sipi silently does not transcode the image") {
      for {
        _        <- SipiClientMock.dontTranscode()
        assetId  <- AssetId.makeNew
        assetDir <- StorageService.getAssetDirectory(AssetRef(assetId, "0001".toProjectShortcode))
        _        <- Files.createDirectories(assetDir)
        image     = assetDir / s"$assetId.jp2.orig"
        _        <- Files.createFile(image)
        actual   <- StillImageService.createDerivative(OriginalFile.unsafeFrom(image)).exit
      } yield assertTrue(actual.isFailure)
    },
    test("getDimensions should return Dimensions if Sipi returns them") {
      val dim = Dimensions(positiveFrom(100), positiveFrom(100))
      for {
        _      <- SipiClientMock.setQueryImageDimensions(dim)
        actual <- StillImageService.getDimensions(JpxDerivativeFile.unsafeFrom(Path("images/some-file.jp2")))
      } yield assertTrue(actual == dim)
    },
    test("getDimensions should fail Dimensions if Sipi does not return them") {
      for {
        actual <- StillImageService.getDimensions(JpxDerivativeFile.unsafeFrom(Path("images/some-file.jp2"))).exit
      } yield assert(actual)(
        fails(isSubtype[IOException](hasMessage(equalTo("Could not get dimensions from 'images/some-file.jp2'"))))
      )
    }
  ).provide(
    AssetInfoServiceLive.layer,
    FileChecksumServiceLive.layer,
    MimeTypeGuesser.layer,
    StillImageService.layer,
    SipiClientMock.layer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer
  )
}

object RefinedHelper {
  def positiveFrom(in: Int): Int Refined Positive =
    refineV(in).toOption.getOrElse(throw new IllegalArgumentException(s"$in is not positive"))
}
