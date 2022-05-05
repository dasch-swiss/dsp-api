package org.knora.webapi.store.cacheservice.config

import org.knora.webapi.store.cacheservice.config.RedisConfig
import org.knora.webapi.testcontainers.RedisTestContainer
import zio._

object RedisTestConfig {
  val hardcoded: ULayer[RedisConfig] = ZLayer.succeed(RedisConfig("localhost", 6379))
  val redisTestContainer: ZLayer[RedisTestContainer, Nothing, RedisConfig] = {
    ZLayer {
      for {
        rtc <- ZIO.service[RedisTestContainer]
      } yield RedisConfig("localhost", rtc.container.getFirstMappedPort())
    }.tap(_ => ZIO.debug(">>> Redis Test Config Initialized <<<"))
  }
}
