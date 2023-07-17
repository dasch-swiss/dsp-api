/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*
import zio.nio.file.{ Files, Path }
import zio.stream.ZStream

object MaintenanceActions {

  private val targetFormat = Tif
  def createTifOriginals(projectPath: Path)
      : ZStream[SipiClient with ProjectService, Throwable, (AssetId, Path, Path, SipiOutput)] =
    findJpxFiles(projectPath)
      .flatMap(findAssetsWithoutOriginal)
      .mapZIOPar(8) { c =>
        ZIO.logInfo(s"Creating ${c.originalPath} for ${c.jpxPath}") *>
          SipiClient
            .transcodeImageFile(fileIn = c.jpxPath, fileOut = c.originalPath, outputFormat = targetFormat)
            .map(sipiOut => (c.assetId, c.jpxPath, c.originalPath, sipiOut))
      }

  private def findJpxFiles(projectPath: Path): ZStream[Any, Throwable, Path] =
    Files
      .walk(projectPath)
      .filterZIO(p =>
        Files.isRegularFile(p) && Files.isHidden(p).negate && ZIO.succeed(p.filename.toString.endsWith(".jpx"))
      )

  final private case class CreateOriginalFor(
      assetId: AssetId,
      jpxPath: Path,
      originalPath: Path,
    )

  private def findAssetsWithoutOriginal(jpxPath: Path): ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath)
      case None          => ZStream.logWarning(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(assetId: AssetId, jpxPath: Path): ZStream[Any, Throwable, CreateOriginalFor] = {
    val originalPath: Path = jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
    ZStream
      .fromZIO(Files.exists(originalPath))
      .flatMap {
        case true  =>
          ZStream.logInfo(s"Original for $jpxPath present, skipping $originalPath") *>
            ZStream.empty
        case false =>
          ZStream.logDebug(s"Original for $jpxPath not present") *>
            ZStream.succeed(CreateOriginalFor(assetId, jpxPath, originalPath))
      }
  }
}
