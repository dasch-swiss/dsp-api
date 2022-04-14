package org.knora.webapi.testcontainers

import org.knora.webapi.http.version.BuildInfo
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

import java.net.NetworkInterface
import java.net.UnknownHostException
import scala.jdk.CollectionConverters._

final case class FusekiTestContainer(container: GenericContainer[Nothing])

object FusekiTestContainer {

  /**
   * A functional effect that initiates a Fuseki Testcontainer
   */
  val aquire: Task[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    // get local IP address, which we need for SIPI
    val localIpAddress: String = NetworkInterface.getNetworkInterfaces.asScala.toSeq
      .filter(!_.isLoopback)
      .flatMap(_.getInetAddresses.asScala.toSeq.filter(_.getAddress.length == 4).map(_.toString))
      .headOption
      .getOrElse(throw new UnknownHostException("No suitable network interface found"))

    val fusekiImageName: DockerImageName = DockerImageName.parse(BuildInfo.fuseki)
    val fusekiContainer                  = new GenericContainer(fusekiImageName)
    fusekiContainer.withExposedPorts(3030)
    fusekiContainer.withEnv("ADMIN_PASSWORD", "test")
    fusekiContainer.withEnv("JVM_ARGS", "-Xmx3G")
    fusekiContainer.start()
    fusekiContainer
  }.orDie.tap(_ => ZIO.debug(">>> Aquire Fuseki TestContainer executed <<<"))

  def release(container: GenericContainer[Nothing]): URIO[Any, Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.tap(_ => ZIO.debug(">>> Release Fuseki TestContainer executed <<<"))

  val layer: ZLayer[Any, Nothing, FusekiTestContainer] = {
    ZLayer.scoped {
      for {
        tc <- ZIO.acquireRelease(aquire)(release(_)).orDie
      } yield FusekiTestContainer(tc)
    }.tap(_ => ZIO.debug(">>> Fuseki Test Container Initialized <<<"))
  }
}
