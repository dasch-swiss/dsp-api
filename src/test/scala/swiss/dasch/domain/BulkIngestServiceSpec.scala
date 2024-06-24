/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.Asset.StillImageAsset
import swiss.dasch.domain.AugmentedPath.JpxDerivativeFile
import swiss.dasch.domain.AugmentedPath.OrigFile
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream
import zio.test.TestAspect
import zio.test.TestClock
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.IOException

object BulkIngestServiceSpec extends ZIOSpecDefault {
  // accessor functions for testing
  private def finalizeBulkIngest(
    shortcode: ProjectShortcode,
  ): ZIO[BulkIngestService, Unit, Fiber.Runtime[IOException, Unit]] =
    ZIO.serviceWithZIO[BulkIngestService](_.finalizeBulkIngest(shortcode))

  private def getBulkIngestMappingCsv(
    shortcode: ProjectShortcode,
  ): ZIO[BulkIngestService, Option[IOException], Option[String]] =
    ZIO.serviceWithZIO[BulkIngestService](_.getBulkIngestMappingCsv(shortcode))

  private def bulkIngestService = ZIO.serviceWithZIO[BulkIngestService]

  private val shortcode = ProjectShortcode.unsafeFrom("0001")

  object TestData {
    val path    = zio.http.Path("a/b/c.tiff")
    val assetId = AssetId.from("aaaa").toOption.get
    val stillImageAsset = StillImageAsset(
      AssetRef(assetId, ProjectShortcode.unsafeFrom("0001")),
      Original(OrigFile.from("original.jpg.orig").toOption.get, NonEmptyString.from("original.jpg").toOption.get),
      JpxDerivativeFile.from("original.jpx").toOption.get,
      StillImageMetadata(Dimensions.unsafeFrom(6, 6), None, None),
    )
  }

  private val startBulkIngestSuite = suite("start ingest")(test("lock project while ingesting") {
    for {
      // given
      importDir <- StorageService
                     .getTempFolder()
                     .map(_ / "import" / shortcode.value)
                     .tap(Files.createDirectories(_))
      _ <- Files.createFile(importDir / "0001.tif")

      // when
      ingestFiber  <- bulkIngestService(_.startBulkIngest(shortcode))
      failureFiber <- bulkIngestService(_.startBulkIngest(shortcode)).fork
      _            <- TestClock.adjust(700.second)

      // then
      _            <- failureFiber.join.flip
      ingestResult <- ingestFiber.join
    } yield assertTrue(ingestResult == IngestResult(1, 0))
  })

  private val finalizeBulkIngestSuite = suite("finalize bulk ingest should")(test("remove all files") {
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

  private val postBulkIngestEndpointSuite = suite("postBulkIngestEndpoint")(test("test bulk-ingest individual upload") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- StorageService.getTempFolder().map(_ / "import" / shortcode.value)
      filenames  = List("one", "..", "two", "out.txt")
      // when
      _ <- ZIO.serviceWithZIO[BulkIngestService](_.uploadSingleFile(shortcode, filenames, ZStream(0)))
      // then
      file <- Files.readAllBytes(importDir / "one" / "two" / "out.txt")
    } yield assertTrue(file == Chunk(0))
  })

  val MockIngestServiceLayer = ZLayer[Any, Nothing, IngestService](
    ZIO.succeed(
      new IngestService {
        override def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset] =
          ZIO.sleep(Duration.fromMillis(600)).as(TestData.stillImageAsset)
      },
    ),
  )

  val spec = suite("BulkIngestServiceLive")(
    startBulkIngestSuite,
    finalizeBulkIngestSuite,
    getBulkIngestMappingCsvSuite,
    checkSemaphoresReleased,
    postBulkIngestEndpointSuite,
  ).provide(
    BulkIngestService.layer,
    MockIngestServiceLayer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  ) @@ TestAspect.timeout(1.second)
}
