/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import zio.*
import zio.config.*
import zio.config.typesafe.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayers { self =>

  type Environment = SharedVolumes.Images & SharedVolumes.Temp & DspIngestTestContainer & FusekiTestContainer &
    SipiTestContainer & AppConfigurations

  private type ContainersEnv =
    SharedVolumes.Images & SharedVolumes.Temp & DspIngestTestContainer & FusekiTestContainer & SipiTestContainer

  private val containersLayer: ULayer[ContainersEnv] =
    SharedVolumes.layer >+> DspIngestTestContainer.layer >+> FusekiTestContainer.layer >+> SipiTestContainer.layer

  /**
   * The custom JUnit runner ([[zio.test.junit.DspZTestRunnerBase]]) builds each spec class's
   * `bootstrap` against its own fresh `Runtime` - a per-spec `val`/`ZLayer` composition here would
   * therefore start a new Fuseki/Sipi/dsp-ingest container set per spec class instead of sharing
   * one set across the whole test JVM. Cache the *built environment* once per JVM instead: a
   * process-static `lazy val`, built via a one-off `Unsafe.run` extended into [[Scope.global]] -
   * the standing, never-closed scope ZIO provides for exactly this "shared for the life of the
   * process" case. Containers are therefore never stopped by a per-spec scope release; the
   * Testcontainers Ryuk sidecar (and its JVM-exit hook) reaps them when the process ends.
   */
  private lazy val cachedContainers: ZEnvironment[ContainersEnv] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(Scope.global.extend[Any](containersLayer.build)).getOrThrowFiberFailure()
    }

  val all: ULayer[self.Environment] =
    ZLayer.succeedEnvironment(cachedContainers) >+> AppConfigForTestContainers.testcontainers

  private object AppConfigForTestContainers {

    private def providerFor(
      fusekiContainer: FusekiTestContainer,
      sipiContainer: SipiTestContainer,
      dspIngestContainer: DspIngestTestContainer,
    ): ConfigProvider =
      TypesafeConfigProvider.fromTypesafeConfig(
        ConfigFactory
          .load()
          .getConfig("app")
          .resolve()
          .withValue("triplestore.fuseki.port", ConfigValueFactory.fromAnyRef(fusekiContainer.getFirstMappedPort))
          .withValue("sipi.internal-port", ConfigValueFactory.fromAnyRef(sipiContainer.getFirstMappedPort))
          .withValue(
            "dsp-ingest.base-url",
            ConfigValueFactory.fromAnyRef(s"http://localhost:${dspIngestContainer.getFirstMappedPort}"),
          ),
      )

    /**
     * Altered AppConfig with ports from TestContainers for DSP-Ingest, Fuseki and Sipi.
     */
    val testcontainers
      : ZLayer[DspIngestTestContainer & FusekiTestContainer & SipiTestContainer, Nothing, AppConfigurations] = {
      val providerLayer = ZLayer {
        for {
          fusekiContainer    <- ZIO.service[FusekiTestContainer]
          sipiContainer      <- ZIO.service[SipiTestContainer]
          dspIngestContainer <- ZIO.service[DspIngestTestContainer]
        } yield providerFor(fusekiContainer, sipiContainer, dspIngestContainer)
      }
      providerLayer.flatMap { env =>
        val provider       = env.get[ConfigProvider]
        val appConfigLayer = ZLayer.fromZIO(read(AppConfig.config from provider).orDie)
        Runtime.setConfigProvider(provider) >>>
          AppConfig
            .projectAppConfigurations(appConfigLayer)
            .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki and Sipi Testcontainers Initialized <<<"))
      }
    }
  }
}
