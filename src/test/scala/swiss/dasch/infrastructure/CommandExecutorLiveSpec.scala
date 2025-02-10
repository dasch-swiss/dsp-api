/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.{StorageService, StorageServiceLive}
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.version.BuildInfo
import zio.ZLayer
import zio.test.{ZIOSpecDefault, assertTrue}

object CommandExecutorLiveSpec extends ZIOSpecDefault {

  val spec = suite("CommandExecutorLive")(
    test("buildCommand with docker when useLocalDev is true") {
      val devLayer = ZLayer.succeed(SipiConfig(useLocalDev = true)) >>> CommandExecutorLive.layer
      for {
        cmd      <- CommandExecutor.buildCommand("customCommand", "customParams").provideSome[StorageService](devLayer)
        assetDir <- StorageService.getAssetsBaseFolder().flatMap(_.toAbsolutePath)
        expected =
          s"docker run --entrypoint customCommand -v $assetDir:$assetDir daschswiss/knora-sipi:${BuildInfo.knoraSipiVersion} customParams"
      } yield assertTrue(cmd.cmd.mkString(" ") == expected)
    },
    test("buildCommand without docker when useLocalDev is false") {
      val prodLayer = ZLayer.succeed(SipiConfig(useLocalDev = false)) >>> CommandExecutorLive.layer
      for {
        cmd     <- CommandExecutor.buildCommand("customCommand", "customParams").provideSome[StorageService](prodLayer)
        expected = "customCommand customParams"
      } yield assertTrue(cmd.cmd.mkString(" ") == expected)
    },
  ).provide(
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )
}
