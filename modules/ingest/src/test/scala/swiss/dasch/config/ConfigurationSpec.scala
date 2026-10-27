/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.version.BuildInfo
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.ingest.config.BulkIngestConfig
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.config.StorageConfig

@RunWith(classOf[DspZTestJUnitRunner])
class ConfigurationSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("Configuration")(
    test("reads the standalone keys while webapi's application.conf is on the classpath") {
      for {
        service <- ZIO.service[ServiceConfig]
        storage <- ZIO.service[StorageConfig]
        sipi    <- ZIO.service[SipiConfig]
        bulk    <- ZIO.service[BulkIngestConfig]
      } yield assertTrue(
        ConfigFactory.defaultApplication().hasPath("app.dsp-ingest"),
        service.port == 3340,
        storage.assetDir == "localdev/storage/images",
        sipi.useLocalDev,
        sipi.localDevImage == s"daschswiss/knora-sipi:${BuildInfo.knoraSipiVersion}",
        bulk.bulkMaxParallel == 8,
      )
    }.provide(Configuration.layer),
  )
}
