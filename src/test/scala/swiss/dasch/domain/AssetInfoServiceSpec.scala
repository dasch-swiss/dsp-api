/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO}

object AssetInfoServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoService")(
      test("parsing a simple file info works") {
        // given
        val shortcode        = ProjectShortcode.unsafeFrom("0001")
        val checksumOriginal = Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c")
        val checksumDerivative =
          Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")
        ZIO.scoped {
          for {
            assetRef      <- AssetRef.makeNew(shortcode)
            assetDir      <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
            simpleInfoFile = assetDir / s"${assetRef.id}.info"
            _             <- Files.createFile(simpleInfoFile)
            _ <- Files.writeLines(
                   simpleInfoFile,
                   List(s"""{
                           |    "internalFilename" : "${assetRef.id}.jp2",
                           |    "originalInternalFilename" : "${assetRef.id}.jp2.orig",
                           |    "originalFilename" : "250x250.jp2",
                           |    "checksumOriginal" : "$checksumOriginal",
                           |    "checksumDerivative" : "$checksumDerivative"
                           |}
                           |""".stripMargin)
                 )
            // when
            actual <- AssetInfoService.findByAssetRef(assetRef)
            // then
          } yield assertTrue(
            actual.assetRef == assetRef,
            actual.originalFilename == NonEmptyString.unsafeFrom("250x250.jp2"),
            actual.original.file == assetDir / s"${assetRef.id}.jp2.orig",
            actual.original.checksum == checksumOriginal,
            actual.derivative.file == assetDir / s"${assetRef.id}.jp2",
            actual.derivative.checksum == checksumDerivative,
            actual.movingImageMetadata.isEmpty
          )
        }
      },
      test("parsing an info file with moving image metadata info works") {
        // given
        val shortcode        = ProjectShortcode.unsafeFrom("0001")
        val checksumOriginal = Sha256Hash.unsafeFrom("fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c")
        val checksumDerivative =
          Sha256Hash.unsafeFrom("0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc")
        ZIO.scoped {
          for {
            assetRef      <- AssetRef.makeNew(shortcode)
            assetDir      <- StorageService.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
            simpleInfoFile = assetDir / s"${assetRef.id}.info"
            _             <- Files.createFile(simpleInfoFile)
            _ <- Files.writeLines(
                   simpleInfoFile,
                   List(s"""{
                           |    "internalFilename" : "${assetRef.id}.jp2",
                           |    "originalInternalFilename" : "${assetRef.id}.jp2.orig",
                           |    "originalFilename" : "250x250.jp2",
                           |    "checksumOriginal" : "$checksumOriginal",
                           |    "checksumDerivative" : "$checksumDerivative",
                           |    "width": 640,
                           |    "height": 480,
                           |    "fps": 60,
                           |    "duration": 3.14
                           |}
                           |""".stripMargin)
                 )
            actual <- AssetInfoService.findByAssetRef(assetRef)
          } yield assertTrue(
            actual.assetRef == assetRef,
            actual.originalFilename == NonEmptyString.unsafeFrom("250x250.jp2"),
            actual.original.file == assetDir / s"${assetRef.id}.jp2.orig",
            actual.original.checksum == checksumOriginal,
            actual.derivative.file == assetDir / s"${assetRef.id}.jp2",
            actual.derivative.checksum == checksumDerivative,
            actual.movingImageMetadata.contains(
              MovingImageMetadata(width = 640, height = 480, duration = 3.14, fps = 60)
            )
          )
        }
      }
    ).provide(AssetInfoServiceLive.layer, StorageServiceLive.layer, SpecConfigurations.storageConfigLayer)
}
