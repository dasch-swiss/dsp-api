/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*
import zio.nio.file.*

import java.io.{ FileInputStream, IOException }

trait FileChecksum {
  def checksum(path: Path): Task[String]
}

object FileChecksum {
  def checksum(path: Path): ZIO[FileChecksum, Throwable, String] = ZIO.serviceWithZIO[FileChecksum](_.checksum(path))
}

case class FileChecksumLive() extends FileChecksum {

  def checksum(path: Path): Task[String] =
    ZIO.scoped {
      for {
        fis        <- ZIO.acquireRelease(ZIO.attempt(new FileInputStream(path.toFile)))(fis => ZIO.succeed(fis.close()))
        hashString <- ZIO.attempt(hashSha256(fis))
      } yield hashString
    }

  private def hashSha256(fis: FileInputStream) = {
    val digest    = java.security.MessageDigest.getInstance("SHA-256")
    val buffer    = new Array[Byte](8192)
    var bytesRead = 0
    while ({ bytesRead = fis.read(buffer); bytesRead != -1 }) digest.update(buffer, 0, bytesRead)
    val sb        = new StringBuilder
    for (byte <- digest.digest()) sb.append(String.format("%02x", Byte.box(byte)))
    sb.toString()
  }
}
object FileChecksumLive {
  val layer: ULayer[FileChecksum] = ZLayer.succeed(FileChecksumLive())
}
