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
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagCreator {

  /** How often to wake the reporter fiber to evaluate whether a progress line is due. */
  private val ProgressPollInterval = 30.seconds

  /** Emit a progress line on each step of this many percent of the payload bytes. */
  private val ProgressStepPercent = 10

  /** Never let the heartbeat stay silent longer than this, even within a single large file. */
  private val ProgressMaxGap = 5.minutes

  /** Thread-safe live counters shared between the packing loop and the reporter fiber. */
  private final class ProgressCounter {
    val bytes: AtomicLong = new AtomicLong(0L)
    val files: AtomicLong = new AtomicLong(0L)
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
    counter: ProgressCounter,
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
                      counter.bytes.addAndGet(read.toLong)
                      read = fis.read(buffer)
                    }
                    zos.closeEntry()
                    counter.files.incrementAndGet()
                    val checksums = digests.map { case (algo, md) =>
                      algo -> md.digest().map(b => String.format("%02x", b)).mkString
                    }.toMap
                    (zipPath, checksums, bytesTotal)
                  }.refineToOrDie[IOException]
      } yield result
    }

  /** Flattens the payload entries into the concrete (zip path, source file) pairs to be written. */
  private def collectFiles(entries: List[PayloadEntry]): List[(String, File)] =
    entries.flatMap {
      case PayloadEntry.File(relativePath, sourcePath) =>
        List((s"data/$relativePath", sourcePath.toFile))
      case PayloadEntry.Directory(prefix, sourcePath) =>
        val baseDir = sourcePath.toFile
        JioHelper.walkDirectory(baseDir).map { file =>
          val rel     = baseDir.toPath.relativize(file.toPath).toString.replace('\\', '/')
          val zipPath = if (prefix.isEmpty) s"data/$rel" else s"data/$prefix/$rel"
          (zipPath, file)
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
      counter     = new ProgressCounter()
      start      <- Clock.nanoTime
      _          <- ZIO.logDebug(BagProgress.startLine(totalFiles, totalBytes))
      results    <- writeWithProgress(zos, files, algorithms, counter, totalFiles, totalBytes, start)
      elapsed    <- Clock.nanoTime.map(_ - start)
      _          <- ZIO.logDebug(BagProgress.doneLine(totalFiles, totalBytes, elapsed))
    } yield {
      val checksums = results.map { case (path, cs, _) => path -> cs }.toMap
      val bytes     = results.map(_._3).sum
      (checksums, bytes, results.size.toLong)
    }

  /** Writes all payload files, running a background heartbeat fiber alongside for progress logging. */
  private def writeWithProgress(
    zos: ZipOutputStream,
    files: List[(String, File)],
    algorithms: List[ChecksumAlgorithm],
    counter: ProgressCounter,
    totalFiles: Long,
    totalBytes: Long,
    startNanos: Long,
  ): IO[IOException, List[(String, Map[ChecksumAlgorithm, String], Long)]] = {
    val writeAll = ZIO.foreach(files) { case (zipPath, file) =>
      writePayloadFile(zos, zipPath, file, algorithms, counter)
    }
    if (totalBytes <= 0L) writeAll
    else ZIO.scoped(reporter(counter, totalFiles, totalBytes, startNanos).forkScoped *> writeAll)
  }

  /** Background fiber: periodically logs packing progress until interrupted by the caller. */
  private def reporter(
    counter: ProgressCounter,
    totalFiles: Long,
    totalBytes: Long,
    startNanos: Long,
  ): UIO[Unit] = {
    val maxGapNanos                                       = ProgressMaxGap.toNanos
    def loop(state: BagProgress.ReporterState): UIO[Unit] =
      for {
        _         <- Clock.sleep(ProgressPollInterval)
        now       <- Clock.nanoTime
        bytesDone  = counter.bytes.get()
        step       = BagProgress.stepIndex(bytesDone, totalBytes, ProgressStepPercent)
        nextState <- if (BagProgress.shouldEmit(state, step, now, maxGapNanos))
                       ZIO
                         .logInfo(
                           BagProgress
                             .progressLine(counter.files.get(), totalFiles, bytesDone, totalBytes, now - startNanos),
                         )
                         .as(BagProgress.ReporterState(now, step))
                     else ZIO.succeed(state)
        _ <- loop(nextState)
      } yield ()
    loop(BagProgress.ReporterState(startNanos, 0))
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
