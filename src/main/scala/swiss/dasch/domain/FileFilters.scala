/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.PathOps.fileExtension
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.nio.file.{Files, Path}

import java.io.IOException

type FileFilter = Path => IO[IOException, Boolean]
object FileFilters {

  val isJpeg2000: FileFilter = hasFileExtension(Jpx.allExtensions)

  val isStillImage: FileFilter = hasFileExtension(SupportedFileType.StillImage.extensions)

  val isMovingImage: FileFilter = hasFileExtension(SupportedFileType.MovingImage.extensions)

  val isOther: FileFilter = hasFileExtension(SupportedFileType.Other.extensions)

  val isSupported: FileFilter = hasFileExtension(SupportedFileType.values.map(_.extensions).reduce(_ ++ _))

  val isNonHiddenRegularFile: FileFilter = (path: Path) => Files.isRegularFile(path) && Files.isHidden(path).negate

  val isBakFile: FileFilter = hasFileExtension(List("bak"))

  def hasFileExtension(extension: String): FileFilter = hasFileExtension(List(extension))

  def hasFileExtension(extension: Seq[String]): FileFilter = (path: Path) =>
    isNonHiddenRegularFile(path) && ZIO.succeed(extension.exists(_.equalsIgnoreCase(path.fileExtension)))
}
