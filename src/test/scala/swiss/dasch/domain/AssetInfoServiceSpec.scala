/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.AssetInfoFileTestHelper.*
import swiss.dasch.test.SpecConfigurations
import zio.Scope
import zio.nio.file.Path
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object AssetInfoServiceSpec extends ZIOSpecDefault {
  private val findByAssetRefSuite = {
    suite("findByAssetRef")(
      test("parsing a simple file info works") {
        // given
        for {
          refAndDir           <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetRef.id}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata == OtherMetadata(None, None)
        )
      },
      test("parsing an info file for a moving image with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "mp4",
                         derivativeFileExt = "mp4",
                         customJsonProps = Some("""
                                                  |"width": 640,
                                                  |"height": 480,
                                                  |"fps": 60,
                                                  |"duration": 3.14,
                                                  |"internalMimeType": "video/mp4",
                                                  |"originalMimeType": "video/mp4"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.mp4"),
          actual.original.file == assetDir / s"${assetRef.id}.mp4.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.mp4",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            MovingImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              duration = DurationSecs.unsafeFrom(3.14),
              fps = Fps.unsafeFrom(60),
              internalMimeType = Some(MimeType.unsafeFrom("video/mp4")),
              originalMimeType = Some(MimeType.unsafeFrom("video/mp4"))
            )
        )
      },
      test("parsing an info file for a still image with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "png",
                         derivativeFileExt = "jpx",
                         customJsonProps = Some("""
                                                  |"width": 640,
                                                  |"height": 480,
                                                  |"internalMimeType": "image/jpx",
                                                  |"originalMimeType": "image/png"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.png"),
          actual.original.file == assetDir / s"${assetRef.id}.png.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.jpx",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            StillImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              internalMimeType = Some(MimeType.unsafeFrom("image/jpx")),
              originalMimeType = Some(MimeType.unsafeFrom("image/png"))
            )
        )
      },
      test("parsing an info file for a other file type with complete metadata info works") {
        // given
        for {
          refAndDir <- createInfoFile(
                         originalFileExt = "pdf",
                         derivativeFileExt = "pdf",
                         customJsonProps = Some("""
                                                  |"internalMimeType": "application/pdf",
                                                  |"originalMimeType": "application/pdf"
                                                  |""".stripMargin)
                       )
          (assetRef, assetDir) = refAndDir
          // when
          actual <- AssetInfoService.findByAssetRef(assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetRef.id}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetRef.id}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            OtherMetadata(
              internalMimeType = Some(MimeType.unsafeFrom("application/pdf")),
              originalMimeType = Some(MimeType.unsafeFrom("application/pdf"))
            )
        )
      }
    )
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoServiceSpec")(findByAssetRefSuite).provide(
      AssetInfoServiceLive.layer,
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer
    )
}
