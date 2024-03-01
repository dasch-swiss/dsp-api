/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.PathOps.fileExtension
import zio.json.JsonCodec
import zio.nio.file.Path

private val archive = Map(
  "7z"     -> MimeType.unsafeFrom("application/x-7z-compressed"),
  "gz"     -> MimeType.unsafeFrom("application/gzip"),
  "gzip"   -> MimeType.unsafeFrom("application/gzip"),
  "tar"    -> MimeType.unsafeFrom("application/x-tar"),
  "tar.gz" -> MimeType.unsafeFrom("application/gzip"),
  "tgz"    -> MimeType.unsafeFrom("application/x-compress"),
  "z"      -> MimeType.unsafeFrom("application/x-compress"),
  "zip"    -> MimeType.unsafeFrom("application/zip")
)
private val audio =
  Map(
    "mp3"  -> MimeType.unsafeFrom("audio/mpeg"),
    "mpeg" -> MimeType.unsafeFrom("audio/mpeg"),
    "wav"  -> MimeType.unsafeFrom("audio/wav")
  )
private val office = Seq(
  "doc"  -> MimeType.unsafeFrom("application/msword"),
  "docx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
  "pdf"  -> MimeType.unsafeFrom("application/pdf"),
  "ppt"  -> MimeType.unsafeFrom("application/vnd.ms-powerpoint"),
  "pptx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.presentationml.presentation")
)
private val tables = Map(
  "csv"  -> MimeType.unsafeFrom("text/csv"),
  "xls"  -> MimeType.unsafeFrom("application/vnd.ms-excel"),
  "xlsx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
)
private val text =
  Map(
    // odd and rng are TEI formats, see
    // https://tei-c.org/guidelines/customization/getting-started-with-p5-odds/#section-2
    "odd" -> MimeType.unsafeFrom("application/odd+xml"),
    "rng" -> MimeType.unsafeFrom("application/rng+xml"),
    "txt" -> MimeType.unsafeFrom("text/plain"),
    // xml, xsd, xsl are XML files, schema and stylesheets
    "xml" -> MimeType.unsafeFrom("application/xml"),
    "xsd" -> MimeType.unsafeFrom("application/xsd+xml"),
    "xsl" -> MimeType.unsafeFrom("application/xslt+xml")
  )

private val other = archive ++ audio ++ office ++ tables ++ text

private val movingImages = Map("mp4" -> MimeType.unsafeFrom("video/mp4"))

private val stillImages = Map(
  "jpx"  -> MimeType.unsafeFrom("image/jpx"),
  "jp2"  -> MimeType.unsafeFrom("image/jp2"),
  "jpg"  -> MimeType.unsafeFrom("image/jpeg"),
  "jpeg" -> MimeType.unsafeFrom("image/jpeg"),
  "tiff" -> MimeType.unsafeFrom("image/tiff"),
  "tif"  -> MimeType.unsafeFrom("image/tiff"),
  "png"  -> MimeType.unsafeFrom("image/png")
)

/**
 * Enumeration of supported file types.
 * See also https://docs.dasch.swiss/2023.11.02/DSP-API/01-introduction/file-formats/
 *
 * @param extensions the file extensions of the supported file types.
 */
enum SupportedFileType(val mappings: Map[String, MimeType]) derives JsonCodec {
  case StillImage  extends SupportedFileType(stillImages)
  case MovingImage extends SupportedFileType(movingImages)
  case OtherFiles  extends SupportedFileType(other)

  val extensions: Seq[String]                      = mappings.keys.toSeq
  def acceptsExtension(extension: String): Boolean = extensions.exists(extension.equalsIgnoreCase)
}

object SupportedFileType {

  def fromPath(path: Path): Option[SupportedFileType] =
    SupportedFileType.values.find(_.acceptsExtension(path.fileExtension))
}
