/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.*
import zio.*
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ConfigurationSpec extends ZIOSpecDefault {

  /** `${?VAR}` substitutions find root keys before environment variables, so these stand in for the environment. */
  private def loadWith(env: Map[String, String]) =
    Configuration
      .fromTypesafeConfig(
        ConfigFactory.parseResources("ingest.conf").withFallback(ConfigFactory.parseMap(env.asJava)).resolve(),
      )
      .build

  val spec = suite("Configuration")(
    test("loads the defaults from ingest.conf") {
      for {
        env <- Configuration.layer.build
      } yield assertTrue(
        env.get[StorageConfig] == StorageConfig("/opt/images", "/opt/temp"),
        env.get[SipiConfig] == SipiConfig(useLocalDev = false, localDevImage = "daschswiss/knora-sipi:latest"),
        env.get[IngestConfig] == IngestConfig(8),
        env.get[Features] == Features(allowEraseProjects = false),
        env.get[ExecutorConfig] == ExecutorConfig(0.5),
      )
    },
    test("applies INGEST_* overrides") {
      for {
        env <- loadWith(
                 Map(
                   "INGEST_STORAGE_ASSET_DIR"    -> "/data/images",
                   "INGEST_SIPI_USE_LOCAL_DEV"   -> "true",
                   "INGEST_ALLOW_ERASE_PROJECTS" -> "true",
                   "INGEST_EXECUTOR_CPU_SHARE"   -> "1",
                 ),
               )
      } yield assertTrue(
        env.get[StorageConfig].assetDir == "/data/images",
        env.get[SipiConfig].useLocalDev,
        env.get[Features].allowEraseProjects,
        env.get[ExecutorConfig] == ExecutorConfig(1.0),
      )
    },
    test("fails for a cpu-share outside (0, 1]") {
      for {
        zero  <- loadWith(Map("INGEST_EXECUTOR_CPU_SHARE" -> "0")).exit
        above <- loadWith(Map("INGEST_EXECUTOR_CPU_SHARE" -> "1.5")).exit
      } yield assertTrue(zero.isFailure, above.isFailure)
    },
    test("is not read by ConfigFactory.load()") {
      assertTrue(!ConfigFactory.load().hasPath("ingest"))
    },
  )
}
