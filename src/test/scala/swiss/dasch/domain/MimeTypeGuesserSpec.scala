/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}
import zio.{URIO, ZIO}

object MimeTypeGuesserSpec extends ZIOSpecDefault {

  private val filesAndMimeTypes = Map(
    // archive
    "7z"     -> MimeType.unsafeFrom("application/x-7z-compressed"),
    "gz"     -> MimeType.unsafeFrom("application/gzip"),
    "gzip"   -> MimeType.unsafeFrom("application/gzip"),
    "tar"    -> MimeType.unsafeFrom("application/x-tar"),
    "tar.gz" -> MimeType.unsafeFrom("application/gzip"),
    "tgz"    -> MimeType.unsafeFrom("application/x-compress"),
    "z"      -> MimeType.unsafeFrom("application/x-compress"),
    "zip"    -> MimeType.unsafeFrom("application/zip"),
    // audio =
    "mp3"  -> MimeType.unsafeFrom("audio/mpeg"),
    "mpeg" -> MimeType.unsafeFrom("audio/mpeg"),
    "wav"  -> MimeType.unsafeFrom("audio/wav"),
    // office
    "doc"  -> MimeType.unsafeFrom("application/msword"),
    "docx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    "pdf"  -> MimeType.unsafeFrom("application/pdf"),
    "ppt"  -> MimeType.unsafeFrom("application/vnd.ms-powerpoint"),
    "pptx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    // tables
    "csv"  -> MimeType.unsafeFrom("text/csv"),
    "xls"  -> MimeType.unsafeFrom("application/vnd.ms-excel"),
    "xlsx" -> MimeType.unsafeFrom("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    // text
    "odd" -> MimeType.unsafeFrom("application/odd+xml"),
    "rng" -> MimeType.unsafeFrom("application/rng+xml"),
    "txt" -> MimeType.unsafeFrom("text/plain"),
    "xml" -> MimeType.unsafeFrom("application/xml"),
    "xsd" -> MimeType.unsafeFrom("application/xsd+xml"),
    "xsl" -> MimeType.unsafeFrom("application/xslt+xml")
  )

  private def guess(file: Path): URIO[MimeTypeGuesser, Option[MimeType]] =
    ZIO.serviceWith[MimeTypeGuesser](_.guess(file))

  val spec = suite("MimeTypeGuesser")(
    test("should guess the mime type from the filename") {
      check(Gen.fromIterable(filesAndMimeTypes ++ filesAndMimeTypes.map((k, v) => (k.toUpperCase, v)))) {
        case (ext, expected) =>
          guess(Path("test." + ext)).map(result => assertTrue(result.contains(expected)))
      }
    },
    test("should return None if it cannot guess the mimetype") {
      guess(Path("test")).map(result => assertTrue(result.isEmpty))
    }
  ).provide(MimeTypeGuesser.layer)
}
