/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.IOException
import java.util.zip.ZipInputStream

import org.knora.bagit.BagItError
import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.ExtractionLimits
import org.knora.bagit.domain.*

object BagReader {

  def readFromZip(
    zipPath: Path,
    limits: ExtractionLimits = ExtractionLimits.default,
    maxTagFileSize: Long = MaxTagFileSize,
  ): ZIO[Scope, IOException | BagItError, (Bag, Path)] =
    for {
      tempDir <- Files.createTempDirectoryScoped(Some("bagit-read"), Seq.empty)
      _       <- extractZip(zipPath, tempDir, limits)
      bagRoot <- detectBagRoot(tempDir)
      bag     <- parseBag(bagRoot, maxTagFileSize)
    } yield (bag, bagRoot)

  private def extractZip(
    zipPath: Path,
    targetDir: Path,
    limits: ExtractionLimits,
  ): IO[IOException | BagItError, Unit] = {
    def nextEntry(zis: ZipInputStream): IO[IOException, Option[java.util.zip.ZipEntry]] =
      ZIO.attemptBlocking(Option(zis.getNextEntry)).refineToOrDie[IOException]

    def copyToFile(
      zis: ZipInputStream,
      target: java.io.File,
      totalBytesRef: Ref[Long],
    ): IO[IOException | BagItError, Unit] =
      ZIO.scoped {
        JioHelper.fileOutputStream(target).flatMap { fos =>
          def writeLoop(entryBytes: Long): IO[IOException | BagItError, Unit] = {
            val buffer = new Array[Byte](8192)
            ZIO.attemptBlocking(zis.read(buffer)).refineToOrDie[IOException].flatMap {
              case -1        => ZIO.unit
              case bytesRead =>
                val newEntryBytes = entryBytes + bytesRead
                if (newEntryBytes > limits.maxSingleEntryBytes)
                  ZIO.fail(
                    BagItError.ExtractionLimitExceeded(
                      s"Single entry exceeds ${limits.maxSingleEntryBytes} bytes",
                    ),
                  )
                else
                  totalBytesRef.updateAndGet(_ + bytesRead).flatMap { total =>
                    if (total > limits.maxTotalBytes)
                      ZIO.fail(
                        BagItError.ExtractionLimitExceeded(
                          s"Total extracted size exceeds ${limits.maxTotalBytes} bytes",
                        ),
                      )
                    else
                      ZIO
                        .attemptBlocking(fos.write(buffer, 0, bytesRead))
                        .refineToOrDie[IOException] *>
                        writeLoop(newEntryBytes)
                  }
            }
          }
          writeLoop(0L)
        }
      }

    def extractEntry(
      zis: ZipInputStream,
      entry: java.util.zip.ZipEntry,
      totalBytesRef: Ref[Long],
    ): IO[IOException | BagItError, Unit] =
      ZIO.fromEither(PathSecurity.validateEntryName(entry.getName)) *> {
        val target = targetDir.toFile.toPath.resolve(entry.getName).toFile
        if (entry.isDirectory)
          ZIO.attemptBlocking(target.mkdirs()).unit.refineToOrDie[IOException]
        else
          ZIO.attemptBlocking(target.getParentFile.mkdirs()).refineToOrDie[IOException] *>
            copyToFile(zis, target, totalBytesRef)
      }

    def processEntries(
      zis: ZipInputStream,
      entryCount: Int,
      totalBytesRef: Ref[Long],
    ): IO[IOException | BagItError, Unit] =
      nextEntry(zis).flatMap {
        case None        => ZIO.unit
        case Some(entry) =>
          val newCount = entryCount + 1
          if (newCount > limits.maxEntryCount)
            ZIO.fail(
              BagItError.ExtractionLimitExceeded(
                s"Number of entries exceeds ${limits.maxEntryCount}",
              ),
            )
          else
            extractEntry(zis, entry, totalBytesRef) *>
              ZIO.attemptBlocking(zis.closeEntry()).refineToOrDie[IOException] *>
              processEntries(zis, newCount, totalBytesRef)
      }

    for {
      totalBytesRef <- Ref.make(0L)
      _             <- ZIO.scoped(JioHelper.zipFileInputStream(zipPath).flatMap(processEntries(_, 0, totalBytesRef)))
    } yield ()
  }

  private def detectBagRoot(extractDir: Path): IO[IOException, Path] =
    ZIO.attemptBlocking {
      // Check if bagit.txt is at root level
      val rootBagit = (extractDir / "bagit.txt").toFile
      if (rootBagit.exists()) extractDir
      else {
        // Check for single wrapper directory
        val children = extractDir.toFile.listFiles()
        if (children != null && children.length == 1 && children(0).isDirectory) {
          val nested = Path.fromJava(children(0).toPath)
          if ((nested / "bagit.txt").toFile.exists()) nested
          else extractDir // will fail later with MissingBagitTxt
        } else extractDir
      }
    }
      .refineToOrDie[IOException]

  private def parseBag(bagRoot: Path, maxTagFileSize: Long): IO[IOException | BagItError, Bag] =
    for {
      _            <- parseBagitTxt(bagRoot, maxTagFileSize)
      manifests    <- parseManifests(bagRoot, "manifest-", maxTagFileSize)
      tagManifests <- parseManifests(bagRoot, "tagmanifest-", maxTagFileSize)
      bagInfo      <- parseBagInfo(bagRoot, maxTagFileSize)
      payloadFiles <- enumeratePayloadFiles(bagRoot)
      _            <- ZIO.when(manifests.isEmpty)(ZIO.fail(BagItError.MissingPayloadManifest))
      dataDir       = (bagRoot / "data").toFile
      _            <- ZIO.when(!dataDir.exists() || !dataDir.isDirectory)(ZIO.fail(BagItError.MissingPayloadDirectory))
    } yield Bag(
      version = "1.0",
      encoding = "UTF-8",
      manifests = manifests,
      tagManifests = tagManifests,
      bagInfo = bagInfo,
      payloadFiles = payloadFiles,
    )

  private val MaxTagFileSize: Long = 10L * 1024 * 1024 // 10 MB

  private def checkFileSize(file: java.io.File, maxSize: Long): IO[BagItError, Unit] = {
    val size = file.length()
    if (size > maxSize) ZIO.fail(BagItError.FileTooLarge(file.getName, size))
    else ZIO.unit
  }

  private def readContent(file: java.io.File, maxSize: Long): IO[IOException | BagItError, String] =
    checkFileSize(file, maxSize) *>
      ZIO.scoped {
        ZIO
          .fromAutoCloseable(ZIO.attemptBlocking(scala.io.Source.fromFile(file, "UTF-8")))
          .flatMap(source => ZIO.attemptBlocking(source.mkString))
          .refineToOrDie[IOException]
      }

  private def readLines(file: java.io.File, maxSize: Long): IO[IOException | BagItError, List[String]] =
    checkFileSize(file, maxSize) *>
      ZIO.scoped {
        ZIO
          .fromAutoCloseable(ZIO.attemptBlocking(scala.io.Source.fromFile(file, "UTF-8")))
          .flatMap(source => ZIO.attemptBlocking(source.getLines().toList))
          .refineToOrDie[IOException]
      }

  private def parseBagitTxt(bagRoot: Path, maxSize: Long): IO[IOException | BagItError, Unit] = {
    val bagitTxt = (bagRoot / "bagit.txt").toFile
    if (!bagitTxt.exists()) ZIO.fail(BagItError.MissingBagitTxt)
    else
      readContent(bagitTxt, maxSize)
        .map(content => content.split("\n", -1).map(_.stripSuffix("\r")).filter(_.nonEmpty).toList)
        .flatMap { lines =>
          if (lines.length != 2)
            ZIO.fail(BagItError.InvalidBagitTxt("Expected exactly two lines"))
          else if (lines(0) != "BagIt-Version: 1.0")
            ZIO.fail(BagItError.InvalidBagitTxt("Expected first line to be 'BagIt-Version: 1.0'"))
          else if (lines(1) != "Tag-File-Character-Encoding: UTF-8")
            ZIO.fail(BagItError.InvalidBagitTxt("Expected second line to be 'Tag-File-Character-Encoding: UTF-8'"))
          else ZIO.unit
        }
  }

  private def parseManifests(
    bagRoot: Path,
    prefix: String,
    maxSize: Long,
  ): IO[IOException | BagItError, List[Manifest]] =
    ZIO.attemptBlocking {
      val files = bagRoot.toFile.listFiles()
      if (files == null) Nil
      else
        files.toList.filter { f =>
          f.isFile && f.getName.startsWith(prefix) && f.getName.endsWith(".txt")
        }
    }
      .refineToOrDie[IOException]
      .flatMap { manifestFiles =>
        ZIO.foreach(manifestFiles) { file =>
          val algoName = file.getName.stripPrefix(prefix).stripSuffix(".txt")
          for {
            algo    <- ZIO.fromEither(ChecksumAlgorithm.fromBagitName(algoName))
            lines   <- readLines(file, maxSize)
            entries <- ZIO.fromEither(ManifestParser.parseAll(lines))
          } yield Manifest(algo, entries)
        }
      }

  private def parseBagInfo(bagRoot: Path, maxSize: Long): IO[IOException | BagItError, Option[BagInfo]] = {
    val bagInfoFile = (bagRoot / "bag-info.txt").toFile
    if (!bagInfoFile.exists()) ZIO.none
    else
      for {
        lines   <- readLines(bagInfoFile, maxSize)
        bagInfo <- ZIO.fromEither(BagInfoParser.parse(lines))
      } yield Some(bagInfo)
  }

  private def enumeratePayloadFiles(bagRoot: Path): IO[IOException | BagItError, List[PayloadPath]] = {
    val dataDir = (bagRoot / "data").toFile
    if (!dataDir.exists() || !dataDir.isDirectory) ZIO.succeed(Nil)
    else
      ZIO
        .attemptBlocking(JioHelper.walkDirectory(dataDir))
        .refineToOrDie[IOException]
        .flatMap { files =>
          ZIO.foreach(files) { file =>
            val relativePath = dataDir.toPath.relativize(file.toPath).toString.replace('\\', '/')
            ZIO.fromEither(PayloadPath(relativePath))
          }
        }
  }
}
