package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

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
  }.orDie.tap(_ => ZIO.debug(">>> aquireRedisTestContainer executed <<<"))

  def releaseRedisTestContainer(container: GenericContainer[Nothing]): URIO[Any, Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.tap(_ => ZIO.debug(">>> releaseRedisTestContainer executed <<<"))

  val layer: ZLayer[Scope, Nothing, RedisTestContainer] = {
    ZLayer {
      for {
        tc <- ZIO.acquireRelease(aquireRedisTestContainer)(releaseRedisTestContainer(_)).orDie
      } yield RedisTestContainer(tc)
    }.tap(_ => ZIO.debug(">>> Redis Test Container Initialized <<<"))
  }
}
