/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.auth

import spray.json.JsValue
import zio._

import scala.concurrent.duration

import org.knora.webapi._
import org.knora.webapi.config._
import org.knora.webapi.routing.JWTHelper

final case class JWTService(secret: String, longevity: duration.Duration, issuer: String) {

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
        secret = secret,
        longevity = longevity,
        issuer = issuer,
        content = content
      )
    }
}

object JWTService {
  val layer: ZLayer[AppConfig, Nothing, JWTService] =
    ZLayer {
      for {
        config <- ZIO.service[AppConfig]
      } yield JWTService(config.jwtSecretKey, config.jwtLongevityAsDuration, config.knoraApi.externalKnoraApiHostPort)
    }
}
