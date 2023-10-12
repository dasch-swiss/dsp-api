/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import zio.nio.file.*
import zio.test.{ZIOSpecDefault, assertCompletes, assertTrue}
import zio.{Exit, Scope, ZIO, ZLayer}

import java.io.IOException

object FileSystemCheckLiveSpec extends ZIOSpecDefault {

  val createOnlyAssetAndTempFolder: ZIO[Scope, Throwable, (String, String)] = for {
    tempStorage <- Files.createTempDirectoryScoped(None, List.empty)
    assetDirAbsolutePath <- {
      val assetDir = tempStorage / "assets"
      Files.createDirectories(assetDir) *> assetDir.toAbsolutePath
    }
    tempDirAbsolutePath <- {
      val tempDir = tempStorage / "temp"
      Files.createDirectories(tempDir) *> tempDir.toAbsolutePath
    }
  } yield (assetDirAbsolutePath.toString, tempDirAbsolutePath.toString)

  def spec = suite("FileSystemCheck")(
    test("should pass smoke test given the expected folders exist") {
      for {
        _ <- FileSystemCheck.smokeTestOrDie()
      } yield assertCompletes
    }.provide(SpecConfigurations.storageConfigLayer, FileSystemCheckLive.layer),
    test("should fail smoke test given the expected folders do not exist") {
      for {
        result <- FileSystemCheck.smokeTestOrDie().exit
      } yield assertTrue(result.isFailure)
    }.provide(ZLayer.succeed(StorageConfig("does-not-exist", "does-not-exist")), FileSystemCheckLive.layer)
  )
}
