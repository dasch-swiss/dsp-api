/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.infrastructure.{CommandExecutor, CommandExecutorMock, ProcessOutput}
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.*
import zio.test.Assertion.*
import zio.{Exit, ZIO, ZLayer}

import java.io.IOException

object MovingImageServiceSpec extends ZIOSpecDefault {

  private val shortcode = ProjectShortcode.unsafeFrom("0001")
  private final case class OrigRef(original: Original, assetRef: AssetRef)
  private def createOriginalFile(fileExtension: String): ZIO[StorageService, Throwable, OrigRef] = for {
    assetRef    <- AssetRef.makeNew(shortcode)
    assetDir    <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
    originalPath = assetDir / s"${assetRef.id}.$fileExtension.orig"
    _           <- Files.createFile(originalPath)
    original     = Original(OriginalFile.unsafeFrom(originalPath), NonEmptyString.unsafeFrom(s"test.$fileExtension"))
  } yield OrigRef(original, assetRef)

  private val createDerivativeSuite = suite("createDerivative")(
    test("should die for unsupported files") {
      for {
        // given
        c <- createOriginalFile("txt")
        // when
        exit <- MovingImageService.createDerivative(c.original, c.assetRef).exit
        // then
      } yield assert(exit)(diesWithA[IllegalArgumentException])
    },
    test("should create a derivative for supported files") {
      for {
        // given
        c <- createOriginalFile("mp4")
        // when
        derivative <- MovingImageService.createDerivative(c.original, c.assetRef)
        // then
        expectedDerivativePath <- StorageService
                                    .getAssetDirectory(c.assetRef)
                                    .map(_ / s"${c.assetRef.id}.mp4")
        origChecksum  <- FileChecksumService.createSha256Hash(c.original.file.toPath)
        derivChecksum <- FileChecksumService.createSha256Hash(derivative.toPath)
      } yield assertTrue(
        derivative.toPath == expectedDerivativePath,
        origChecksum == derivChecksum // moving image derivative is just a copy
      )
    }
  )

  private val extractMetaDataSuite = suite("extractMetaData")(
    test("given correct metadata it should extract") {
      for {
        // given
        c <- createOriginalFile("mp4")
        d <- MovingImageService.createDerivative(c.original, c.assetRef)
        _ <- CommandExecutorMock.setOutput(
               ProcessOutput(
                 stdout = s"""
                             |{
                             |    "programs": [
                             |    ],
                             |    "streams": [
                             |        {
                             |            "width": 1280,
                             |            "height": 720,
                             |            "r_frame_rate": "25/1",
                             |            "duration": "170.840000"
                             |        }
                             |    ]
                             |}
                             |""".stripMargin,
                 "",
                 0
               )
             )
        // when
        metadata <- MovingImageService.extractMetadata(d, c.assetRef)
        // then
      } yield assertTrue(metadata == MovingImageMetadata(width = 1280, height = 720, duration = 170.84, fps = 25.0))
    },
    test("given invalid metadata it should not extract") {
      val invalidProcessOut = Seq(
        ProcessOutput("", "there was an error", 1),
        ProcessOutput("", "", 0),
        ProcessOutput(
          stdout = s"""
                      |{
                      |    "programs": [
                      |    ],
                      |    "streams": [
                      |    ]
                      |}
                      |""".stripMargin,
          "",
          0
        ),
        ProcessOutput(
          stdout = s"""
                      |{
                      |    "programs": [
                      |    ],
                      |    "streams": [
                      |            "width": 1280,
                      |            "height": 720,
                      |            "r_frame_rate": 0,
                      |            "duration": "170.840000"
                      |    ]
                      |}
                      |""".stripMargin,
          "",
          0
        )
      )
      check(Gen.fromIterable(invalidProcessOut)) { processOutput =>
        for {
          // given
          c <- createOriginalFile("mp4")
          d <- MovingImageService.createDerivative(c.original, c.assetRef)
          _ <- CommandExecutorMock.setOutput(processOutput)
          // when
          exit <- MovingImageService.extractMetadata(d, c.assetRef).exit
          // then
        } yield assertTrue(exit.isFailure)
      }
    }
  )

  val spec = suite("MovingImageService")(createDerivativeSuite, extractMetaDataSuite)
    .provide(
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      MovingImageService.layer,
      CommandExecutorMock.layer
    )
}
