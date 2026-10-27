/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.domain.MimeTypeGuesser
import org.knora.webapi.slice.ingest.infrastructure.IngestExecutor
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations
import org.knora.webapi.slice.ingest.testutil.SpecConstants.AssetRefs.*
import org.knora.webapi.slice.ingest.testutil.SpecPaths.pathFromResource

@RunWith(classOf[DspZTestJUnitRunner])
class FileChecksumServiceLiveSpec extends ZIOSpecDefault {

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
    test("should verify the checksums of an asset's derivative and original") {
      for {
        assetInfo      <- AssetInfoService.findByAssetRef(existingAssetRef).map(_.head)
        checksumResult <- FileChecksumService.verifyChecksum(assetInfo)
      } yield assertTrue(
        checksumResult.map(_.file) == Chunk(assetInfo.original.file, assetInfo.derivative.file),
        checksumResult.forall(_.checksumMatches),
      )
    },
    test("createSha256Hash waits for an IngestExecutor permit") {
      ZIO.scoped {
        for {
          executor <- IngestExecutor.make(1)
          release  <- Promise.make[Nothing, Unit]
          held     <- Promise.make[Nothing, Unit]
          _        <- executor.withPermit(held.succeed(()) *> release.await).fork
          _        <- held.await
          hashing  <- FileChecksumServiceLive(executor).createSha256Hash(pathFromResource(fileprefix + ".jp2.orig")).fork
          _        <- ZIO.sleep(200.millis)
          early    <- hashing.poll
          _        <- release.succeed(())
          checksum <- hashing.join
        } yield assertTrue(early.isEmpty, checksum.toString == checksumOrig)
      }
    } @@ TestAspect.withLiveClock,
  ).provide(
    AssetInfoServiceLive.layer,
    SpecConfigurations.ingestExecutorLayer,
    MimeTypeGuesser.layer,
    FileChecksumServiceLive.layer,
    StorageServiceLive.layer,
    SpecConfigurations.storageConfigLayer,
  )
}
