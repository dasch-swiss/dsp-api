package org.knora.webapi.config

import org.knora.webapi.store.cacheservice.config.CacheServiceConfig

import zio.config._
import zio.config.ConfigDescriptor
import zio.config._, typesafe._, magnolia._


final case class AppConfig(cacheService: CacheServiceConfig)

object AppConfig {
  val config: ConfigDescriptor[AppConfig] = descriptor[AppConfig].mapKey(toKebabCase)
}
