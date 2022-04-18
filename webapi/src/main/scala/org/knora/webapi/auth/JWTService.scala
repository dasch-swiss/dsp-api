package org.knora.webapi.auth

import zio._
import org.knora.webapi.config._
import org.knora.webapi._
import spray.json.JsValue
import org.knora.webapi.routing.JWTHelper
import spray.json.JsObject
import spray.json.JsString
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

final case class JWTService(config: AppConfig) {

  /**
   * Creates a new JWT token for a specific user and holds some additional
   * content.
   *
   * @param id the user's IRI.
   * @param content containing additional information.
   */
  def newToken(id: IRI, content: Map[String, JsValue]): UIO[String] =
    ZIO.succeed {
      JWTHelper.createToken(
        userIri = id,
        secret = config.jwtSecretKey,
        longevity = config.jwtLongevityAsDuration,
        issuer = config.knoraApi.externalKnoraApiHostPort,
        content = content
      )
    }
}

object JWTService {
  val layer: ZLayer[AppConfig, Nothing, JWTService] = {
    ZLayer {
      for {
        config <- ZIO.service[AppConfig]
      } yield JWTService(config)
    }
  }
}
