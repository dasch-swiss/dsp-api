/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import zio.*
import zio.nio.file.Path

import org.knora.bagit.BagItError
import org.knora.bagit.ChecksumAlgorithm
import org.knora.bagit.domain.*

object BagValidator {

  def validate(bag: Bag, bagRoot: Path): IO[BagItError, Unit] =
    checkStructural(bag, bagRoot) *>
      checkCompleteness(bag, bagRoot) *>
      checkChecksums(bag, bagRoot) *>
      checkTagManifests(bag, bagRoot)

  private def checkStructural(bag: Bag, bagRoot: Path): IO[BagItError, Unit] =
    for {
      _      <- ZIO.when(bag.manifests.isEmpty)(ZIO.fail(BagItError.MissingPayloadManifest))
      dataDir = (bagRoot / "data").toFile
      _      <- ZIO.when(!dataDir.exists() || !dataDir.isDirectory)(ZIO.fail(BagItError.MissingPayloadDirectory))
    } yield ()

  private def checkCompleteness(bag: Bag, bagRoot: Path): IO[BagItError, Unit] = {
    val dataDir     = (bagRoot / "data").toFile
    val actualFiles = if (dataDir.exists() && dataDir.isDirectory) walkFiles(dataDir) else Nil
    val actualPaths = actualFiles.map(f => dataDir.toPath.relativize(f.toPath).toString.replace('\\', '/')).toSet

    // Every manifest entry must have a corresponding file
    val checkEntries = ZIO.foreachDiscard(bag.manifests) { manifest =>
      ZIO.foreachDiscard(manifest.entries) { entry =>
        val path    = PayloadPath.value(entry.path)
        val relPath = if (path.startsWith("data/")) path.stripPrefix("data/") else path
        ZIO.when(!actualPaths.contains(relPath))(ZIO.fail(BagItError.ManifestEntryMissing(path)))
      }
    }

    // Every actual file must be in every manifest
    val checkFiles = ZIO.foreachDiscard(bag.manifests) { manifest =>
      val entryPaths = manifest.entries.map { e =>
        val p = PayloadPath.value(e.path)
        if (p.startsWith("data/")) p.stripPrefix("data/") else p
      }.toSet
      ZIO.foreachDiscard(actualPaths) { path =>
        ZIO.when(!entryPaths.contains(path))(ZIO.fail(BagItError.FileNotInManifest(s"data/$path")))
      }
    }

    checkEntries *> checkFiles
  }

  private def checkChecksums(bag: Bag, bagRoot: Path): IO[BagItError, Unit] =
    ZIO.foreachDiscard(bag.manifests) { manifest =>
      ZIO.foreachDiscard(manifest.entries) { entry =>
        val path     = PayloadPath.value(entry.path)
        val filePath =
          if (path.startsWith("data/")) bagRoot / path
          else bagRoot / "data" / path
        val file = filePath.toFile
        if (!file.exists()) ZIO.unit // already caught by completeness check
        else
          ZIO.scoped {
            JioHelper.fileInputStream(file).flatMap(manifest.algorithm.computeDigest(_))
          }.orDie.flatMap { actual =>
            if (actual == entry.checksum) ZIO.unit
            else ZIO.fail(BagItError.ChecksumMismatch(path, manifest.algorithm.bagitName, entry.checksum, actual))
          }
      }
    }

  private def checkTagManifests(bag: Bag, bagRoot: Path): IO[BagItError, Unit] =
    ZIO.foreachDiscard(bag.tagManifests) { manifest =>
      ZIO.foreachDiscard(manifest.entries) { entry =>
        val path = PayloadPath.value(entry.path)
        val file = (bagRoot / path).toFile
        if (!file.exists()) ZIO.fail(BagItError.ManifestEntryMissing(path))
        else
          ZIO.scoped {
            JioHelper.fileInputStream(file).flatMap { is =>
              manifest.algorithm.computeDigest(is)
            }
          }.orDie.flatMap { actual =>
            if (actual == entry.checksum) ZIO.unit
            else ZIO.fail(BagItError.ChecksumMismatch(path, manifest.algorithm.bagitName, entry.checksum, actual))
          }
      }
    }

  private def walkFiles(dir: java.io.File): List[java.io.File] =
    if (!dir.isDirectory) Nil
    else {
      val result = List.newBuilder[java.io.File]
      val stack  = scala.collection.mutable.ArrayDeque[java.io.File](dir)
      while (stack.nonEmpty) {
        val current       = stack.removeLast()
        val children      = Option(current.listFiles()).map(_.toList).getOrElse(Nil)
        val (dirs, files) = children.partition(_.isDirectory)
        result ++= files.sortBy(_.getName)
        dirs.sortBy(_.getName).reverse.foreach(stack.append)
      }
      result.result()
    }
}
