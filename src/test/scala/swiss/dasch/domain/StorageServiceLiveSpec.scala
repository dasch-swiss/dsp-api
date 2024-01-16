/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

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
    test("should return the path of the folder where the asset is stored") {
      for {
        assetPath <- ZIO.serviceWith[StorageConfig](_.assetPath)
        ref        = AssetRef("FGiLaT4zzuV-CqwbEDFAFeS".toAssetId, "0001".toProjectShortcode)
        actual    <- StorageService.getAssetFolder(ref)
      } yield assertTrue(
        actual.path == assetPath / "0001" / "fg" / "il",
        actual.shortcode == ref.belongsToProject,
        actual.assetId == ref.id
      )
    },
    test("should return asset path") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.assetPath)
        actual   <- StorageService.getAssetsBaseFolder()
      } yield assertTrue(actual.path == expected)
    },
    test("should return temp path") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.tempPath)
        actual   <- StorageService.getTempFolder()
      } yield assertTrue(actual.path == expected)
    },
    test("should return project directory") {
      for {
        expected <- ZIO.serviceWith[StorageConfig](_.assetPath / existingProject.value)
        actual   <- StorageService.getProjectFolder(existingProject)
      } yield assertTrue(actual.path == expected, actual.shortcode == existingProject)
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
      test("should fail to load a file which does not contain json") {
        ZIO.scoped {
          for {
            tempDir <- Files.createTempDirectoryScoped(Some("test"), List.empty)
            testFile = tempDir / "this-does-not-contain.json"
            _       <- Files.createFile(testFile) *> Files.writeLines(testFile, List("not a json file"))
            actual  <- StorageService.loadJsonFile[SomeJsonContent](testFile).exit
          } yield assert(actual)(failsWithA[ParseException])
        }
      }
    )
  ).provide(StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
