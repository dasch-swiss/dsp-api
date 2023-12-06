/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain
import swiss.dasch.domain.PathOps.fileExtension
import zio.*
import zio.nio.file.Path

private val archive = Seq("7z", "gz", "gzip", "tar", "tar.gz", "tgz", "z", "zip")
private val audio   = Seq("mp3", "mpeg", "wav")
private val office  = Seq("doc", "docx", "pdf", "ppt", "pptx")
private val tables  = Seq("csv", "xls", "xslx")
private val text    = Seq("odd", "rng", "txt", "xml", "xsd", "xsl")

/**
 * Enumeration of supported file types.
 * See also https://docs.dasch.swiss/2023.11.02/DSP-API/01-introduction/file-formats/
 *
 * @param extensions the file extensions of the supported file types.
 */
enum SupportedFileType(val extensions: Seq[String]) {
  case StillImage  extends SupportedFileType(SipiImageFormat.allExtensions)
  case MovingImage extends SupportedFileType(Seq("mp4"))
  case Other       extends SupportedFileType(archive ++ audio ++ office ++ tables ++ text)
}

object SupportedFileType {

  def fromPath(path: Path): Option[SupportedFileType] =
    SupportedFileType.values.find(_.extensions.exists(path.fileExtension.equalsIgnoreCase(_)))
}
