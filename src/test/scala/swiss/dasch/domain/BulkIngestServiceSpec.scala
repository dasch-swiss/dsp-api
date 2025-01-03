/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.Asset.StillImageAsset
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.{JpxDerivativeFile, OrigFile}
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.util.TestUtils
import zio.*
import zio.nio.file.{Files, Path}
import zio.stream.ZStream
import zio.test.{TestAspect, TestClock, ZIOSpecDefault, assertCompletes, assertTrue}

import java.io.IOException

object BulkIngestServiceSpec extends ZIOSpecDefault {
  // accessor functions for testing
  private val storageService    = ZIO.serviceWithZIO[StorageService]
  private val bulkIngestService = ZIO.serviceWithZIO[BulkIngestService]

  private def getBulkIngestMappingCsv(shortcode: ProjectShortcode) =
    bulkIngestService(_.getBulkIngestMappingCsv(shortcode))

  private val shortcode = ProjectShortcode.unsafeFrom("0001")

  object TestData {
    val stillImageAsset: StillImageAsset = StillImageAsset(
      AssetRef(AssetId.unsafeFrom("aaaa"), ProjectShortcode.unsafeFrom("0001")),
      Original(OrigFile.unsafeFrom("original.jpg.orig"), NonEmptyString.unsafeFrom("original.jpg")),
      JpxDerivativeFile.unsafeFrom("original.jpx"),
      StillImageMetadata(Dimensions.unsafeFrom(6, 6), None, None),
    )
  }

  private val startBulkIngestSuite = suite("start ingest")(
    test("lock project while ingesting") {
      for {
        // given
        importDir <- storageService(_.getImportFolder(shortcode).tap(Files.createDirectories(_)))
        _         <- Files.createFile(importDir / "0001.tif")

        // when
        ingestFiber <- bulkIngestService(_.startBulkIngest(shortcode))
        failed      <- bulkIngestService(_.startBulkIngest(shortcode)).fork
        _           <- TestClock.adjust(700.second)

        // then
        failed       <- failed.join.exit
        ingestResult <- ingestFiber.join
        project      <- ZIO.serviceWithZIO[ProjectRepository](_.findByShortcode(shortcode))
      } yield assertTrue(ingestResult == IngestResult.success, failed.isFailure, project.nonEmpty)
    },
    test("fail when import folder does not exist") {
      for {
        _    <- storageService(_.getImportFolder(shortcode).tap(Files.deleteIfExists(_)))
        exit <- bulkIngestService(_.startBulkIngest(shortcode)).exit
      } yield assertTrue(exit == Exit.fail(BulkIngestError.ImportFolderDoesNotExist))
    },
  )

  private val finalizeBulkIngestSuite = suite("finalize bulk ingest should")(
    test("remove all files") {
      for {
        // given
        importDir     <- storageService(_.getImportFolder(shortcode)).tap(Files.createDirectories(_))
        _             <- Files.createFile(importDir / "0001.tif")
        mappingCsvFile = importDir.parent.head / s"mapping-$shortcode.csv"
        _             <- Files.createFile(mappingCsvFile)
        // when
        fork <- bulkIngestService(_.finalizeBulkIngest(shortcode))
        // then
        _                  <- fork.join
        importDirDeleted   <- Files.exists(importDir).negate
        mappingFileDeleted <- Files.exists(mappingCsvFile).negate
      } yield assertTrue(importDirDeleted && mappingFileDeleted)
    },
    test("fail when import folder does not exist") {
      for {
        _    <- storageService(_.getImportFolder(shortcode).tap(Files.deleteIfExists(_)))
        exit <- bulkIngestService(_.finalizeBulkIngest(shortcode)).exit
      } yield assertTrue(exit == Exit.fail(BulkIngestError.ImportFolderDoesNotExist))
    },
  )

  private val getBulkIngestMappingCsvSuite = suite("getBulkIngestMappingCsv")(test("return the mapping csv file") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir     <- storageService(_.getImportFolder(shortcode)).tap(Files.createDirectories(_))
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
      importDir <- storageService(_.getImportFolder(shortcode)).tap(Files.createDirectories(_))
      _         <- Files.createFile(importDir.parent.head / s"mapping-$shortcode.csv")

      _ <- getBulkIngestMappingCsv(shortcode)
      _ <- bulkIngestService(_.finalizeBulkIngest(shortcode))
      _ <- getBulkIngestMappingCsv(shortcode)
      _ <- storageService(_.getImportFolder(shortcode)).tap(Files.createDirectories(_))
      _ <- bulkIngestService(_.finalizeBulkIngest(shortcode))
    } yield assertCompletes
  })

  private val postBulkIngestEndpointSuite = suite("postBulkIngestEndpoint")(test("test bulk-ingest individual upload") {
    val shortcode = ProjectShortcode.unsafeFrom("0001")
    for {
      // given
      importDir <- storageService(_.getImportFolder(shortcode))
      filepath   = "one/two/out.txt"
      // when
      _ <- ZIO.serviceWithZIO[BulkIngestService](_.uploadSingleFile(shortcode, filepath, ZStream(0)))
      // then
      file <- Files.readAllBytes(importDir / "one" / "two" / "out.txt")
    } yield assertTrue(file == Chunk(0))
  })

  val MockIngestServiceLayer: ULayer[IngestService] = ZLayer.succeed {
    new IngestService {
      override def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset] =
        ZIO.sleep(Duration.fromMillis(600)).as(TestData.stillImageAsset)
    }
  }

  private val deleteProjectFolder =
    storageService(_.getProjectFolder(shortcode)).tap(p => Files.deleteRecursive(p).whenZIO(Files.exists(p)))

  val spec = (suite("BulkIngestServiceLive")(
    startBulkIngestSuite,
    finalizeBulkIngestSuite,
    getBulkIngestMappingCsvSuite,
    checkSemaphoresReleased,
    postBulkIngestEndpointSuite,
  ) @@ TestAspect.before(deleteProjectFolder)).provide(
    AssetInfoServiceLive.layer,
    BulkIngestService.layer,
    FileChecksumServiceLive.layer,
    MockIngestServiceLayer,
    ProjectRepositoryLive.layer,
    ProjectService.layer,
    SpecConfigurations.ingestConfigLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
    TestUtils.testDbLayerWithEmptyDb,
  ) @@ TestAspect.timeout(4.seconds)
}
