package org.knora.webapi.config

import org.knora.webapi.store.cacheservice.config.CacheServiceConfig

import zio.config._
import zio.config.ConfigDescriptor
import zio.config.magnolia.DeriveConfigDescriptor

final case class AppConfig(cacheService: CacheServiceConfig)

object AppConfig {
  val descriptor: ConfigDescriptor[AppConfig] = DeriveConfigDescriptor.descriptor[AppConfig].mapKey(toKebabCase)
}
