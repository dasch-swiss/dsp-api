/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.infrastructure

import org.junit.runner.RunWith
import swiss.dasch.version.BuildInfo
import zio.ZIO
import zio.ZLayer
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.repo.StorageService
import org.knora.webapi.slice.ingest.repo.StorageServiceLive
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations

@RunWith(classOf[DspZTestJUnitRunner])
class CommandExecutorLiveSpec extends ZIOSpecDefault {
  val devLayer = ZLayer.succeed(SipiConfig(useLocalDev = true)) >>> CommandExecutorLive.layer

  val spec = suite("CommandExecutorLive")(
    test("buildCommand with docker when useLocalDev is true") {
      for {
        cmd      <- CommandExecutor.buildCommand("customCommand", "customParams").provideSome[StorageService](devLayer)
        assetDir <- StorageService.getAssetsBaseFolder().flatMap(_.toAbsolutePath)
        expected  =
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
