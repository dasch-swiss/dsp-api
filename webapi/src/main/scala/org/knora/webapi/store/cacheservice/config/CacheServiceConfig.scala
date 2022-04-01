package org.knora.webapi.store.cacheservice.config

import zio._

final case class CacheServiceConfig(enabled: Boolean, redis: RedisConfig)

final case class RedisConfig(server: String, port1: Int, port2: Int)
object RedisConfig {
  val hardcoded = ZLayer.succeed(RedisConfig("localhost", 6379, 20999))
}
