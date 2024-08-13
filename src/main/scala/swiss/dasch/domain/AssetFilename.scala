/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.nio.file.*

import java.text.Normalizer

final case class AssetFilename private (value: String) extends AnyVal

object AssetFilename {
  // Allow letters, numbers, underscores, hyphens, spaces, full stops, comma, single quote, apostrophe and braces
  private val regex = """^[\p{L}\p{N}_\- .,'`()]+$""".r

  def from(valueUnnormalized: String): Either[String, AssetFilename] = {
    val value       = Normalizer.normalize(valueUnnormalized, Normalizer.Form.NFC)
    val valueAsPath = Path(value)

    for {
      _ <- if (valueAsPath.normalize.filename.toString != value) {
             Left("Filename must not contain any path information")
           } else { Right(()) }
      _ <- SupportedFileType.fromPath(valueAsPath).toRight("Filename must have a valid file extension")
      filename <- if (regex.matches(value)) { Right(new AssetFilename(value)) }
                  else { Left("Filename contains invalid characters") }
    } yield filename
  }
}
