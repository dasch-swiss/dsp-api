package org.knora.webapi.testcontainers

import zio._
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

final case class RedisTestContainer(container: GenericContainer[Nothing])

object RedisTestContainer {

  /**
   * A functional effect that initiates a Redis Testcontainer
   */
  val aquireRedisTestContainer: Task[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
    val container                       = new GenericContainer(RedisImageName)
    container.withExposedPorts(6379)
    container.start()
    container
  }.orDie

  def releaseRedisTestContainer(container: GenericContainer[Nothing]): URIO[Any,Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie

  val layer: ZLayer[Scope, Nothing, RedisTestContainer] = {
    ZLayer {
      for {
        tc <- ZIO.acquireRelease(aquireRedisTestContainer)(releaseRedisTestContainer(_))
      } yield RedisTestContainer(tc)
    }.tap(_ => ZIO.debug(">>> Redis Test Container Initialized <<<"))
  }
}
