/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.durationInt
import zio.nio.file.Path

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagCreator {

  /** Emit a progress line each time cumulative packed bytes cross a step of this many percent. */
  private val ProgressStepPercent = 10

  /** Emit a progress line at least this often (evaluated after each file) even if no new step was crossed. */
  private val ProgressMaxGap = 5.minutes

  /** Maps the per-entry [[Compression]] to a `Deflater` level. `Store` is level 0 (see [[Compression.Store]]). */
  private def deflateLevel(c: Compression): Int = c match {
    case Compression.Deflate => Deflater.DEFAULT_COMPRESSION // -1 -> ~level 6
    case Compression.Store   => Deflater.NO_COMPRESSION      // 0  -> no effective compression
  }

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
    sourceFile: File,
    algorithms: List[ChecksumAlgorithm],
    compression: Compression,
  ): IO[IOException, (String, Map[ChecksumAlgorithm, String], Long)] =
    ZIO.scoped {
      for {
        fis    <- JioHelper.bufferedFileInputStream(sourceFile)
        result <- ZIO.attemptBlocking {
                    val digests = algorithms.map(a => a -> MessageDigest.getInstance(a.javaName))
                    // setLevel applies to subsequent entries, so it must be set before putNextEntry.
                    zos.setLevel(deflateLevel(compression))
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

  /** Flattens the payload entries into the concrete (zip path, source file, compression) tuples to be written. */
  private def collectFiles(entries: List[PayloadEntry]): List[(String, File, Compression)] =
    entries.flatMap {
      case PayloadEntry.File(relativePath, sourcePath, compression) =>
        List((s"data/$relativePath", sourcePath.toFile, compression))
      case PayloadEntry.Directory(prefix, sourcePath, compression) =>
        val baseDir = sourcePath.toFile
        JioHelper.walkDirectory(baseDir).map { file =>
          val rel     = baseDir.toPath.relativize(file.toPath).toString.replace('\\', '/')
          val zipPath = if (prefix.isEmpty) s"data/$rel" else s"data/$prefix/$rel"
          (zipPath, file, compression)
        }
    }

  private def writePayloadEntries(
    zos: ZipOutputStream,
    entries: List[PayloadEntry],
    algorithms: List[ChecksumAlgorithm],
  ): IO[IOException, (Map[String, Map[ChecksumAlgorithm, String]], Long, Long)] =
    for {
      files      <- ZIO.attemptBlocking(collectFiles(entries)).refineToOrDie[IOException]
      totalFiles  = files.size.toLong
      totalBytes <- ZIO.attemptBlocking(files.iterator.map(_._2.length()).sum).refineToOrDie[IOException]
      start      <- Clock.nanoTime
      _          <- ZIO.logDebug(BagProgress.startLine(totalFiles, totalBytes))
      progress   <- Ref.make(BagProgress.ReporterState(start, 0))
      bytesDone  <- Ref.make(0L)
      // Must stay sequential (ZIO.foreach, not foreachPar): per-entry setLevel mutates the single shared
      // Deflater in `zos`, and ZIP entry framing is inherently sequential -- parallelizing would corrupt the bag.
      results <- ZIO.foreach(files.zipWithIndex) { case ((zipPath, file, compression), idx) =>
                   writePayloadFile(zos, zipPath, file, algorithms, compression).tap { case (_, _, fileBytes) =>
                     logProgress(progress, bytesDone, fileBytes, idx + 1L, totalFiles, totalBytes, start)
                   }
                 }
      elapsed <- Clock.nanoTime.map(_ - start)
      _       <- ZIO.logDebug(BagProgress.doneLine(totalFiles, totalBytes, elapsed))
    } yield {
      val checksums = results.map { case (path, cs, _) => path -> cs }.toMap
      val bytes     = results.map(_._3).sum
      (checksums, bytes, results.size.toLong)
    }

  /**
   * Emits a progress line after a file is packed, throttled to one line per [[ProgressStepPercent]] of bytes
   * with a [[ProgressMaxGap]] wall-clock floor. Runs inline (no background fiber), so it cannot affect the
   * completion or interruption of the surrounding packing effect.
   */
  private def logProgress(
    progress: Ref[BagProgress.ReporterState],
    bytesDone: Ref[Long],
    fileBytes: Long,
    filesDone: Long,
    totalFiles: Long,
    totalBytes: Long,
    startNanos: Long,
  ): UIO[Unit] =
    if (totalBytes <= 0L) ZIO.unit
    else
      for {
        done  <- bytesDone.updateAndGet(_ + fileBytes)
        now   <- Clock.nanoTime
        step   = BagProgress.stepIndex(done, totalBytes, ProgressStepPercent)
        state <- progress.get
        _     <- ZIO
               .logInfo(BagProgress.progressLine(filesDone, totalFiles, done, totalBytes, now - startNanos))
               .zipRight(progress.set(BagProgress.ReporterState(now, step)))
               .when(BagProgress.shouldEmit(state, step, now, ProgressMaxGap.toNanos))
               .unit
      } yield ()

  private def writeTagFiles(
    zos: ZipOutputStream,
    algorithms: List[ChecksumAlgorithm],
    payloadChecksums: Map[String, Map[ChecksumAlgorithm, String]],
    bagInfo: Option[BagInfo],
    totalBytes: Long,
    fileCount: Long,
  ): IO[IOException, Map[String, Array[Byte]]] =
    ZIO.attemptBlocking {
      // Payload entries may have left the deflate level at 0 (Store); tag files are tiny but should be
      // written at the default level. This single reset also covers writeTagManifests, which runs afterward.
      zos.setLevel(Deflater.DEFAULT_COMPRESSION)

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
