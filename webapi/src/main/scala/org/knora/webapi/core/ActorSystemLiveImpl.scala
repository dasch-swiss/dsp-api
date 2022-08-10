package org.knora.webapi.core

import akka.actor
import org.knora.webapi.config.AppConfig
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import zio._
import zio.macros.accessible
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory

final case class AppActorSystemLiveImpl(_system: actor.ActorSystem) extends ActorSystem { self =>

  override val system: actor.ActorSystem                  = self._system
  override val settings: KnoraSettingsImpl                = KnoraSettings(self._system)
  override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(self._system.settings.config)
}

object AppActorSystemLiveImpl {

  private def acquire(config: AppConfig) =
    ZIO
      .attempt(actor.ActorSystem("webapi"))
      .tap(_ => ZIO.debug(">>> Acquire Live Actor System <<<"))
      .orDie

  private def release(system: actor.ActorSystem) =
    ZIO
      .attempt(system.terminate())
      .tap(_ => ZIO.logInfo(">>> Release Live Actor System <<<"))
      .orDie

  val layer: ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[AppConfig]
        system <- ZIO.acquireRelease(acquire(config))(release(_))
        _      <- ZIO.attempt(StringFormatter.init(KnoraSettings(system))).orDie   // needs early init before first usage
        _      <- ZIO.attempt(RdfFeatureFactory.init(KnoraSettings(system))).orDie // needs early init before first usage
      } yield AppActorSystemLiveImpl(system)
    }
}
