package org.knora.webapi.auth

import zio._
import org.knora.webapi.config._
import org.knora.webapi._
import spray.json.JsValue
import org.knora.webapi.routing.JWTHelper
import spray.json.JsObject
import spray.json.JsString

final case class JWTService(config: JWTConfig) {

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
        secret = config.secret,
        longevity = config.longevity,
        issuer = config.issuer,
        content = content
      )
    }
}

object JWTService {
  val layer: ZLayer[JWTConfig, Nothing, JWTService] = {
    ZLayer {
      for {
        config <- ZIO.service[JWTConfig]
      } yield JWTService(config)
    }
  }
}
