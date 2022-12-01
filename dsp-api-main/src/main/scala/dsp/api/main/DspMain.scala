package dsp.api.main

import zio._
import dsp.user.route.UserRoutes
import dsp.user.handler.UserHandler
import dsp.user.repo.impl.UserRepoLive
import zio.logging.removeDefaultLoggers
import zio.logging.backend.SLF4J
import dsp.config.AppConfig
import dsp.util.UuidGeneratorLive

object DspMain extends ZIOAppDefault {

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
        SLF4J.slf4j,
        UuidGeneratorLive.layer
      )

}
