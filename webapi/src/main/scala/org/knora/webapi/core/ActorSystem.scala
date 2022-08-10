package org.knora.webapi.core

import akka.actor
import org.knora.webapi.settings.KnoraSettingsImpl
import zio._
import zio.macros.accessible
import org.knora.webapi.store.cache.settings.CacheServiceSettings

@accessible
trait ActorSystem {
  val system: actor.ActorSystem
  val settings: KnoraSettingsImpl
  val cacheServiceSettings: CacheServiceSettings
}
