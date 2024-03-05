/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.AssetInfoFileTestHelper.*
import swiss.dasch.test.SpecConfigurations
import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object AssetInfoServiceSpec extends ZIOSpecDefault {
  private val findByAssetRefSuite = {
    suite("findByAssetRef")(
      test("parsing a simple file info works") {
        // given
        for {
          assetDir <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
          // when
          actual <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetDir.assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetDir.assetId}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetDir.assetId}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata == OtherMetadata(None, None),
        )
      },
      test("parsing an info file for a moving image with complete metadata info works") {
        // given
        for {
          assetDir <- createInfoFile(
                        originalFileExt = "mp4",
                        derivativeFileExt = "mp4",
                        customJsonProps = Some("""
                                                 |"width": 640,
                                                 |"height": 480,
                                                 |"fps": 60,
                                                 |"duration": 3.14,
                                                 |"internalMimeType": "video/mp4",
                                                 |"originalMimeType": "video/mp4"
                                                 |""".stripMargin),
                      )
          // when
          actual <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetDir.assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.mp4"),
          actual.original.file == assetDir / s"${assetDir.assetId}.mp4.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetDir.assetId}.mp4",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            MovingImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              duration = DurationSecs.unsafeFrom(3.14),
              fps = Fps.unsafeFrom(60),
              internalMimeType = Some(MimeType.unsafeFrom("video/mp4")),
              originalMimeType = Some(MimeType.unsafeFrom("video/mp4")),
            ),
        )
      },
      test("parsing an info file for a still image with complete metadata info works") {
        // given
        for {
          assetDir <- createInfoFile(
                        originalFileExt = "png",
                        derivativeFileExt = "jpx",
                        customJsonProps = Some("""
                                                 |"width": 640,
                                                 |"height": 480,
                                                 |"internalMimeType": "image/jpx",
                                                 |"originalMimeType": "image/png"
                                                 |""".stripMargin),
                      )
          // when
          actual <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetDir.assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.png"),
          actual.original.file == assetDir / s"${assetDir.assetId}.png.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetDir.assetId}.jpx",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            StillImageMetadata(
              Dimensions.unsafeFrom(640, 480),
              internalMimeType = Some(MimeType.unsafeFrom("image/jpx")),
              originalMimeType = Some(MimeType.unsafeFrom("image/png")),
            ),
        )
      },
      test("parsing an info file for a other file type with complete metadata info works") {
        // given
        for {
          assetDir <- createInfoFile(
                        originalFileExt = "pdf",
                        derivativeFileExt = "pdf",
                        customJsonProps = Some("""
                                                 |"internalMimeType": "application/pdf",
                                                 |"originalMimeType": "application/pdf"
                                                 |""".stripMargin),
                      )
          // when
          actual <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.assetRef == assetDir.assetRef,
          actual.originalFilename == NonEmptyString.unsafeFrom("test.pdf"),
          actual.original.file == assetDir / s"${assetDir.assetId}.pdf.orig",
          actual.original.checksum == testChecksumOriginal,
          actual.derivative.file == assetDir / s"${assetDir.assetId}.pdf",
          actual.derivative.checksum == testChecksumDerivative,
          actual.metadata ==
            OtherMetadata(
              internalMimeType = Some(MimeType.unsafeFrom("application/pdf")),
              originalMimeType = Some(MimeType.unsafeFrom("application/pdf")),
            ),
        )
      },
    )
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoServiceSpec")(findByAssetRefSuite).provide(
      AssetInfoServiceLive.layer,
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
    )
}
