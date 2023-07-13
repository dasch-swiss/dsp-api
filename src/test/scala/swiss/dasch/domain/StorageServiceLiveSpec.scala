/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConstants.Assets.existingAsset
import swiss.dasch.test.SpecConstants.Projects.existingProject
import zio.*
import zio.nio.file.{ Files, Path }
import zio.test.*

import java.time.format.DateTimeFormatter
import java.time.{ Instant, ZoneId, ZoneOffset }
import java.util.UUID

object StorageServiceLiveSpec extends ZIOSpecDefault {

  val spec = suite("StorageServiceLiveSpec")(
    test("should return the path of the folder where the asset is stored") {
      for {
        assetPath <- ZIO.serviceWith[StorageConfig](_.assetPath)
        actual    <-
          StorageService.getAssetDirectory(Asset("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode))
      } yield assertTrue(actual == assetPath / "0001" / "fg" / "il")
    },
    test("should return asset path") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.assetPath)
        actual   <- StorageService.getAssetDirectory()
      } yield assertTrue(expected == actual)
    },
    test("should return temp path") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.tempPath)
        actual   <- StorageService.getTempDirectory()
      } yield assertTrue(expected == actual)
    },
    test("should return project directory") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.assetPath).map(_ / existingProject.toString)
        actual   <- StorageService.getProjectDirectory(existingProject)
      } yield assertTrue(expected == actual)
    },
    test("should load asset info file") {
      val asset = Asset("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode)
      val name  = NonEmptyString.unsafeFrom("250x250.jp2")
      for {
        projectPath <- ZIO.serviceWith[StorageConfig](_.assetPath).map(_ / asset.belongsToProject.toString)
        expected     = AssetInfo(
                         asset = asset,
                         original = FileAndChecksum(
                           projectPath / "fg" / "il" / s"${asset.id.toString}.jp2.orig",
                           "fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c".toSha256Hash,
                         ),
                         originalFilename = name,
                         derivative = FileAndChecksum(
                           projectPath / "fg" / "il" / s"${asset.id.toString}.jp2",
                           "0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc".toSha256Hash,
                         ),
                       )
        actual      <- AssetInfoService.findByAsset(asset)
      } yield assertTrue(expected == actual)
    },
    suite("create temp directory scoped")(
      test("should create a temp directory") {
        ZIO.scoped {
          for {
            now                <- Clock.instant
            _                  <- TestClock.setTime(now)
            testDirName        <- Random.nextUUID.map(_.toString)
            tempDir            <- StorageService.createTempDirectoryScoped(testDirName)
            exists             <- Files.isDirectory(tempDir)
            containsName        = tempDir.filename.toString == testDirName
            parentNameIsCorrect =
              tempDir
                .parent
                .exists(
                  _.filename.toString == DateTimeFormatter
                    .ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.from(ZoneOffset.UTC))
                    .format(now)
                )
          } yield assertTrue(exists, containsName, parentNameIsCorrect)
        }
      },
      test("Should remove directory and content after scope") {
        for {
          testDirName <- Random.nextUUID.map(_.toString)
          tempDir     <-
            ZIO.scoped(
              StorageService.createTempDirectoryScoped(testDirName).tap(p => Files.createFile(p / "test.txt"))
            )
          isRemoved   <- Files.notExists(tempDir)
        } yield assertTrue(isRemoved)
      },
    ),
  ).provide(AssetInfoServiceLive.layer, StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
