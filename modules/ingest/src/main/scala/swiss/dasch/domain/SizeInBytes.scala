/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.IO
import zio.json.JsonCodec
import zio.nio.file.Files
import zio.nio.file.Path
import zio.schema.Schema

import java.io.IOException

final case class SizeInBytes(value: Long) extends AnyVal

object SizeInBytes {
  def of(path: Path): IO[IOException, SizeInBytes] = Files.size(path).map(SizeInBytes.apply)

  given codec: JsonCodec[SizeInBytes] = JsonCodec[Long].transform(SizeInBytes.apply, _.value)
  given schema: Schema[SizeInBytes]   = Schema[Long].transform(SizeInBytes.apply, _.value)
}
