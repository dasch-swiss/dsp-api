/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.MetricsServer
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.util.Logger

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = LayersLive.DspEnvironmentLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override def bootstrap: ZLayer[Any, Nothing, Environment] =
    Logger.fromEnv() >>> LayersLive.dspLayersLive

  /**
   *  Entrypoint of our Application
   */
  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] =
    AppServer.make *> MetricsServer.make
}

object MainTwo extends ZIOAppDefault {

  private def testRun: ZIO[KnoraProjectRepo, Throwable, Unit] = for {
    repo    <- ZIO.service[KnoraProjectRepo]
    project <- repo.findByShortcode(Shortcode.unsafeFrom("0001"))
    _       <- Console.printLine(project.toString)
  } yield ()

  def app(): Task[Unit] = for {
    _ <- testRun.provide(
           KnoraProjectRepoLive.layer,
           TriplestoreServiceLive.layer,
           AppConfig.layer,
           StringFormatter.live,
           PredicateObjectMapper.layer,
           IriConverter.layer
         )
  } yield ()

  val run: ZIO[Any, Nothing, Unit] = app().orDie
}
