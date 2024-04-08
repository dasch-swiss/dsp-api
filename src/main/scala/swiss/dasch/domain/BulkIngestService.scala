/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.IngestConfig
import zio.*
import zio.nio.file.{Files, Path}
import zio.stm.{TMap, TSemaphore}

import java.io.IOException
import java.nio.file.StandardOpenOption

case class IngestResult(success: Int = 0, failed: Int = 0) {
  def +(other: IngestResult): IngestResult = IngestResult(success + other.success, failed + other.failed)
}

object IngestResult {
  val success: IngestResult = IngestResult(success = 1)
  val failed: IngestResult  = IngestResult(failed = 1)
}

final case class BulkIngestService(
  storage: StorageService,
  ingestService: IngestService,
  config: IngestConfig,
  semaphoresPerProject: TMap[ProjectShortcode, TSemaphore],
) {
  private def getSemaphore(key: ProjectShortcode): UIO[TSemaphore] =
    (for {
      semaphore <- semaphoresPerProject.getOrElseSTM(key, TSemaphore.make(1))
      _         <- semaphoresPerProject.put(key, semaphore)
    } yield semaphore).commit

  private def acquireWithTimeout(sem: TSemaphore): UIO[Unit] =
    sem.acquire.commit.timeout(Duration.fromMillis(400)).as(())

  private def withSemaphore[E, A](key: ProjectShortcode)(
    zio: IO[E, A],
  ): IO[Option[E], A] =
    getSemaphore(key)
      .tap(acquireWithTimeout(_).asSomeError)
      .flatMap(sem => zio.logError.ensuring(sem.release.commit).asSomeError)

  def startBulkIngest(shortcode: ProjectShortcode): IO[Option[Nothing], Fiber.Runtime[IOException, IngestResult]] =
    withSemaphore(shortcode) {
      doBulkIngest(shortcode).forkDaemon
    }

  private def doBulkIngest(project: ProjectShortcode) =
    for {
      _           <- ZIO.logInfo(s"Starting bulk ingest for project $project.")
      importDir   <- getImportFolder(project)
      _           <- ZIO.fail(new IOException(s"Import directory '$importDir' does not exist")).unlessZIO(Files.exists(importDir))
      mappingFile <- createMappingFile(project, importDir)
      _           <- ZIO.logInfo(s"Import dir: $importDir, mapping file: $mappingFile")
      total       <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      _           <- ZIO.logInfo(s"Found $total ingest candidates.")
      sum <-
        StorageService
          .findInPath(importDir, FileFilters.isSupported)
          .mapZIOPar(config.bulkMaxParallel)(file => ingestFileAndUpdateMapping(project, importDir, mappingFile, file))
          .runFold(IngestResult())(_ + _)
      _ <- {
        val countAssets  = sum.success + sum.failed
        val countSuccess = sum.success
        val countFailed  = sum.failed
        val countSkipped = total - countAssets
        ZIO.logInfo(
          s"Finished bulk ingest for project $project. " +
            s"Found $countAssets assets from $total files. " +
            s"Ingested $countSuccess assets successfully, failed $countFailed and skipped $countSkipped files (See logs above for more details).",
        )
      }
    } yield sum

  private def getImportFolder(shortcode: ProjectShortcode): UIO[Path] =
    storage.getTempFolder().map(_ / "import" / shortcode.toString)

  private def createMappingFile(project: ProjectShortcode, importDir: Path): IO[IOException, Path] = {
    val mappingFile = getMappingCsvFile(importDir, project)
    ZIO
      .unlessZIO(Files.exists(mappingFile))(
        Files.createFile(mappingFile) *> Files.writeLines(mappingFile, List("original,derivative")),
      )
      .as(mappingFile)
  }

  private def getMappingCsvFile(importDir: _root_.zio.nio.file.Path, project: ProjectShortcode) =
    importDir.parent.head / s"mapping-$project.csv"

  private def ingestFileAndUpdateMapping(project: ProjectShortcode, importDir: Path, mappingFile: Path, file: Path) =
    ingestService
      .ingestFile(file, project)
      .flatMap(asset => updateMappingCsv(asset, file, importDir, mappingFile))
      .as(IngestResult.success)
      .catchNonFatalOrDie(e =>
        ZIO.logErrorCause(s"Error ingesting file $file: ${e.getMessage}", Cause.fail(e)).as(IngestResult.failed),
      )

  private def updateMappingCsv(asset: Asset, fileToIngest: Path, importDir: Path, csv: Path) =
    ZIO.logInfo(s"Updating mapping file $csv, $asset") *> {
      val ingestedFileRelativePath = CsvUtil.escapeCsvValue(s"${importDir.relativize(fileToIngest)}")
      val derivativeFilename       = CsvUtil.escapeCsvValue(asset.derivative.filename.toString)
      val line                     = s"$ingestedFileRelativePath,$derivativeFilename"
      Files.writeLines(csv, Seq(line), openOptions = Set(StandardOpenOption.APPEND))
    }

  def finalizeBulkIngest(shortcode: ProjectShortcode): IO[Option[Nothing], Fiber.Runtime[IOException, Unit]] =
    withSemaphore(shortcode) {
      doFinalize(shortcode).forkDaemon
    }

  private def doFinalize(shortcode: ProjectShortcode): ZIO[Any, IOException, Unit] =
    for {
      _         <- ZIO.logInfo(s"Finalizing bulk ingest for project $shortcode")
      importDir <- getImportFolder(shortcode)
      mappingCsv = getMappingCsvFile(importDir, shortcode)
      _         <- storage.deleteRecursive(importDir)
      _         <- storage.delete(mappingCsv)
      _         <- ZIO.logInfo(s"Finished finalizing bulk ingest for project $shortcode")
    } yield ()

  def getBulkIngestMappingCsv(shortcode: ProjectShortcode): IO[Option[IOException], Option[String]] =
    withSemaphore(shortcode) {
      for {
        importDir <- getImportFolder(shortcode)
        mappingCsv = getMappingCsvFile(importDir, shortcode)
        mapping <- ZIO.whenZIO(Files.exists(mappingCsv)) {
                     Files.readAllLines(mappingCsv).map(_.mkString("\n"))
                   }
      } yield mapping
    }
}

object BulkIngestService {
  val layer = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[BulkIngestService]
}
