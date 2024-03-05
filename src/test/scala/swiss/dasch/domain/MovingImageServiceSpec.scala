/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.OrigFile
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
    assetRef <- AssetRef.makeNew(shortcode)
    assetDir <- StorageService.getAssetFolder(assetRef).tap(Files.createDirectories(_))
    orig      = OrigFile.unsafeFrom(assetDir / s"${assetRef.id}.$fileExtension.orig")
    _        <- Files.createFile(orig)
    original  = Original(orig, NonEmptyString.unsafeFrom(s"test.$fileExtension"))
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
                                    .getAssetFolder(c.assetRef)
                                    .map(_ / s"${c.assetRef.id}.mp4")
        origChecksum  <- FileChecksumService.createSha256Hash(c.original.file)
        derivChecksum <- FileChecksumService.createSha256Hash(derivative)
      } yield assertTrue(
        derivative.path == expectedDerivativePath,
        origChecksum == derivChecksum, // moving image derivative is just a copy
      )
    },
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
                 0,
               ),
             )
        // when
        metadata <- MovingImageService.extractMetadata(c.original, d)
        // then
      } yield assertTrue(
        metadata == MovingImageMetadata(
          Dimensions.unsafeFrom(1280, 720),
          duration = DurationSecs.unsafeFrom(170.84),
          fps = Fps.unsafeFrom(25.0),
          Some(MimeType.unsafeFrom("video/mp4")),
          Some(MimeType.unsafeFrom("video/mp4")),
        ),
      )
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
          0,
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
          0,
        ),
      )
      check(Gen.fromIterable(invalidProcessOut)) { processOutput =>
        for {
          // given
          c <- createOriginalFile("mp4")
          d <- MovingImageService.createDerivative(c.original, c.assetRef)
          _ <- CommandExecutorMock.setOutput(processOutput)
          // when
          exit <- MovingImageService.extractMetadata(c.original, d).exit
          // then
        } yield assertTrue(exit.isFailure)
      }
    },
  )

  val spec = suite("MovingImageService")(createDerivativeSuite, extractMetaDataSuite)
    .provide(
      CommandExecutorMock.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
    )
}
