/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._
import zio.nio.file._

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

  /**
   * Unzips a zip file to the specified destination folder.
   *
   * @param zipFile The path to the zip file to be unzipped.
   * @param destinationFolder The path to the folder where the content of the zip file is written to.
   */
  def unzipFile(zipFile: Path, destinationFolder: Path): Task[Path] = ZIO.scoped {
    ZScopedJavaIoStreams.zipInputStream(zipFile).flatMap(unzip(_, destinationFolder)).as(destinationFolder)
  }
  private def unzip(zipInput: ZipInputStream, destinationFolder: Path): Task[Path] =
    unzipNextEntry(zipInput, destinationFolder)
      // recursively unpack the rest of the stream
      .flatMap(entry => unzip(zipInput, destinationFolder).when(entry.isDefined))
      .as(destinationFolder)
  private def unzipNextEntry(zipInput: ZipInputStream, destinationFolder: Path) = {
    val acquire  = ZIO.attemptBlocking(Option(zipInput.getNextEntry))
    val release  = ZIO.attemptBlocking(zipInput.closeEntry()).logError.ignore
    val getEntry = ZIO.acquireRelease(acquire)(_ => release)
    val createParentIfNotExists = (path: Path) =>
      path.parent.map(d => Files.createDirectories(d).whenZIO(Files.notExists(d)).unit).getOrElse(ZIO.unit)
    val unzipToFile = (path: Path) =>
      ZScopedJavaIoStreams.fileOutputStream(path).flatMap(fos => ZIO.attemptBlocking(zipInput.transferTo(fos)))
    ZIO.scoped {
      getEntry.tapSome { case Some(entry) =>
        val targetPath = destinationFolder / entry.getName
        entry match {
          case _ if entry.isDirectory => Files.createDirectories(targetPath)
          case _                      => createParentIfNotExists(targetPath) *> unzipToFile(targetPath)
        }
      }
    }
  }
}
