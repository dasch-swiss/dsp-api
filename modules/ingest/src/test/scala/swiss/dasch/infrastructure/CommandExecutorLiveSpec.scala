/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.StorageService
import swiss.dasch.domain.StorageServiceLive
import swiss.dasch.test.SpecConfigurations
import zio.ZIO
import zio.ZLayer
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class CommandExecutorLiveSpec extends ZIOSpecDefault {
  val devLayer =
    ZLayer.succeed(SipiConfig(useLocalDev = true, localDevImage = "sipi:local-dev")) >>> CommandExecutorLive.layer

  val prodLayer = ZLayer.succeed(
    SipiConfig(useLocalDev = false, localDevImage = "daschswiss/knora-sipi:latest"),
  ) >>> CommandExecutorLive.layer

  val spec = suite("CommandExecutorLive")(
    test("buildCommand with docker when useLocalDev is true") {
      for {
        cmd <- CommandExecutor
                 .buildCommand("customCommand", "customParams")
                 .provideSome[IngestExecutor & StorageService](devLayer)
        assetDir <- StorageService.getAssetsBaseFolder().flatMap(_.toAbsolutePath)
        expected  =
          s"docker run --entrypoint customCommand -v $assetDir:$assetDir sipi:local-dev customParams"
      } yield assertTrue(cmd.cmd.mkString(" ") == expected)
    },
    test("buildCommand without docker, through the oom_score_adj wrapper, when useLocalDev is false") {
      for {
        cmd <- CommandExecutor
                 .buildCommand("customCommand", "customParams")
                 .provideSome[IngestExecutor & StorageService](prodLayer)
      } yield assertTrue(
        cmd.cmd == List(
          "/bin/sh",
          "-c",
          """echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"""",
          "customCommand",
          "customParams",
        ),
      )
    },
    test("a command run outside local-dev mode is the OOM killer's first choice") {
      for {
        executor <- ZIO.service[CommandExecutor].provideSome[IngestExecutor & StorageService](prodLayer)
        cmd      <- executor.buildCommand("cat", "/proc/self/oom_score_adj")
        out      <- executor.executeOrFail(cmd)
      } yield assertTrue(out.stdout.trim == "1000")
    } @@ TestAspect.ifProp("os.name")(_.contains("Linux")),
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
    SpecConfigurations.ingestExecutorLayer,
    SpecConfigurations.storageConfigLayer,
    StorageServiceLive.layer,
  )
}
