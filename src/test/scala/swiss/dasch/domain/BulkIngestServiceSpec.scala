/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.SipiClientMock
import swiss.dasch.infrastructure.CommandExecutorMock
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.Files
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.*

import java.io.IOException
import zio.test.TestAspect

object BulkIngestServiceSpec extends ZIOSpecDefault {
  // accessor functions for testing
  private def finalizeBulkIngest(
    shortcode: ProjectShortcode,
  ): ZIO[BulkIngestService, Option[Nothing], Fiber.Runtime[IOException, Unit]] =
    ZIO.serviceWithZIO[BulkIngestService](_.finalizeBulkIngest(shortcode))

  private def getBulkIngestMappingCsv(
    shortcode: ProjectShortcode,
  ): ZIO[BulkIngestService, Option[IOException], Option[String]] =
    ZIO.serviceWithZIO[BulkIngestService](_.getBulkIngestMappingCsv(shortcode))

  private val finalizeBulkIngestSuite = suite("finalize bulk ingest should")(test("remove all files") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService
                     .getTempFolder()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      _             <- Files.createFile(importDir / "0001.tif")
      mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
      _             <- Files.createFile(mappingCsvFile)
      // when
      fork <- finalizeBulkIngest(shortcode)
      // then
      _                  <- fork.join
      importDirDeleted   <- Files.exists(importDir).negate
      mappingFileDeleted <- Files.exists(mappingCsvFile).negate
    } yield assertTrue(importDirDeleted && mappingFileDeleted)
  })

  private val getBulkIngestMappingCsvSuite = suite("getBulkIngestMappingCsv")(test("return the mapping csv file") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService
                     .getTempFolder()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
      _             <- Files.createFile(mappingCsvFile)
      _             <- Files.writeLines(mappingCsvFile, List("1,2,3"))
      // when
      mappingCsv <- getBulkIngestMappingCsv(shortcode)
      // then
      mappingCsvFileExists <- Files.exists(mappingCsvFile)
    } yield assertTrue(mappingCsvFileExists && mappingCsv.contains("1,2,3"))
  })

  private val checkSemaphoresReleased = suite("check semaphores released")(test("check semaphores") {
    for {
      shortcode <- ZIO.succeed(ProjectShortcode.unsafeFrom("0001"))
      importDir <- StorageService.getTempFolder().map(_ / "import" / shortcode.value).tap(Files.createDirectories(_))
      _         <- Files.createFile(importDir.parent.head / s"mapping-$shortcode.csv")

      _ <- getBulkIngestMappingCsv(shortcode)
      _ <- finalizeBulkIngest(shortcode)
      _ <- getBulkIngestMappingCsv(shortcode)
      _ <- finalizeBulkIngest(shortcode)

    } yield assertTrue(true)
  })

  val spec = suite("BulkIngestServiceLive")(
    finalizeBulkIngestSuite,
    getBulkIngestMappingCsvSuite,
    checkSemaphoresReleased,
  ).provide(
    AssetInfoServiceLive.layer,
    BulkIngestService.layer,
    CommandExecutorMock.layer,
    IngestService.layer,
    MimeTypeGuesser.layer,
    MovingImageService.layer,
    OtherFilesService.layer,
    SipiClientMock.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StillImageService.layer,
    StorageServiceLive.layer,
  ) @@ TestAspect.timeout(1.second)
}
