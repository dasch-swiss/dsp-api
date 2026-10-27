/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.repo

import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.string.MatchesRegex
import zio.*
import zio.nio.file.Path

import java.io.FileInputStream
import java.io.FileNotFoundException

import org.knora.webapi.slice.ingest.infrastructure.IngestExecutor

type Sha256Hash = String Refined MatchesRegex["^[A-Fa-f0-9]{64}$"]
object Sha256Hash extends RefinedTypeOps[Sha256Hash, String]

final case class ChecksumResult(file: Path, checksumMatches: Boolean)

trait FileChecksumService {

  /** Takes an `IngestExecutor` permit while it reads the file. */
  def createSha256Hash(path: Path): IO[FileNotFoundException, Sha256Hash]
  def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]]
}
object FileChecksumService {
  def createSha256Hash(path: Path): ZIO[FileChecksumService, FileNotFoundException, Sha256Hash] =
    ZIO.serviceWithZIO[FileChecksumService](_.createSha256Hash(path))
  def verifyChecksum(assetInfo: AssetInfo): ZIO[FileChecksumService, Throwable, Chunk[ChecksumResult]] =
    ZIO.serviceWithZIO[FileChecksumService](_.verifyChecksum(assetInfo))
}

final case class FileChecksumServiceLive(ingestExecutor: IngestExecutor) extends FileChecksumService {

  override def createSha256Hash(path: Path): IO[FileNotFoundException, Sha256Hash] =
    ingestExecutor.withPermit {
      ZIO.scoped {
        ZIO
          .fromAutoCloseable(
            ZIO
              .attemptBlocking(new FileInputStream(path.toFile))
              .refineOrDie { case e: FileNotFoundException => e },
          )
          .flatMap(fis => ZIO.attemptBlocking(hexSha256(fis)).orDie)
          .flatMap(hex => ZIO.fromEither(Sha256Hash.from(hex)).mapError(IllegalStateException(_)).orDie)
      }
    }

  private def hexSha256(fis: FileInputStream): String = {
    val digest    = java.security.MessageDigest.getInstance("SHA-256")
    val buffer    = new Array[Byte](8192)
    var bytesRead = 0
    while ({
      bytesRead = fis.read(buffer)
      bytesRead != -1
    }) digest.update(buffer, 0, bytesRead)
    val sb = new StringBuilder
    for (byte <- digest.digest()) sb.append(String.format("%02x", Byte.box(byte)))
    sb.toString()
  }

  private def verifyChecksum(fileAndChecksum: FileAndChecksum): Task[Boolean] =
    createSha256Hash(fileAndChecksum.file)
      .logError(s"Unable to calculate checksum for ${fileAndChecksum.file}")
      .map(_ == fileAndChecksum.checksum)

  override def verifyChecksum(assetInfo: AssetInfo): Task[Chunk[ChecksumResult]] = {
    val original   = assetInfo.original
    val derivative = assetInfo.derivative
    for {
      origResult       <- verifyChecksum(original).map(ChecksumResult(original.file, _))
      derivativeResult <- verifyChecksum(derivative).map(ChecksumResult(derivative.file, _))
    } yield Chunk(origResult, derivativeResult)
  }
}

object FileChecksumServiceLive {
  val layer: URLayer[IngestExecutor, FileChecksumService] =
    ZLayer.derive[FileChecksumServiceLive]
}
