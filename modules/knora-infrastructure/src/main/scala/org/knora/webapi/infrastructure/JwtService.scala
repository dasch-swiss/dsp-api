/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.infrastructure

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtZIOJson
import zio.Clock
import zio.Duration
import zio.Random
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.durationInt
import zio.json.ast.Json

import scala.util.Failure
import scala.util.Success

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil

case class Jwt(jwtString: String, expiration: Long)

/**
 * Provides functions for creating, decoding, and validating JWT tokens.
 */
trait JwtService {

  /**
   * Creates a JWT.
   *
   * @param user the user IRI that will be encoded into the token.
   * @param content   any other content to be included in the token.
   * @return a [[String]] containing the JWT.
   */
  def createJwt(user: UserIri, scope: Scope, content: Map[String, Json] = Map.empty): UIO[Jwt]

  def createJwtForDspIngest(): UIO[Jwt]

  /**
   * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
   * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
   * present.
   *
   * @param token  the JWT.
   * @return a [[Boolean]].
   */
  def isTokenValid(token: String): Boolean

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[String]].
   */
  def extractUserIriFromToken(token: String): Task[Option[String]]
}

final case class JwtServiceLive(
  private val jwtConfig: JwtConfig,
  private val dspIngestConfig: DspIngestConfig,
  private val cache: InvalidTokenCache,
) extends JwtService {
  private val algorithm: JwtAlgorithm = JwtAlgorithm.HS256
  private val header: String          = """{"typ":"JWT","alg":"HS256"}"""
  private val logger                  = Logger(LoggerFactory.getLogger(this.getClass))
  private val audience                = Set("Knora", "Sipi", dspIngestConfig.audience)

  override def createJwt(userIri: UserIri, scope: Scope, content: Map[String, Json] = Map.empty): UIO[Jwt] =
    createJwtToken(jwtConfig.issuerAsString(), userIri.value, audience, scope, Some(Json.Obj(content.toSeq: _*)))

  override def createJwtForDspIngest(): UIO[Jwt] =
    createJwtToken(
      jwtConfig.issuerAsString(),
      jwtConfig.issuerAsString(),
      Set(dspIngestConfig.audience),
      Scope.admin,
      expiration = Some(10.minutes),
    )

  private def createJwtToken(
    issuer: String,
    subject: String,
    audience: Set[String],
    scope: Scope,
    content: Option[Json.Obj] = None,
    expiration: Option[Duration] = None,
  ) =
    for {
      now  <- Clock.instant
      uuid <- Random.nextUUID
      exp   = now.plus(expiration.getOrElse(jwtConfig.expiration))
      claim = JwtClaim(
                content = content.getOrElse(Json.Obj()).toString,
                issuer = Some(issuer),
                subject = Some(subject),
                audience = Some(audience),
                issuedAt = Some(now.getEpochSecond),
                expiration = Some(exp.getEpochSecond),
                jwtId = Some(UuidUtil.base64Encode(uuid)),
              ) + ("scope", scope.toScopeString)
    } yield Jwt(JwtZIOJson.encode(header, claim.toJson, jwtConfig.secret, algorithm), exp.getEpochSecond)

  /**
   * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
   * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
   * present.
   *
   * @param token  the JWT.
   * @return a [[Boolean]].
   */
  override def isTokenValid(token: String): Boolean = !cache.contains(token) && decodeToken(token).isDefined

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[String]].
   */
  override def extractUserIriFromToken(token: String): Task[Option[String]] =
    ZIO.attempt(decodeToken(token)).map(_.flatMap { case (_, claims) => claims.subject })

  /**
   * Decodes and validates a JWT token.
   *
   * @param token  the token to be decoded.
   * @return the token's header and claim, or `None` if the token is invalid.
   */
  private def decodeToken(token: String): Option[(JwtHeader, JwtClaim)] =
    JwtZIOJson.decodeAll(token, jwtConfig.secret, Seq(JwtAlgorithm.HS256)) match {
      case Success((header: JwtHeader, claim: JwtClaim, _)) =>
        val missingRequiredContent: Boolean = Set(
          header.typ.isDefined,
          claim.issuer == jwtConfig.issuer,
          claim.subject.isDefined,
          claim.jwtId.isDefined,
          claim.issuedAt.isDefined,
          claim.expiration.isDefined,
          claim.audience.isDefined,
        ).contains(false)

        if (!missingRequiredContent) {
          claim.subject.flatMap(iri => Iri.validateAndEscapeIri(iri).toOption.map(_ => (header, claim)))
        } else {
          logger.debug("Missing required content in JWT")
          None
        }

      case Failure(f) =>
        logger.debug(s"Invalid JWT: $f")
        None
    }
}

object JwtServiceLive {
  val layer = ZLayer.derive[JwtServiceLive]
}