/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecFileUtil.pathFromResource
import zio.{ Layer, ZLayer }
import zio.nio.file.Files

import java.io.IOException

object SpecConfigurations {

  val storageConfigLayer: Layer[IOException, StorageConfig] = ZLayer.scoped {
    for {
      tmpDir <- Files.createTempDirectoryScoped(None, List.empty)
    } yield StorageConfig(
      assetDir = pathFromResource("/test-folder-structure").toFile.getAbsolutePath,
      tempDir = tmpDir.toFile.toString,
    )
  }
}
