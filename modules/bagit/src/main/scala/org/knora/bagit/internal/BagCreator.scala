/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Path

import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagCreator {

  def createBag(
    payloadEntries: List[PayloadEntry],
    algorithms: List[ChecksumAlgorithm],
    bagInfo: Option[BagInfo],
    outputZipPath: Path,
  ): IO[IOException, Path] =
    ZIO.scoped {
      for {
        zos                                      <- JioHelper.zipFileOutputStream(outputZipPath)
        payloadResult                            <- writePayloadEntries(zos, payloadEntries, algorithms)
        (payloadChecksums, totalBytes, fileCount) = payloadResult
        tagFiles                                 <- writeTagFiles(zos, algorithms, payloadChecksums, bagInfo, totalBytes, fileCount)
        _                                        <- writeTagManifests(zos, algorithms, tagFiles)
      } yield outputZipPath
    }

  private def writePayloadFile(
    zos: ZipOutputStream,
    zipPath: String,
    sourceFile: java.io.File,
    algorithms: List[ChecksumAlgorithm],
  ): IO[IOException, (String, Map[ChecksumAlgorithm, String], Long)] =
    ZIO.scoped {
      for {
        fis    <- JioHelper.bufferedFileInputStream(sourceFile)
        result <- ZIO.attemptBlocking {
                    val digests = algorithms.map(a => a -> MessageDigest.getInstance(a.javaName))
                    zos.putNextEntry(new ZipEntry(zipPath))
                    val buffer     = new Array[Byte](8192)
                    var bytesTotal = 0L
                    var read       = fis.read(buffer)
                    while (read != -1) {
                      zos.write(buffer, 0, read)
                      digests.foreach(_._2.update(buffer, 0, read))
                      bytesTotal += read
                      read = fis.read(buffer)
                    }
                    zos.closeEntry()
                    val checksums = digests.map { case (algo, md) =>
                      algo -> md.digest().map(b => String.format("%02x", b)).mkString
                    }.toMap
                    (zipPath, checksums, bytesTotal)
                  }.refineToOrDie[IOException]
      } yield result
    }

  private def writePayloadEntries(
    zos: ZipOutputStream,
    entries: List[PayloadEntry],
    algorithms: List[ChecksumAlgorithm],
  ): IO[IOException, (Map[String, Map[ChecksumAlgorithm, String]], Long, Long)] =
    ZIO
      .foreach(entries) {
        case PayloadEntry.File(relativePath, sourcePath) =>
          writePayloadFile(zos, s"data/$relativePath", sourcePath.toFile, algorithms).map(List(_))
        case PayloadEntry.Directory(prefix, sourcePath) =>
          val baseDir = sourcePath.toFile
          ZIO.foreach(JioHelper.walkDirectory(baseDir)) { file =>
            val rel     = baseDir.toPath.relativize(file.toPath).toString.replace('\\', '/')
            val zipPath = if (prefix.isEmpty) s"data/$rel" else s"data/$prefix/$rel"
            writePayloadFile(zos, zipPath, file, algorithms)
          }
      }
      .map { results =>
        val flat      = results.flatten
        val checksums = flat.map { case (path, cs, _) => path -> cs }.toMap
        val bytes     = flat.map(_._3).sum
        (checksums, bytes, flat.size.toLong)
      }

  private def writeTagFiles(
    zos: ZipOutputStream,
    algorithms: List[ChecksumAlgorithm],
    payloadChecksums: Map[String, Map[ChecksumAlgorithm, String]],
    bagInfo: Option[BagInfo],
    totalBytes: Long,
    fileCount: Long,
  ): IO[IOException, Map[String, Array[Byte]]] =
    ZIO.attemptBlocking {
      val bagitTxtContent = "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n"
      val bagitTxtBytes   = bagitTxtContent.getBytes("UTF-8")
      zos.putNextEntry(new ZipEntry("bagit.txt"))
      zos.write(bagitTxtBytes)
      zos.closeEntry()

      var tagFiles = Map[String, Array[Byte]]("bagit.txt" -> bagitTxtBytes)

      algorithms.foreach { algo =>
        val entries = payloadChecksums.toList.sortBy(_._1).map { case (path, checksums) =>
          s"${checksums(algo)}  ${ManifestPathEncoding.encode(path)}"
        }
        val manifestContent = entries.mkString("\n") + "\n"
        val manifestBytes   = manifestContent.getBytes("UTF-8")
        val filename        = s"manifest-${algo.bagitName}.txt"
        zos.putNextEntry(new ZipEntry(filename))
        zos.write(manifestBytes)
        zos.closeEntry()
        tagFiles = tagFiles + (filename -> manifestBytes)
      }

      bagInfo.foreach { info =>
        val enriched = info.copy(
          payloadOxum = Some(PayloadOxum(totalBytes, fileCount)),
          baggingDate = Some(info.baggingDate.getOrElse(LocalDate.now())),
        )
        val bagInfoContent = BagInfoWriter.write(enriched) + "\n"
        val bagInfoBytes   = bagInfoContent.getBytes("UTF-8")
        zos.putNextEntry(new ZipEntry("bag-info.txt"))
        zos.write(bagInfoBytes)
        zos.closeEntry()
        tagFiles = tagFiles + ("bag-info.txt" -> bagInfoBytes)
      }

      tagFiles
    }.refineToOrDie[IOException]

  private def writeTagManifests(
    zos: ZipOutputStream,
    algorithms: List[ChecksumAlgorithm],
    tagFiles: Map[String, Array[Byte]],
  ): IO[IOException, Unit] =
    ZIO.attemptBlocking {
      algorithms.foreach { algo =>
        val md      = MessageDigest.getInstance(algo.javaName)
        val entries = tagFiles.toList.sortBy(_._1).map { case (filename, bytes) =>
          md.reset()
          md.update(bytes)
          val checksum = md.digest().map(b => String.format("%02x", b)).mkString
          s"$checksum  $filename"
        }
        val tagManifestContent = entries.mkString("\n") + "\n"
        zos.putNextEntry(new ZipEntry(s"tagmanifest-${algo.bagitName}.txt"))
        zos.write(tagManifestContent.getBytes("UTF-8"))
        zos.closeEntry()
      }
    }.refineToOrDie[IOException]
}
