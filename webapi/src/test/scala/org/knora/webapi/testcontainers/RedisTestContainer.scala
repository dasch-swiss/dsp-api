package org.knora.webapi.testcontainers

import zio._
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

final case class RedisTestContainer(port: Int)

object RedisTestContainer {
  val layer: ZLayer[Any, Nothing, RedisTestContainer] = {
    ZLayer {
      ZIO.attemptBlocking {
        val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
        val container                       = new GenericContainer(RedisImageName)
        container.withExposedPorts(6379)
        container.start()
        RedisTestContainer(port = container.getFirstMappedPort)
      }.orDie
    }.tap(_ => ZIO.debug(">>> Redis Test Container Initialized <<<"))
  }
}
