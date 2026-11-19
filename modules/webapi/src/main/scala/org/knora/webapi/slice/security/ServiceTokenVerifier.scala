/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import zio.*

import scala.util.Try
import scala.util.matching.Regex

import org.knora.jwt.EdDsaJwt
import org.knora.jwt.JwtClaim
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.common.domain.ServiceName

/** A trusted service, authenticated by a token signed with one of its configured keys. */
final case class ServicePrincipal(name: ServiceName, kid: String, jti: String)

/**
 * Why a service token was rejected. `reason` is a fixed category for the log; the client always gets the same 401.
 * `BadSignature` covers every token that fails as a JWS: a malformed part, `crit` present, bad encoding, an
 * unreadable payload or a bad signature. A header that does not parse, or an `alg` other than `EdDSA`, is caught here
 * too, but over HTTP such a token never reaches the verifier: the export policy routes it to the user path.
 */
enum ServiceTokenRejection(val reason: String) {
  case Disabled        extends ServiceTokenRejection("disabled")
  case UnknownKid      extends ServiceTokenRejection("unknown_kid")
  case BadSignature    extends ServiceTokenRejection("bad_signature")
  case BadAudience     extends ServiceTokenRejection("bad_audience")
  case Expired         extends ServiceTokenRejection("expired")
  case LifetimeTooLong extends ServiceTokenRejection("lifetime_too_long")
  case BadClaims       extends ServiceTokenRejection("bad_claims")
}

/**
 * Verifies a service token: an Ed25519-signed JWT that a trusted service mints for itself. The key is chosen by
 * `kid` from the configured keys only; `iss` and `sub` must both be that key's service name, `aud` this server's
 * external base URL, and the token must be at most 15 minutes long-lived and current within a 30 second leeway.
 */
final class ServiceTokenVerifier(keys: TrustedServiceKeys, knoraApi: KnoraApi) {
  import ServiceTokenRejection.*
  import ServiceTokenVerifier.*

  private val serverAudience: Option[String] = normaliseAudience(knoraApi.externalKnoraApiBaseUrl)

  /**
   * Every rejection logs one WARN line with its reason. The `kid` is logged only once it has matched a configured
   * key: before that it is unverified caller input. The token itself is never logged.
   */
  def verify(token: String): IO[ServiceTokenRejection, ServicePrincipal] =
    ZIO
      .fail(Disabled)
      .when(keys.isEmpty)
      .zipRight(findKey(token))
      .tapError(logRejection(_, None))
      .flatMap(key => verifyWith(token, key).tapError(logRejection(_, Some(key.kid))))

  private def logRejection(rejection: ServiceTokenRejection, kid: Option[String]): UIO[Unit] =
    ZIO.logWarning(s"Service token rejected: reason=${rejection.reason} kid=${kid.getOrElse("-")}")

  private def verifyWith(token: String, key: TrustedServiceKey): IO[ServiceTokenRejection, ServicePrincipal] =
    for {
      claim <- ZIO.fromTry(EdDsaJwt.verify(token, key.publicKey)).mapBoth(_ => BadSignature, _._2)
      times <- ZIO.fromEither(checkClaims(claim, key))
      _     <- ZIO.fail(BadAudience).unless(audienceMatches(claim))
      _     <- ZIO.fromEither(checkLifetime(times))
      now   <- Clock.instant.map(_.getEpochSecond)
      _     <- ZIO.fromEither(checkCurrent(times, now))
    } yield ServicePrincipal(key.name, key.kid, times.jti)

  private def findKey(token: String): IO[ServiceTokenRejection, TrustedServiceKey] =
    ZIO
      .fromTry(EdDsaJwt.peekHeader(token))
      .orElseFail(BadSignature)
      .flatMap(header => ZIO.fromOption(header.kid.flatMap(keys.get)).orElseFail(UnknownKid))

  private def audienceMatches(claim: JwtClaim): Boolean =
    serverAudience.exists(server => claim.audience.exists(_.exists(normaliseAudience(_).contains(server))))
}

object ServiceTokenVerifier {
  import ServiceTokenRejection.*

  val MaxLifetimeSeconds: Long = 15 * 60
  val LeewaySeconds: Long      = 30

  val layer: URLayer[TrustedServiceKeys & KnoraApi, ServiceTokenVerifier] = ZLayer.derive[ServiceTokenVerifier]

  private final case class TokenTimes(issuedAt: Long, expiration: Long, jti: String)

  private def checkClaims(claim: JwtClaim, key: TrustedServiceKey): Either[ServiceTokenRejection, TokenTimes] =
    for {
      _   <- Either.cond(claim.issuer.contains(key.name.value), (), BadClaims)
      _   <- Either.cond(claim.subject.contains(key.name.value), (), BadClaims)
      iat <- claim.issuedAt.toRight(BadClaims)
      exp <- claim.expiration.toRight(BadClaims)
      jti <- claim.jwtId.filterNot(_.isBlank).toRight(BadClaims)
    } yield TokenTimes(iat, exp, jti)

  // No leeway on the lifetime itself; subtractExact keeps an absurd iat or exp from overflowing into a short one.
  private def checkLifetime(times: TokenTimes): Either[ServiceTokenRejection, Unit] =
    Try(Math.subtractExact(times.expiration, times.issuedAt)).toOption match {
      case Some(lifetime) if lifetime < 0                  => Left(BadClaims)
      case Some(lifetime) if lifetime > MaxLifetimeSeconds => Left(LifetimeTooLong)
      case Some(_)                                         => Right(())
      case None                                            => Left(BadClaims)
    }

  private def checkCurrent(times: TokenTimes, now: Long): Either[ServiceTokenRejection, Unit] =
    if (now - LeewaySeconds >= times.expiration) Left(Expired)
    else if (times.issuedAt - LeewaySeconds > now) Left(BadClaims)
    else Right(())

  private val UrlPattern: Regex       = "^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#@]+)/?$".r
  private val AuthorityPattern: Regex = """^(\[[0-9A-Fa-f:.]+\]|[^:\[\]]+)(?::(\d{1,5}))?$""".r

  /**
   * Normalises a base URL for comparison: lower-case scheme and host, the scheme's default port and a trailing
   * slash dropped. Anything beyond scheme, host and port yields `None`, so it matches nothing. The authority is
   * parsed by hand because `URI.getHost` is null for host names containing `_`, such as Docker service names.
   */
  private[security] def normaliseAudience(url: String): Option[String] =
    url match {
      case UrlPattern(scheme, authority) =>
        authority match {
          case AuthorityPattern(host, port) =>
            val s           = scheme.toLowerCase
            val defaultPort = s match {
              case "https" => Some(443)
              case "http"  => Some(80)
              case _       => None
            }
            val explicitPort = Option(port).map(_.toInt).filterNot(p => defaultPort.contains(p))
            Some(s"$s://${host.toLowerCase}${explicitPort.fold("")(p => s":$p")}")
          case _ => None
        }
      case _ => None
    }
}
