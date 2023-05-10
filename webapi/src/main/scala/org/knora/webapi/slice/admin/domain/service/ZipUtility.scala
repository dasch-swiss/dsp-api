package org.knora.webapi.slice.admin.domain.service

import zio._
import zio.nio.file._

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.knora.webapi.util.ZScopedJavaIoStreams

object ZipUtility {

  /**
   * Zips a directory recursively, it will traverse and include files in subfolder, empty directories are not included.
   * The zip file is created in the specified destination directory.
   * Should the destination directory not exist, it is created.
   * Should the ZIP file already exist, it is overwritten.
   *
   * @param srcFolder The path to the directory to be zipped.
   * @param destinationFolder The path to the destination directory.
   * @param zipFilename The name of the zip file to be created. If not specified, the name of the source directory is used.
   * @return The [[Path]]to the created zip file.
   */
  def zipFolder(
    srcFolder: Path,
    destinationFolder: Path,
    zipFilename: Option[String] = None
  ): Task[Path] = ZIO.scoped {
    val zipFile = destinationFolder / zipFilename.getOrElse(s"${srcFolder.filename.toString}.zip")
    for {
      _      <- ZIO.whenZIO(Files.notExists(destinationFolder))(Files.createDirectory(destinationFolder))
      zipOut <- createTargetZipFileStream(zipFile)
      _      <- addFolder(srcFolder, srcFolder, zipOut)
    } yield zipFile
  }

  private def createTargetZipFileStream(targetFile: Path) =
    for {
      _ <-
        ZIO
          .whenZIO(Files.notExists(targetFile))(Files.createFile(targetFile))
          .orElse(Files.delete(targetFile) *> Files.createFile(targetFile))
      zipOut <- ZScopedJavaIoStreams.zipOutputStream(targetFile.toFile)
    } yield zipOut

  private def addFolder(folderToAdd: Path, srcFolder: Path, out: ZipOutputStream): ZIO[Scope, Throwable, Unit] =
    Files
      .newDirectoryStream(folderToAdd)
      .runCollect
      .flatMap(ZIO.foreach(_)(addEntryToZip(_, srcFolder, out)).unit)

  private def addEntryToZip(entry: Path, srcFolder: Path, out: ZipOutputStream) = for {
    _ <- ZIO.whenZIO(Files.isRegularFile(entry))(addFile(entry, srcFolder, out))
    _ <- ZIO.whenZIO(Files.isDirectory(entry))(addFolder(entry, srcFolder, out))
  } yield ()

  private def addFile(entry: Path, srcFolder: Path, out: ZipOutputStream) = for {
    fis <- ZScopedJavaIoStreams.fileInputStream(entry.toFile)
    _ <- ZIO.attemptBlocking {
           out.putNextEntry(getZipEntry(entry, srcFolder))
           fis.transferTo(out)
           out.closeEntry()
         }
  } yield ()

  private def getZipEntry(entry: Path, srcFolder: Path) = new ZipEntry(srcFolder.relativize(entry).toString())

}
