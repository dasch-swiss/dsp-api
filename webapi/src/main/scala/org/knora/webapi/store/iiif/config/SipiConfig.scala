package org.knora.webapi.store.iiif.config

import scala.concurrent.duration.FiniteDuration
import zio._
import com.typesafe.config.ConfigFactory
import java.util.concurrent.TimeUnit
import com.typesafe.config.Config

final case class SipiConfig(internal: Internal, external: External, timeout: FiniteDuration, v2: V2)

final case class Internal(host: String, port: Int, protocol: String) {
  def baseUrl: String = protocol + "://" + host + (if (port != 80)
                                                     ":" + port
                                                   else "")
}
final case class External(host: String, port: Int, protocol: String) {
  def baseUrl: String = protocol + "://" + host + (if (port != 80)
                                                     ":" + port
                                                   else "")
}

final case class V2(fileMetadataRoute: String, moveFileRoute: String, deleteTempFileRoute: String)

object IIIFServiceConfig {

  /**
   * Hardcoded configuration to "localhost", 1024 and "http"
   */
  val hardcoded = ZLayer.succeed(
    SipiConfig(
      Internal("localhost", 1024, "http"),
      External("localhost", 1024, "http"),
      FiniteDuration(120, TimeUnit.SECONDS),
      V2(fileMetadataRoute = "knora.json", moveFileRoute = "store", deleteTempFileRoute = "delete_temp_file")
    )
  )

  /**
   * Live configuration reading from application.conf.
   */
  val live: ZLayer[Any, Nothing, SipiConfig] =
    ZLayer {
      for {
        config   <- ZIO.attempt(ConfigFactory.load()).orDie
        host     <- ZIO.succeed(config.getString("app.sipi.internal-host"))
        port     <- ZIO.succeed(config.getInt("app.sipi.internal-port"))
        protocol <- ZIO.succeed(config.getString("app.sipi.internal-protocol"))
        timeout  <- ZIO.succeed(FiniteDuration(config.getString("app.sipi.timeout").toLong, TimeUnit.SECONDS))
      } yield SipiConfig(
        Internal(host, port, protocol),
        External(host, port, protocol),
        timeout,
        V2(fileMetadataRoute = "knora.json", moveFileRoute = "store", deleteTempFileRoute = "delete_temp_file")
      )
    }
}
