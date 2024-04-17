/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtZIOJson
import spray.json.JsObject
import spray.json.JsValue
import zio.Clock
import zio.Duration
import zio.Random
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.durationInt

import scala.util.Failure
import scala.util.Success

import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectAdminAll
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateAll
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateRestricted
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.infrastructure.Scope
import org.knora.webapi.slice.infrastructure.ScopeValue
import org.knora.webapi.slice.infrastructure.ScopeValue.Write
import org.knora.webapi.util.cache.CacheUtil

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
  def createJwt(user: User, content: Map[String, JsValue] = Map.empty): UIO[Jwt]

  def createJwtForDspIngest(): UIO[Jwt]

  /**
   * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
   * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
   * present.
   *
   * @param token  the JWT.
   * @return a [[Boolean]].
   */
  def validateToken(token: String): Task[Boolean]

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[IRI]].
   */
  def extractUserIriFromToken(token: String): Task[Option[IRI]]
}

final case class JwtServiceLive(
  private val jwtConfig: JwtConfig,
  private val dspIngestConfig: DspIngestConfig,
  private val knoraProjectService: KnoraProjectService,
) extends JwtService {
  private val algorithm: JwtAlgorithm = JwtAlgorithm.HS256
  private val header: String          = """{"typ":"JWT","alg":"HS256"}"""
  private val logger                  = Logger(LoggerFactory.getLogger(this.getClass))

  override def createJwt(user: User, content: Map[String, JsValue] = Map.empty): UIO[Jwt] = {
    val audience = if (user.isSystemAdmin) { Set("Knora", "Sipi", dspIngestConfig.audience) }
    else { Set("Knora", "Sipi") }
    calculateScope(user)
      .flatMap(scope => createJwtToken(jwtConfig.issuerAsString(), user.id, audience, scope, Some(JsObject(content))))
  }

  private def calculateScope(user: User) =
    if (user.isSystemAdmin || user.isSystemUser) { ZIO.succeed(Scope.admin) }
    else { mapUserPermissionsToScope(user) }

  private def mapUserPermissionsToScope(user: User): UIO[Scope] =
    ZIO
      .foreach(user.permissions.administrativePermissionsPerProject.toSeq) { case (prjIri, permission) =>
        knoraProjectService
          .findById(ProjectIri.unsafeFrom(prjIri))
          .orDie
          .map(_.map(prj => mapPermissionToScope(permission, prj.shortcode)).getOrElse(Set.empty))
      }
      .map(scopeValues => Scope.from(scopeValues.flatten))

  private def mapPermissionToScope(permission: Set[PermissionADM], shortcode: Shortcode): Set[ScopeValue] =
    permission
      .map(_.name)
      .flatMap(Administrative.fromToken)
      .flatMap {
        case ProjectResourceCreateAll | ProjectResourceCreateRestricted | ProjectAdminAll => Some(Write(shortcode))
        case _                                                                            => None
      }

  override def createJwtForDspIngest(): UIO[Jwt] =
    createJwtToken(
      jwtConfig.issuerAsString(),
      jwtConfig.issuerAsString(),
      Set(dspIngestConfig.audience),
      Scope.admin,
      expiration = Some(10.minutes),
    )

  private def createJwtToken(
    issuer: IRI,
    subject: IRI,
    audience: Set[IRI],
    scope: Scope,
    content: Option[JsObject] = None,
    expiration: Option[Duration] = None,
  ) =
    for {
      now  <- Clock.instant
      uuid <- Random.nextUUID
      exp   = now.plus(expiration.getOrElse(jwtConfig.expiration))
      claim = JwtClaim(
                content = content.getOrElse(JsObject.empty).compactPrint,
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
  override def validateToken(token: String): Task[Boolean] =
    ZIO.attempt(if (CacheUtil.get[User](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
      // token invalidated so no need to decode
      logger.debug("validateToken - token found in invalidation cache, so not valid")
      false
    } else {
      decodeToken(token).isDefined
    })

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[IRI]].
   */
  override def extractUserIriFromToken(token: String): Task[Option[IRI]] =
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