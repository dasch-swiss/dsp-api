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
import zio.ZIO

object CommandExecutorLiveSpec extends ZIOSpecDefault {
  val devLayer = ZLayer.succeed(SipiConfig(useLocalDev = true)) >>> CommandExecutorLive.layer

  val spec = suite("CommandExecutorLive")(
    test("buildCommand with docker when useLocalDev is true") {
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
    test("log processing") {
      val logExample =
        """
        Something bad happened
        {"level": "ERROR", "message": "GET /0811/G5a5GeA4Jgn-ChDqwJzOJQM.jp2/0,2048,2048,111/1024,56/0/default.jpg failed (Not Found)"}
        """

      ZIO
        .service[CommandExecutor]
        .map { ce =>
          assertTrue(
            ce.parseSipiLogs(logExample) == List(
              "Sipi: INFO: Something bad happened",
              "Sipi: ERROR: GET /0811/G5a5GeA4Jgn-ChDqwJzOJQM.jp2/0,2048,2048,111/1024,56/0/default.jpg failed (Not Found)",
            ),
          )
        }
        .provideSomeLayer(devLayer)
    },
  ).provide(
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )
}
