/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*
import zio.nio.file.*

import java.io.*
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

object ZipUtility {

  /**
   * Zips a directory recursively, it will traverse and include files in subfolder, empty directories are not included.
   * The zip file is created in the specified destination directory. Should the destination directory not exist, it is
   * created. Should the ZIP file already exist, it is overwritten.
   *
   * @param srcFolder
   *   The path to the directory to be zipped.
   * @param destinationFolder
   *   The path to the destination directory.
   * @param zipFilename
   *   The name of the zip file to be created. If not specified, the name of the source directory is used.
   * @return
   *   The [[zio.nio.file.Path]]to the created zip file.
   */
  def zipFolder(
    srcFolder: Path,
    destinationFolder: Path,
    zipFilename: Option[String] = None,
  ): Task[Path] = ZIO.scoped {
    val zipFile = destinationFolder / zipFilename.getOrElse(s"${srcFolder.filename.toString}.zip")
    for {
      _      <- ZIO.whenZIO(Files.notExists(destinationFolder))(Files.createDirectory(destinationFolder))
      zipOut <- createTargetZipFileStream(zipFile)
      _      <- addFolder(srcFolder, zipOut)
    } yield zipFile
  }

  private def createTargetZipFileStream(targetFile: Path) =
    for {
      _ <-
        ZIO
          .whenZIO(Files.notExists(targetFile))(Files.createFile(targetFile))
          .orElse(Files.delete(targetFile) *> Files.createFile(targetFile))
      zipOut <- ScopedIoStreams.zipFileOutputStream(targetFile)
    } yield zipOut

  private def addFolder(folderToAdd: Path, out: ZipOutputStream): ZIO[Scope, Throwable, Unit] =
    addFolderRec(folderToAdd, folderToAdd, out)

  private def addFolderRec(
    folderToAdd: Path,
    srcFolder: Path,
    out: ZipOutputStream,
  ): ZIO[Scope, Throwable, Unit] =
    Files
      .newDirectoryStream(folderToAdd)
      .runCollect
      .flatMap(ZIO.foreach(_)(addEntryToZip(_, srcFolder, out)).unit)

  private def addEntryToZip(
    entry: Path,
    srcFolder: Path,
    out: ZipOutputStream,
  ) = for {
    _ <- ZIO.whenZIO(Files.isRegularFile(entry))(addFile(entry, srcFolder, out))
    _ <- ZIO.whenZIO(Files.isDirectory(entry))(addFolderRec(entry, srcFolder, out))
  } yield ()

  private def addFile(
    entry: Path,
    srcFolder: Path,
    out: ZipOutputStream,
  ) = for {
    fis <- ScopedIoStreams.fileInputStream(entry)
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
   * @param zipFile
   *   The path to the zip file to be unzipped.
   * @param destinationFolder
   *   The path to the folder where the content of the zip file is written to.
   */
  def unzipFile(zipFile: Path, destinationFolder: Path): Task[Path] = ZIO.scoped {
    ScopedIoStreams.zipFileInputStream(zipFile).flatMap(unzip(_, destinationFolder)).as(destinationFolder)
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
      ScopedIoStreams.fileOutputStream(path).flatMap(fos => ZIO.attemptBlocking(zipInput.transferTo(fos)))
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

object ScopedIoStreams {

  /**
   * Creates a [[java.io.FileInputStream]] by opening a connection to an actual file, the file named by the File object
   * file in the filesystem.
   *
   * @param path
   *   the actual file to read from, must exist
   *
   * @return
   *   The [[java.io.FileInputStream]]. If the named file does not exist, is a directory rather than a regular file, or
   *   for some other reason cannot be opened for reading then a [[java.io.FileNotFoundException]] is returned.
   */
  def fileInputStream(path: Path): ZIO[Scope, FileNotFoundException, FileInputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new FileInputStream(path.toFile))
        .refineOrDie { case e: FileNotFoundException => e },
    )

  /**
   * Creates a new managed [[java.util.zip.ZipOutputStream]] which writes into a file using an underlying
   * [[java.io.FileOutputStream]]. The UTF-8 charset is used to encode the entry names and comments.
   *
   * @param path
   *   the actual File to write to, must exist
   *
   * @return
   *   The [[java.util.zip.ZipOutputStream]]. If the file exists but is a directory rather than a regular file, does
   *   not exist but cannot be created, or cannot be opened for any other reason then a
   *   [[java.io.FileNotFoundException]] is returned.
   */
  def zipFileOutputStream(path: Path): ZIO[Scope, FileNotFoundException, ZipOutputStream] =
    fileOutputStream(path).flatMap(zipOutputStream)

  /**
   * Creates a new managed [[java.util.zip.ZipOutputStream]]. The UTF-8 charset is used to encode the entry names and
   * comments.
   *
   * @param out
   *   the actual output stream
   * @return
   *   The [[java.util.zip.ZipOutputStream]]
   */
  def zipOutputStream(out: OutputStream): URIO[Scope, ZipOutputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(new ZipOutputStream(out)))

  private val defaultBufferSize = 4096

  def byteArrayOutputStream(bufferSize: Int = defaultBufferSize): URIO[Scope, ByteArrayOutputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(new ByteArrayOutputStream(bufferSize)))

  def byteArrayInputStream(bufferSize: Int = defaultBufferSize): URIO[Scope, ByteArrayInputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(new ByteArrayInputStream(new Array[Byte](bufferSize))))

  /**
   * Creates a new managed [[java.io.FileOutputStream]] by opening a connection to an actual file, the file named by
   * the File object file in the filesystem.
   *
   * @param path
   *   the actual file to write to, must exist
   * @return
   *   The [[java.io.FileOutputStream]]. If the named file does not exist, is a directory rather than a regular file,
   *   or for some other reason cannot be opened for reading then a [[java.io.FileNotFoundException]] is returned.
   */
  def fileOutputStream(path: Path): ZIO[Scope, FileNotFoundException, FileOutputStream] =
    ZIO.fromAutoCloseable(
      ZIO
        .attemptBlocking(new FileOutputStream(path.toFile))
        .refineOrDie { case e: FileNotFoundException => e },
    )

  /**
   * Creates a new managed [[java.util.zip.ZipInputStream]] which reads from a file using an underlying
   * [[java.io.FileInputStream]]. The UTF-8 charset is used to encode the entry names and comments.
   *
   * @param path
   *   the actual File to read from, must exist
   *
   * @return
   *   The [[java.util.zip.ZipInputStream]]. If the file exists but is a directory rather than a regular file, does not
   *   exist but cannot be created, or cannot be opened for any other reason then a [[java.io.FileNotFoundException]]
   *   is returned.
   */
  def zipFileInputStream(path: Path): ZIO[Scope, FileNotFoundException, ZipInputStream] =
    fileInputStream(path).flatMap(zipInputStream)

  /**
   * Creates a new managed [[java.util.zip.ZipInputStream]]. The UTF-8 charset is used to decode the entry names.
   *
   * @param in
   *   the actual [[java.io.InputStream]]
   * @return
   *   the [[java.util.zip.ZipInputStream]]
   */
  def zipInputStream(in: InputStream): URIO[Scope, ZipInputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(new ZipInputStream(in)))
}
