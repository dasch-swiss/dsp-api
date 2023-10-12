/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConstants.Projects.existingProject
import zio.*
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.nio.file.Files
import zio.test.*
import zio.test.Assertion.failsWithA

import java.nio.file.NoSuchFileException
import java.text.ParseException
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZoneOffset}

object StorageServiceLiveSpec extends ZIOSpecDefault {

  final private case class SomeJsonContent(value: String)
  private object SomeJsonContent {
    given codec: JsonCodec[SomeJsonContent] = DeriveJsonCodec.gen[SomeJsonContent]
  }

  val spec = suite("StorageServiceLiveSpec")(
    test("should create original file in asset directory") {
      ZIO.scoped {
        for {
          // given
          tmp     <- Files.createTempDirectoryScoped(Some("test"), List())
          essence  = tmp / "test.txt"
          _       <- Files.createFile(essence)
          assetId <- AssetId.makeNew
          asset    = SimpleAsset(assetId, "0001".toProjectShortcode)
          // when
          original <- StorageService.createOriginalFileInAssetDir(essence, asset)
          // then
          fileExist <- Files.exists(original.toPath)
          assetDir  <- StorageService.getAssetDirectory(asset)
        } yield assertTrue(
          fileExist,
          original.filename == s"${assetId.toString}.txt.orig",
          original.toPath.parent.contains(assetDir)
        )
      }
    },
    test("should return the path of the folder where the asset is stored") {
      for {
        assetPath <- ZIO.serviceWith[StorageConfig](_.assetPath)
        actual <-
          StorageService.getAssetDirectory(SimpleAsset("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode))
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
    test("should return bulk ingest import folder") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.tempPath)
        actual   <- StorageService.getBulkIngestImportFolder(existingProject)
      } yield assertTrue(actual == expected / "import" / existingProject.value)
    },
    test("should return project directory") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.assetPath).map(_ / existingProject.toString)
        actual   <- StorageService.getProjectDirectory(existingProject)
      } yield assertTrue(expected == actual)
    },
    test("should load asset info file") {
      val asset = SimpleAsset("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode)
      val name  = NonEmptyString.unsafeFrom("250x250.jp2")
      for {
        projectPath <- ZIO.serviceWith[StorageConfig](_.assetPath).map(_ / asset.belongsToProject.toString)
        expected = AssetInfo(
                     asset = asset,
                     original = FileAndChecksum(
                       projectPath / "fg" / "il" / s"${asset.id.toString}.jp2.orig",
                       "fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c".toSha256Hash
                     ),
                     originalFilename = name,
                     derivative = FileAndChecksum(
                       projectPath / "fg" / "il" / s"${asset.id.toString}.jp2",
                       "0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc".toSha256Hash
                     )
                   )
        actual <- AssetInfoService.findByAsset(asset)
      } yield assertTrue(expected == actual)
    },
    suite("create temp directory scoped")(
      test("should create a temp directory") {
        ZIO.scoped {
          for {
            now         <- Clock.instant
            _           <- TestClock.setTime(now)
            testDirName <- Random.nextUUID.map(_.toString)
            tempDir     <- StorageService.createTempDirectoryScoped(testDirName)
            exists      <- Files.isDirectory(tempDir)
            containsName = tempDir.filename.toString == testDirName
            parentNameIsCorrect =
              tempDir.parent
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
          tempDir <-
            ZIO.scoped(
              StorageService.createTempDirectoryScoped(testDirName).tap(p => Files.createFile(p / "test.txt"))
            )
          isRemoved <- Files.notExists(tempDir)
        } yield assertTrue(isRemoved)
      }
    ),
    suite("load and save json files")(
      test("should overwrite (i.e. create new) and load a json file") {
        ZIO.scoped {
          val expected = SomeJsonContent("test")
          for {
            tempDir <- Files.createTempDirectoryScoped(Some("test"), List.empty)
            testFile = tempDir / "create-new-test.json"
            _       <- StorageService.saveJsonFile(testFile, expected)
            actual  <- StorageService.loadJsonFile[SomeJsonContent](testFile)
          } yield assertTrue(actual == expected)
        }
      },
      test("should overwrite existing file and load a json file") {
        ZIO.scoped {
          val expected = SomeJsonContent("test-expected")
          for {
            tempDir <- Files.createTempDirectoryScoped(Some("test"), List.empty)
            testFile = tempDir / "overwrite-test.json"
            _       <- StorageService.saveJsonFile(testFile, SomeJsonContent("test-this-should-be-overwritten"))
            _       <- StorageService.saveJsonFile(testFile, expected)
            actual  <- StorageService.loadJsonFile[SomeJsonContent](testFile)
          } yield assertTrue(actual == expected)
        }
      },
      test("should fail to load a non-existing json file") {
        ZIO.scoped {
          for {
            tempDir <- Files.createTempDirectoryScoped(Some("test"), List.empty)
            testFile = tempDir / "this-does-not-exist.json"
            actual  <- StorageService.loadJsonFile[SomeJsonContent](testFile).exit
          } yield assert(actual)(failsWithA[NoSuchFileException])
        }
      },
      test("should fail to load a non existing json file") {
        ZIO.scoped {
          for {
            tempDir <- Files.createTempDirectoryScoped(Some("test"), List.empty)
            testFile = tempDir / "this-does-not-exist.json"
            _       <- Files.createFile(testFile) *> Files.writeLines(testFile, List("not a json file"))
            actual  <- StorageService.loadJsonFile[SomeJsonContent](testFile).exit
          } yield assert(actual)(failsWithA[ParseException])
        }
      }
    )
  ).provide(AssetInfoServiceLive.layer, StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
