/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.config

import zio.nio.file.Path

final case class StorageConfig(assetDir: String, tempDir: String) {
  val assetPath: Path = Path(assetDir)
  val tempPath: Path  = Path(tempDir)
}

final case class SipiConfig(useLocalDev: Boolean)

final case class BulkIngestConfig(bulkMaxParallel: Int)
