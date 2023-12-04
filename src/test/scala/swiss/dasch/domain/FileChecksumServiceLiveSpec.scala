/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.AssetRefs.*
import swiss.dasch.test.SpecPaths.pathFromResource
import zio.test.{ZIOSpecDefault, assertTrue}

object FileChecksumServiceLiveSpec extends ZIOSpecDefault {

  private val checksumOrig = "fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c"
  private val fileprefix   = "test-folder-structure/0001/fg/il/FGiLaT4zzuV-CqwbEDFAFeS"

  val spec = suite("FileChecksumService")(
    test("should calculate checksum of .jp2.orig should be correct") {
      for {
        checksum <-
          FileChecksumService.createSha256Hash(pathFromResource(fileprefix + ".jp2.orig"))
      } yield assertTrue(checksum.toString == checksumOrig)
    },
    test("should calculate checksum of .jp2 should not match orig") {
      for {
        checksum <-
          FileChecksumService.createSha256Hash(pathFromResource(fileprefix + ".jp2"))
      } yield assertTrue(checksum.toString != checksumOrig)
    },
    test("should verify the checksums of an asset's original") {
      for {
        checksumMatches <- FileChecksumService.verifyChecksumOrig(existingAssetRef)
      } yield assertTrue(checksumMatches)
    },
    test("should verify the checksums of an asset's derivative") {
      for {
        checksumMatches <- FileChecksumService.verifyChecksumDerivative(existingAssetRef)
      } yield assertTrue(checksumMatches)
    },
    test("should verify the checksums of an asset's derivative and original") {
      for {
        assetInfo      <- AssetInfoService.findByAssetRef(existingAssetRef)
        checksumResult <- FileChecksumService.verifyChecksum(assetInfo)
      } yield assertTrue(checksumResult.forall(_.checksumMatches == true))
    }
  ).provide(
    AssetInfoServiceLive.layer,
    FileChecksumServiceLive.layer,
    StorageServiceLive.layer,
    SpecConfigurations.storageConfigLayer
  )
}
