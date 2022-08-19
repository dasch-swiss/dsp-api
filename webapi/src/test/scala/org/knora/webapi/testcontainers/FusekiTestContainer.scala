package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

import org.knora.webapi.http.version.BuildInfo

final case class FusekiTestContainer(container: GenericContainer[Nothing])

object FusekiTestContainer {

  /**
   * A functional effect that initiates a Fuseki Testcontainer
   */
  val acquire: Task[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    val fusekiImageName: DockerImageName = DockerImageName.parse(BuildInfo.fuseki)
    val fusekiContainer                  = new GenericContainer(fusekiImageName)
    fusekiContainer.withExposedPorts(3030)
    fusekiContainer.withEnv("ADMIN_PASSWORD", "test")
    fusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
    fusekiContainer.start()
    fusekiContainer
  }.orDie.tap(_ => ZIO.debug(">>> Acquire Fuseki TestContainer <<<"))

  def release(container: GenericContainer[Nothing]): UIO[Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.tap(_ => ZIO.debug(">>> Release Fuseki TestContainer <<<"))

  val layer: ZLayer[Any, Nothing, FusekiTestContainer] =
    ZLayer.scoped {
      for {
        tc <- ZIO.acquireRelease(acquire)(release(_)).orDie
      } yield FusekiTestContainer(tc)
    }
}
