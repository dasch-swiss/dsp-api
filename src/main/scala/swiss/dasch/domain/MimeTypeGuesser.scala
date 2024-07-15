/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.Path
import zio.ZLayer

final case class MimeType private (value: NonEmptyString) extends AnyVal {
  def stringValue: String = value.value
}
object MimeType {

  def unsafeFrom(str: String): MimeType =
    from(str).fold(msg => throw new IllegalArgumentException(msg), identity)

  def from(str: String): Either[String, MimeType] =
    Option(str)
      .toRight("MIME type cannot be null")
      .flatMap(it => NonEmptyString.from(it).left.map(_ => "MIME type cannot be empty"))
      .map(MimeType.apply)
}

final case class MimeTypeGuesser() {

  private val allMappings: Map[String, MimeType] = SupportedFileType.values.flatMap(_.mappings).toMap

  def guess(filename: NonEmptyString): Option[MimeType] = guess(Path(filename.value))

  def guess(file: Path): Option[MimeType] = allMappings.get(file.fileExtension.toLowerCase)
}

object MimeTypeGuesser {

  val layer = ZLayer.derive[MimeTypeGuesser]
}
