/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import org.knora.bagit.BagItError
import org.knora.bagit.domain.ManifestEntry
import org.knora.bagit.domain.PayloadPath

object ManifestParser {

  private val hexPattern = "^[0-9a-fA-F]+$".r

  def parseLine(line: String): Either[BagItError, ManifestEntry] = {
    val parts = line.split("[ \\t]", 2)
    if (parts.length < 2)
      Left(BagItError.InvalidManifestEntry(line, "expected 'checksum  filepath'"))
    else {
      val checksum = parts(0).trim
      val path     = parts(1).trim
      if (checksum.isEmpty)
        Left(BagItError.InvalidManifestEntry(line, "empty checksum"))
      else if (!hexPattern.matches(checksum))
        Left(BagItError.InvalidManifestEntry(line, "checksum is not valid hex"))
      else if (path.isEmpty)
        Left(BagItError.InvalidManifestEntry(line, "empty path"))
      else {
        val decodedPath = ManifestPathEncoding.decode(path)
        PayloadPath(decodedPath).left
          .map(_ => BagItError.InvalidManifestEntry(line, s"invalid path: $decodedPath"))
          .map { pp =>
            ManifestEntry(checksum.toLowerCase, pp)
          }
      }
    }
  }

  def parseAll(lines: List[String]): Either[BagItError, List[ManifestEntry]] = {
    val nonEmpty = lines.filter(_.trim.nonEmpty)
    nonEmpty.foldLeft[Either[BagItError, List[ManifestEntry]]](Right(Nil)) { (acc, line) =>
      for {
        entries <- acc
        entry   <- parseLine(line)
      } yield entries :+ entry
    }
  }
}
