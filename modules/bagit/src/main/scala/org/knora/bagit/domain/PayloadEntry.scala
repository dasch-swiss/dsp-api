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
   * method — the entry method stays `DEFLATED`, so the observable effect is compressed size ~= uncompressed
   * size rather than a `STORED` method flag. True STORED would need the CRC-32 and size up front, forcing a
   * second read pass over every file.
   */
  case Store
}

enum PayloadEntry {
  case File(relativePath: String, sourcePath: Path, compression: Compression)
  case Directory(prefix: String, sourcePath: Path, compression: Compression)
}
