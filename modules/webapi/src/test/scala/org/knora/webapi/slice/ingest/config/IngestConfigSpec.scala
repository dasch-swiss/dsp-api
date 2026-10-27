/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class IngestConfigSpec extends ZIOSpecDefault {

  private def env(vars: (String, String)*) = ConfigFactory.parseMap(vars.toMap.asJava)

  private def envVarsIn(resource: String): Set[String] = {
    val text = Using.resource(Source.fromResource(resource))(_.mkString)
    """\$\{\?([A-Za-z0-9_]+)\}""".r.findAllMatchIn(text).map(_.group(1)).toSet
  }

  def spec: Spec[Any, Any] = suite("IngestConfig")(
    test("loads the defaults") {
      for {
        c <- IngestConfig.load(env())
      } yield assertTrue(
        c == IngestConfig(
          storage = StorageConfig(assetDir = "/opt/images", tempDir = "/opt/temp"),
          sipi = SipiConfig(useLocalDev = false, localDevImage = "daschswiss/knora-sipi:latest"),
          bulkMaxParallel = 8,
          features = IngestFeatures(allowEraseProjects = false),
          executor = ExecutorConfig(cpuShare = 0.5),
        ),
      )
    },
    test("applies every INGEST_ environment override") {
      for {
        c <- IngestConfig.load(
               env(
                 "INGEST_STORAGE_ASSET_DIR"    -> "/assets",
                 "INGEST_STORAGE_TEMP_DIR"     -> "/tmp/ingest",
                 "INGEST_SIPI_USE_LOCAL_DEV"   -> "true",
                 "INGEST_SIPI_LOCAL_DEV_IMAGE" -> "daschswiss/knora-sipi:v1.2.3",
                 "INGEST_BULK_MAX_PARALLEL"    -> "3",
                 "INGEST_ALLOW_ERASE_PROJECTS" -> "true",
                 "INGEST_EXECUTOR_CPU_SHARE"   -> "0.25",
               ),
             )
      } yield assertTrue(
        c == IngestConfig(
          storage = StorageConfig(assetDir = "/assets", tempDir = "/tmp/ingest"),
          sipi = SipiConfig(useLocalDev = true, localDevImage = "daschswiss/knora-sipi:v1.2.3"),
          bulkMaxParallel = 3,
          features = IngestFeatures(allowEraseProjects = true),
          executor = ExecutorConfig(cpuShare = 0.25),
        ),
      )
    },
    test("accepts a cpu-share of 1") {
      IngestConfig.load(env("INGEST_EXECUTOR_CPU_SHARE" -> "1")).map(c => assertTrue(c.executor.cpuShare == 1.0))
    },
    test("fails to load a cpu-share outside (0, 1]") {
      check(Gen.fromIterable(List("0", "-0.5", "1.01", "2"))) { share =>
        IngestConfig
          .load(env("INGEST_EXECUTOR_CPU_SHARE" -> share))
          .flip
          .map(error => assertTrue(error.getMessage.contains("cpu-share must be in (0, 1]")))
      }
    },
    test("webapi's app configuration carries no ingest key") {
      assertTrue(!ConfigFactory.load().getConfig("app").hasPath("ingest"))
    },
    test("every ingest environment variable is INGEST_-prefixed and none is read by application.conf") {
      val ingestVars = envVarsIn("ingest.conf")
      val webapiVars = envVarsIn("application.conf")
      assertTrue(
        ingestVars == Set(
          "INGEST_STORAGE_ASSET_DIR",
          "INGEST_STORAGE_TEMP_DIR",
          "INGEST_SIPI_USE_LOCAL_DEV",
          "INGEST_SIPI_LOCAL_DEV_IMAGE",
          "INGEST_BULK_MAX_PARALLEL",
          "INGEST_ALLOW_ERASE_PROJECTS",
          "INGEST_EXECUTOR_CPU_SHARE",
        ),
        webapiVars.contains("KNORA_WEBAPI_DSP_INGEST_AUDIENCE"),
        ingestVars.intersect(webapiVars).isEmpty,
        !webapiVars.exists(_.startsWith("INGEST_")),
      )
    },
  )
}
