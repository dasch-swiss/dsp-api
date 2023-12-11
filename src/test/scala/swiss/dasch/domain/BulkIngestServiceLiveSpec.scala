/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.SipiClientMock
import swiss.dasch.infrastructure.CommandExecutorMock
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.{ZIOSpecDefault, assertTrue}

object BulkIngestServiceLiveSpec extends ZIOSpecDefault {

  private val finalizeBulkIngestSuite = suite("finalize bulk ingest should")(test("remove all files") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService
                     .getTempDirectory()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      _             <- Files.createFile(importDir / "0001.tif")
      mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
      _             <- Files.createFile(mappingCsvFile)
      // when
      _ <- BulkIngestService.finalizeBulkIngest(shortcode)
      // then
      importDirDeleted   <- Files.exists(importDir).negate
      mappingFileDeleted <- Files.exists(mappingCsvFile).negate
    } yield assertTrue(importDirDeleted && mappingFileDeleted)
  })

  private val getBulkIngestMappingCsvSuite = suite("getBulkIngestMappingCsv")(test("return the mapping csv file") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService
                     .getTempDirectory()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
      _             <- Files.createFile(mappingCsvFile)
      _             <- Files.writeLines(mappingCsvFile, List("1,2,3"))
      // when
      mappingCsv <- BulkIngestService.getBulkIngestMappingCsv(shortcode)
      // then
      mappingCsvFileExists <- Files.exists(mappingCsvFile)
    } yield assertTrue(mappingCsvFileExists && mappingCsv.contains("1,2,3"))
  })

  val spec = suite("BulkIngestServiceLive")(
    finalizeBulkIngestSuite,
    getBulkIngestMappingCsvSuite
  ).provide(
    AssetInfoServiceLive.layer,
    BulkIngestServiceLive.layer,
    CommandExecutorMock.layer,
    IngestService.layer,
    MovingImageService.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StillImageServiceLive.layer,
    StorageServiceLive.layer
  )
}
