/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.nio.file.{ Files, Path }

import java.io.IOException

type FileFilter = Path => IO[IOException, Boolean]
object FileFilters {

  val isJpeg2000: FileFilter = hasFileExtension(Jpx.allExtensions)

  val isImage: FileFilter = hasFileExtension(SipiImageFormat.allExtensions)

  val isNonHiddenRegularFile: FileFilter = (path: Path) => Files.isRegularFile(path) && Files.isHidden(path).negate

  val isBakFile: FileFilter = hasFileExtension(List("bak"))

  def hasFileExtension(extension: String): FileFilter = hasFileExtension(List(extension))

  def hasFileExtension(extension: List[String]): FileFilter = (path: Path) =>
    isNonHiddenRegularFile(path) &&
    ZIO.succeed(extension.contains(FilenameUtils.getExtension(path.filename.toString).toLowerCase))

}
