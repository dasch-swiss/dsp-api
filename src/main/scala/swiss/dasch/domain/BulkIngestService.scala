/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.ApiProblem
import swiss.dasch.api.ApiProblem.BadRequest
import swiss.dasch.api.ProjectsEndpointsResponses.UploadResponse
import swiss.dasch.config.Configuration.IngestConfig
import swiss.dasch.domain.BulkIngestError.*
import zio.*
import zio.nio.file.{Files, Path}
import zio.stm.{TMap, TSemaphore, ZSTM}
import zio.stream.{ZSink, ZStream}

import java.io.IOException
import java.nio.file.StandardOpenOption
import java.sql.SQLException

case class IngestResult(success: Int = 0, failed: Int = 0) {
  def +(other: IngestResult): IngestResult = IngestResult(success + other.success, failed + other.failed)
}

object IngestResult {
  val success: IngestResult = IngestResult(success = 1)
  val failed: IngestResult  = IngestResult(failed = 1)
}

sealed trait BulkIngestError
object BulkIngestError {
  case class ImportFolderDoesNotExist() extends BulkIngestError
  case class BulkIngestInProgress()     extends BulkIngestError
  case class IoError(msg: String)       extends BulkIngestError
}

final case class BulkIngestService(
  storage: StorageService,
  ingestService: IngestService,
  config: IngestConfig,
  projectService: ProjectService,
  semaphoresPerProject: TMap[ProjectShortcode, TSemaphore],
) {

  private def acquireSemaphore(key: ProjectShortcode): ZSTM[Any, Nothing, TSemaphore] =
    for {
      semaphore <- semaphoresPerProject.getOrElseSTM(key, TSemaphore.make(1))
      _         <- semaphoresPerProject.put(key, semaphore)
      _         <- semaphore.acquire
    } yield semaphore

  private def withSemaphoreDaemon[E, A](key: ProjectShortcode)(
    zio: IO[E, A],
  ): IO[Unit, Fiber.Runtime[E, A]] =
    acquireSemaphore(key).commit
      .timeout(Duration.fromMillis(400))
      .someOrFail(())
      .flatMap(sem => zio.logError.ensuring(sem.release.commit).forkDaemon)
      .orElseFail(())

  private def withSemaphore[E, A](key: ProjectShortcode)(zio: IO[E, A]): IO[Option[E], A] =
    withSemaphoreDaemon(key)(zio).orElseFail(None: Option[Nothing]).flatMap(f => f.join.asSomeError)

  def startBulkIngest(
    shortcode: ProjectShortcode,
  ): IO[ImportFolderDoesNotExist.type | BulkIngestInProgress.type, Fiber.Runtime[IoError, IngestResult]] = for {
    _ <- ensureImportFolderExists(shortcode)
    fiber <- withSemaphoreDaemon(shortcode)(doBulkIngest(shortcode).mapError {
               case _: IOException  => IoError("Unable to access file system")
               case _: SQLException => IoError("Unable to access database")
             }).orElseFail(BulkIngestInProgress)
  } yield fiber

  private def ensureImportFolderExists(shortcode: ProjectShortcode) =
    storage
      .getImportFolder(shortcode)
      .flatMap(Files.isDirectory(_))
      .filterOrFail(identity)(ImportFolderDoesNotExist)

  private def doBulkIngest(project: ProjectShortcode): IO[IOException | SQLException, IngestResult] =
    for {
      _           <- ZIO.logInfo(s"Starting bulk ingest for project $project.")
      importDir   <- storage.getImportFolder(project)
      _           <- ZIO.fail(new IOException(s"Import directory '$importDir' does not exist")).unlessZIO(Files.exists(importDir))
      mappingFile <- createMappingFile(project, importDir)
      _           <- ZIO.logInfo(s"Import dir: $importDir, mapping file: $mappingFile")
      total       <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      _           <- ZIO.logInfo(s"Found $total ingest candidates.")
      _           <- projectService.findOrCreateProject(project)
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

  private def createMappingFile(project: ProjectShortcode, importDir: Path): IO[IOException, Path] = {
    val mappingFile = getMappingCsvFile(importDir, project)
    ZIO
      .unlessZIO(Files.exists(mappingFile))(
        Files.createFile(mappingFile) *> Files.writeLines(mappingFile, List("original,derivative")),
      )
      .as(mappingFile)
  }

  private def getMappingCsvFile(importDir: Path, project: ProjectShortcode) =
    importDir.parent.head / s"mapping-$project.csv"

  private def ingestFileAndUpdateMapping(
    project: ProjectShortcode,
    importDir: Path,
    mappingFile: Path,
    file: Path,
  ): UIO[IngestResult] =
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

  def finalizeBulkIngest(
    shortcode: ProjectShortcode,
  ): IO[ImportFolderDoesNotExist.type | BulkIngestInProgress.type, Fiber.Runtime[IoError, Unit]] =
    ensureImportFolderExists(shortcode) *>
      withSemaphoreDaemon(shortcode) {
        doFinalize(shortcode).orElseFail(IoError("Unable to access file system"))
      }.orElseFail(BulkIngestInProgress)

  private def doFinalize(shortcode: ProjectShortcode): ZIO[Any, IOException, Unit] =
    for {
      _         <- ZIO.logInfo(s"Finalizing bulk ingest for project $shortcode")
      importDir <- storage.getImportFolder(shortcode)
      mappingCsv = getMappingCsvFile(importDir, shortcode)
      _         <- storage.deleteRecursive(importDir)
      _         <- storage.delete(mappingCsv)
      _         <- ZIO.logInfo(s"Finished finalizing bulk ingest for project $shortcode")
    } yield ()

  def getBulkIngestMappingCsv(shortcode: ProjectShortcode): IO[Option[IOException], Option[String]] =
    withSemaphore(shortcode) {
      for {
        importDir <- storage.getImportFolder(shortcode)
        mappingCsv = getMappingCsvFile(importDir, shortcode)
        mapping <- ZIO.whenZIO(Files.exists(mappingCsv)) {
                     Files.readAllLines(mappingCsv).map(_.mkString("\n"))
                   }
      } yield mapping
    }

  def uploadSingleFile(
    shortcode: ProjectShortcode,
    filename: String,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[Option[ApiProblem], UploadResponse] = withSemaphore(shortcode) {
    for {
      path <-
        ZIO
          .fromEither(PathOps.fromString(filename))
          .map(_.normalize)
          .filterOrFail(!_.toString.startsWith(".."))("Cannot traverse out of the upload directory")
          .filterOrFail(_.elements.nonEmpty)("Is empty")
          .tap(p => ZIO.fromEither(AssetFilename.fromPath(p)))
          .mapError(msg => BadRequest.invalidPathVariable("filename", filename, msg))
      file <- storage.getImportFolder(shortcode).map(_ / path)
      _    <- ZIO.foreachDiscard(file.parent)(Files.createDirectories(_)).orDie
      _    <- (stream >>> ZSink.fromFile(file.toFile)).orDie
    } yield UploadResponse()
  }
}

object BulkIngestService {
  val layer = ZLayer.fromZIO(TMap.empty[ProjectShortcode, TSemaphore].commit) >>> ZLayer.derive[BulkIngestService]
}
