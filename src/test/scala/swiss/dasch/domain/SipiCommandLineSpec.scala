/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.nio.file.*
import zio.test.*

object SipiCommandLineSpec extends ZIOSpecDefault {
  private val localDevSuite = suite("SipiCommandLineLive set up for local development")(
    test("should assemble help command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.help())
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --help"
      )
    },
    test("should assemble compare command") {
      for {
        assetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath)
        cmd       <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"docker run --entrypoint /sipi/sipi -v $assetPath:$assetPath daschswiss/knora-sipi:latest --compare /tmp/example /tmp/example2"
      )
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = true)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLineLive.layer,
  )

  private val liveSuite = suite("SipiCommandLineLive set up with local sipi executable")(
    test("should assemble help command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.help())
      } yield assertTrue(cmd == s"/sipi/sipi --help")
    },
    test("should assemble compare command") {
      for {
        cmd <- ZIO.serviceWithZIO[SipiCommandLine](_.compare(Path("/tmp/example"), Path("/tmp/example2")))
      } yield assertTrue(
        cmd == s"/sipi/sipi --compare /tmp/example /tmp/example2"
      )
    },
  ).provide(
    ZLayer.succeed(SipiConfig(useLocalDev = false)),
    SpecConfigurations.storageConfigLayer,
    SipiCommandLineLive.layer,
  )

  val spec = suite("SipiCommand")(
    localDevSuite,
    liveSuite,
  )
}
