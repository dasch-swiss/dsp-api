/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test
import swiss.dasch.config.Configuration.{ JwtConfig, StorageConfig }
import swiss.dasch.test.SpecPaths.pathFromResource
import zio.nio.file.Files.newDirectoryStream
import zio.{ Layer, ULayer, ZIO, ZLayer }
import zio.nio.file.{ Files, Path }
import zio.stream.ZStream

import java.io.IOException

object SpecConfigurations {

  val jwtConfigLayer: ULayer[JwtConfig] =
    ZLayer.succeed(JwtConfig("secret-key", "https://dsp-ingest.dev.dasch.swiss", "https://admin.dev.dasch.swiss"))

  val storageConfigLayer: Layer[IOException, StorageConfig] = ZLayer.scoped {
    for {
      tmpDir       <- Files.createTempDirectoryScoped(None, List.empty)
      assetDir      = tmpDir / "images"
      tempDir       = tmpDir / "tmp"
      _            <- Files.createDirectories(assetDir)
      _            <- Files.createDirectories(tempDir)
      storageConfig = StorageConfig(assetDir.toFile.toString, tempDir.toFile.toString)
      _            <- Files.createDirectories(storageConfig.exportPath)
      _            <- Files.createDirectories(storageConfig.importPath)
      _            <- copyDirectory(SpecPaths.testFolder, storageConfig.assetPath)
    } yield storageConfig
  }

  private def copyDirectory(source: Path, dest: Path): ZIO[Any, IOException, Long] =
    Files.createDirectories(dest) *> Files
      .list(source)
      .mapZIO { file =>
        copyDirectory(file, dest / file.filename)
          .whenZIO(Files.isDirectory(file))
          *> Files
            .copy(file, dest / file.filename)
            .whenZIO(Files.isRegularFile(file))
      }
      .runCount
}
