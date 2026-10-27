/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.test.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayersSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("TestContainerLayers")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig          <- ZIO.service[AppConfig]
        sipiContainer      <- ZIO.service[SipiTestContainer]
        dspIngestContainer <- ZIO.service[DspIngestTestContainer]
        configFromRuntime  <- ZIO.config(AppConfig.config).orDie
        images             <- ZIO.service[SharedVolumes.Images]
        storage            <- ZIO.service[StorageConfig]
        sipiConfig         <- ZIO.service[SipiConfig]
      } yield {
        assertTrue(
          appConfig.sipi.internalPort == sipiContainer.getFirstMappedPort,
          appConfig.dspIngest.baseUrl.endsWith(dspIngestContainer.getFirstMappedPort.toString),
          configFromRuntime.sipi.internalPort == sipiContainer.getFirstMappedPort,
          configFromRuntime.dspIngest.baseUrl.endsWith(dspIngestContainer.getFirstMappedPort.toString),
          storage.assetDir == images.hostPath,
          sipiConfig == SipiConfig(useLocalDev = true, localDevImage = sipiContainer.getDockerImageName),
        )
      }
    },
  ).provide(TestContainerLayers.all)
}
