/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import zio.nio.file.Path

enum Compression {

  /** DEFLATE at the default level (~6). */
  case Deflate

  /**
   * No effective compression: written as DEFLATE level 0 (`Deflater.NO_COMPRESSION`), NOT the ZIP `STORED`
   * method — the entry method stays `DEFLATED`. Chosen over true STORED to keep the single-pass streaming
   * writer: STORED requires the CRC-32 and size up front, which would force a second read pass over every
   * file. The observable effect is compressed size ~= uncompressed size, not a `STORED` method flag.
   */
  case Store
}

enum PayloadEntry {
  case File(relativePath: String, sourcePath: Path, compression: Compression)
  case Directory(prefix: String, sourcePath: Path, compression: Compression)
}
