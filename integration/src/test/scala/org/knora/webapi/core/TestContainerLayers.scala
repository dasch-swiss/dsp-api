/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import monocle.Lens
import monocle.macros.GenLens
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayers { self =>

  type Environment = SharedVolumes.Images & SharedVolumes.Temp & DspIngestTestContainer & FusekiTestContainer &
    SipiTestContainer & AppConfigurations

  val all: ULayer[self.Environment] =
    SharedVolumes.layer >+> DspIngestTestContainer.layer >+> FusekiTestContainer.layer >+> SipiTestContainer.layer >+>
      AppConfigForTestContainers.testcontainers

  private object AppConfigForTestContainers {

    private val fusekiPort: Lens[AppConfig, Int]          = GenLens[AppConfig](_.triplestore.fuseki.port)
    private val sipiPort: Lens[AppConfig, Int]            = GenLens[AppConfig](_.sipi.internalPort)
    private val dspIngestBaseUrl: Lens[AppConfig, String] = GenLens[AppConfig](_.dspIngest.baseUrl)

    private def updateConfig(
      oldConfig: AppConfig,
      fusekiContainer: FusekiTestContainer,
      sipiContainer: SipiTestContainer,
      dspIngestContainer: DspIngestTestContainer,
    ): AppConfig = {
      val update = fusekiPort
        .replace(fusekiContainer.getFirstMappedPort)
        .andThen(sipiPort.replace(sipiContainer.getFirstMappedPort))
        .andThen(dspIngestBaseUrl.replace(s"http://localhost:${dspIngestContainer.getFirstMappedPort}"))
      update(oldConfig)
    }

    /**
     * Altered AppConfig with ports from TestContainers for DSP-Ingest, Fuseki and Sipi.
     */
    val testcontainers
      : ZLayer[DspIngestTestContainer & FusekiTestContainer & SipiTestContainer, Nothing, AppConfigurations] = {
      val appConfigLayer = ZLayer {
        for {
          appConfig          <- AppConfig.parseConfig
          fusekiContainer    <- ZIO.service[FusekiTestContainer]
          sipiContainer      <- ZIO.service[SipiTestContainer]
          dspIngestContainer <- ZIO.service[DspIngestTestContainer]
        } yield updateConfig(appConfig, fusekiContainer, sipiContainer, dspIngestContainer)
      }
      AppConfig
        .projectAppConfigurations(appConfigLayer)
        .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki and Sipi Testcontainers Initialized <<<"))
    }
  }
}
