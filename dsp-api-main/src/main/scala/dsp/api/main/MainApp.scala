package dsp.api.main

import zio._
import dsp.user.route.UserRoutes
import dsp.user.handler.UserHandler
import dsp.user.repo.impl.UserRepoLive
import zio.logging.removeDefaultLoggers
import zio.metrics.connectors.MetricsConfig
import zio.logging.backend.SLF4J

object MainApp extends ZIOAppDefault {

  /**
   * Configures Metrics to be run at a set interval, in our case every five
   * seconds
   */
  val metricsConfig =
    ZLayer.succeed(MetricsConfig(5.seconds))
  override val run: Task[Unit] =
    ZIO
      .serviceWithZIO[DspServer](server => server.start())
      .provide(
        // ZLayer.Debug.mermaid,
        DspServer.layer,
        // Routes
        UserRoutes.layer,
        // VetRoutes.layer,
        // OwnerRoutes.layer,
        // VisitRoutes.layer,
        // // Repositories
        UserRepoLive.layer,
        // OwnerServiceLive.layer,
        // PetServiceLive.layer,
        // VetServiceLive.layer,
        // VisitServiceLive.layer,
        // Migrations.layer,
        // QuillContext.dataSourceLayer,
        // // Handlers
        UserHandler.layer,
        // // Operations
        SLF4J.slf4j,
        removeDefaultLoggers
        // newrelic.newRelicLayer,
        // newrelic.NewRelicConfig.fromEnvLayer,
        // metricsConfig
      )

}
