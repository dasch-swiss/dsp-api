package dsp.api.main

import zio._
import dsp.user.route.UserRoutes
import dsp.user.handler.UserHandler
import dsp.user.repo.impl.UserRepoLive
import zio.logging.removeDefaultLoggers
import zio.metrics.connectors.MetricsConfig
import zio.logging.backend.SLF4J
import dsp.config.AppConfig

object DspMain extends ZIOAppDefault {

  /**
   * Configures Metrics to be run at a set interval, in this case every 5 seconds
   */
  val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))
  override val run: Task[Unit] =
    ZIO
      .serviceWithZIO[DspServer](_.start)
      .provide(
        // ZLayer.Debug.mermaid,
        // server
        DspServer.layer,
        // configuration
        AppConfig.live,
        // routes
        UserRoutes.layer,
        // handlers
        UserHandler.layer,
        // repositories
        UserRepoLive.layer,
        // slf4j facade, we use it with logback.xml
        removeDefaultLoggers,
        SLF4J.slf4j
        // metricsConfig
      )

}
