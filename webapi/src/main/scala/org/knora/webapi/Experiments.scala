package org.knora.webapi

import zio._
import zio.logging.slf4j.bridge.Slf4jBridge

import org.knora.webapi.config.AppConfig

object Experiments extends App {

  /**
   * The `bootstrap` layer combines our app's layers with some configuration
   */
  val bootstrap: ZLayer[Any, Nothing, core.LayersLive.DSPEnvironmentLive] =
    ZLayer.empty ++ Runtime.removeDefaultLoggers ++
      logging.consoleJson() ++ Slf4jBridge.initialize ++ core.LayersLive.dspLayersLive

  // add scope to bootstrap
  val bootstrapWithScope = Scope.default >>>
    bootstrap +!+ ZLayer.environment[Scope]

  // maybe a configured runtime?
  val runtime = Unsafe.unsafe { implicit u =>
    Runtime.unsafe
      .fromLayer(bootstrapWithScope)
  }

  println("after configuring the runtime")

  // An effect for getting stuff out, so that we can pass them
  // to some legacy code
  val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  println("before running routerAndConfig")

  /**
   * Create managers and config by unsafe running them.
   */
  val (router, config) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          routerAndConfig
        )
        .getOrThrowFiberFailure()
    }

  println(router.ref)

  println("before running AppServer")

  // this effect represents our application
  val appServer =
    for {
      _     <- core.AppServer(false, false)
      never <- ZIO.never
    } yield never

  /* Here we start our main effect */
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(appServer)
  }

  println("before shutdown")

  /* Here we start our main effect */
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.shutdown()
  }

  println("after shutdown")
}
