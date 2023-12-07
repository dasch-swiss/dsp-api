/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.SipiClientMock
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import zio.ZIO
import zio.nio.file.Files
import zio.test.{Spec, ZIOSpecDefault, assertTrue}

object IngestServiceSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Any] = suite("IngestService")(test("should ingest a simple csv file") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    ZIO.scoped {
      for {
        // given
        tempDir     <- StorageService.createTempDirectoryScoped("test", None)
        fileToIngest = tempDir / "test.csv"
        _           <- Files.createFile(fileToIngest) *> Files.writeLines(fileToIngest, List("one,two", "1,2"))
        checksum    <- FileChecksumService.createSha256Hash(fileToIngest)
        // when
        asset <- IngestService.ingestFile(fileToIngest, shortcode)
        // then
        info              <- AssetInfoService.findByAssetRef(asset.ref)
        assetDir          <- StorageService.getAssetDirectory(asset.ref)
        originalFilename   = s"${asset.id}.csv.orig"
        derivativeFilename = s"${asset.id}.csv"
        originalExists    <- Files.exists(assetDir / originalFilename)
        derivativeExists  <- Files.exists(assetDir / derivativeFilename)
      } yield assertTrue(
        asset.belongsToProject == shortcode,
        info.originalFilename.toString == fileToIngest.filename.toString,
        info.originalFilename == asset.original.originalFilename,
        info.asset == asset.ref,
        info.original.checksum == checksum,
        info.original.file.filename.toString == originalFilename,
        info.original.file.filename.toString == asset.original.internalFilename.toString,
        info.derivative.checksum == checksum,
        info.derivative.file.filename.toString == derivativeFilename,
        info.derivative.file.filename.toString == asset.derivative.filename,
        originalExists,
        derivativeExists
      )
    }
  }).provide(
    IngestService.layer,
    StorageServiceLive.layer,
    ImageServiceLive.layer,
    SipiClientMock.layer,
    AssetInfoServiceLive.layer,
    SpecConfigurations.storageConfigLayer
  )
}
