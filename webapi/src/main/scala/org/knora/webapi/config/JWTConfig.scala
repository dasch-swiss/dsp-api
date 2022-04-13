package org.knora.webapi.config

import zio._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

final case class JWTConfig(secret: String, longevity: FiniteDuration, issuer: String)

object JWTConfig {

  /**
   * Hardcoded config from the application.conf default values.
   */
  val hardcoded = ZLayer.succeed(
    JWTConfig(
      "UP 4888, nice 4-8-4 steam engine",
      FiniteDuration(30.toLong, TimeUnit.DAYS),
      "http://0.0.0.0:3333"
    )
  )

  /**
   * Live configuration reading from application.conf.
   */
  val live: ZLayer[Any, Nothing, JWTConfig] =
    ZLayer {
      for {
        config    <- ZIO.attempt(ConfigFactory.load()).orDie
        secret    <- ZIO.succeed(config.getString("app.jwt-secret-key"))
        longevity <- ZIO.succeed(FiniteDuration(config.getString("app.jwt-longevity").toLong, TimeUnit.SECONDS))
        issuer    <- ZIO.succeed("we")
      } yield JWTConfig(secret, longevity, issuer)
    }
}
