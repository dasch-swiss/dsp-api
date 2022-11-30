package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

final case class RedisTestContainer(container: GenericContainer[Nothing])

object RedisTestContainer {

  /**
   * A functional effect that initiates a Redis Testcontainer
   */
  val acquireRedisTestContainer: Task[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    val RedisImageName: DockerImageName = DockerImageName.parse("redis:5")
    val container                       = new GenericContainer(RedisImageName)
    container.withExposedPorts(6379)
    container.start()
    container
  }.orDie.tap(_ => ZIO.debug(">>> Acquire Redis TestContainer <<<"))

  def releaseRedisTestContainer(container: GenericContainer[Nothing]): URIO[Any, Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.tap(_ => ZIO.debug(">>> Release Redis TestContainer <<<"))

  val layer: ZLayer[Any, Nothing, RedisTestContainer] =
    ZLayer.scoped {
      for {
        tc <- ZIO.acquireRelease(acquireRedisTestContainer)(releaseRedisTestContainer(_)).orDie
      } yield RedisTestContainer(tc)
    }
}
