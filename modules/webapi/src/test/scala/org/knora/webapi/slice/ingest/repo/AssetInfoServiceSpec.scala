/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.repo

import eu.timepit.refined.types.string.NonEmptyString
import org.junit.runner.RunWith
import zio.Scope
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.domain.Dimensions
import org.knora.webapi.slice.ingest.domain.DurationSecs
import org.knora.webapi.slice.ingest.domain.Fps
import org.knora.webapi.slice.ingest.domain.MimeType
import org.knora.webapi.slice.ingest.domain.MimeTypeGuesser
import org.knora.webapi.slice.ingest.domain.MovingImageMetadata
import org.knora.webapi.slice.ingest.domain.OtherMetadata
import org.knora.webapi.slice.ingest.domain.SizeInBytes
import org.knora.webapi.slice.ingest.domain.StillImageMetadata
import org.knora.webapi.slice.ingest.repo.AssetInfoFileTestHelper.*
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations

@RunWith(classOf[DspZTestJUnitRunner])
class AssetInfoServiceSpec extends ZIOSpecDefault {
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
          // sizes are absent in info files written before they were recorded
          actual.original.size.isEmpty,
          actual.derivative.size.isEmpty,
        )
      },
      test("parsing an info file with sizes works") {
        // given
        for {
          assetDir <- createInfoFile(
                        originalFileExt = "pdf",
                        derivativeFileExt = "pdf",
                        customJsonProps = Some("""
                                                 |"sizeOriginal": 204800,
                                                 |"sizeDerivative": 153211
                                                 |""".stripMargin),
                      )
          // when
          actual <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
          // then
        } yield assertTrue(
          actual.original.size.contains(SizeInBytes(204800L)),
          actual.derivative.size.contains(SizeInBytes(153211L)),
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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AssetInfoServiceSpec")(findByAssetRefSuite).provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      SpecConfigurations.ingestExecutorLayer,
      MimeTypeGuesser.layer,
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
    )
}
