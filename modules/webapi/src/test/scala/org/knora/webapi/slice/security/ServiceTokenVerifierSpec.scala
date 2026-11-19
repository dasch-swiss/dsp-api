/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.security.PrivateKey
import java.time.Instant

import org.knora.jwt.Ed25519TestSigner
import org.knora.jwt.JwtClaim
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.security.ServiceTokenRejection.*

@RunWith(classOf[DspZTestJUnitRunner])
class ServiceTokenVerifierSpec extends ZIOSpecDefault {

  private val keyPairA = Ed25519TestSigner.generateKeyPair()
  private val keyPairB = Ed25519TestSigner.generateKeyPair()

  private val keys = TrustedServiceKeys
    .parse(
      s"""[{"name":"svc-a","kid":"a-1","publicKey":"${Ed25519TestSigner.spkiBase64(keyPairA.getPublic)}"},
         | {"name":"svc-b","kid":"b-1","publicKey":"${Ed25519TestSigner.spkiBase64(keyPairB.getPublic)}"}]""".stripMargin,
    )
    .fold(e => throw new IllegalStateException(e), identity)

  private val now      = 1_800_000_000L
  private val audience = "https://api.example.org"

  private def server(protocol: String = "https", host: String = "api.example.org", port: Int = 443) =
    KnoraApi("0.0.0.0", 3333, protocol, host, port, 3333)

  private def claim(
    iss: Option[String] = Some("svc-a"),
    sub: Option[String] = Some("svc-a"),
    aud: Option[Set[String]] = Some(Set(audience)),
    iat: Option[Long] = Some(now),
    exp: Option[Long] = Some(now + 300),
    jti: Option[String] = Some("jti-1"),
  ) = JwtClaim(issuer = iss, subject = sub, audience = aud, issuedAt = iat, expiration = exp, jwtId = jti)

  private def token(c: JwtClaim = claim(), kid: String = "a-1", key: PrivateKey = keyPairA.getPrivate) =
    Ed25519TestSigner.sign(c, kid, key)

  private def verify(
    token: String,
    keys: TrustedServiceKeys = keys,
    knoraApi: KnoraApi = server(),
  ): IO[ServiceTokenRejection, ServicePrincipal] =
    TestClock.setTime(Instant.ofEpochSecond(now)) *> ServiceTokenVerifier(keys, knoraApi).verify(token)

  private def accepted(token: String, knoraApi: KnoraApi = server()) =
    verify(token, knoraApi = knoraApi).map(p => assertTrue(p.name.value == "svc-a"))

  private def rejectedAs(expected: ServiceTokenRejection, token: String, knoraApi: KnoraApi = server()) =
    verify(token, knoraApi = knoraApi).flip.map(rejection => assertTrue(rejection == expected))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ServiceTokenVerifier")(
    test("accepts a valid token and returns the key's service name, kid and the jti") {
      verify(token()).map(p => assertTrue(p.name.value == "svc-a", p.kid == "a-1", p.jti == "jti-1"))
    },
    test("with no keys configured, rejects every token as disabled") {
      verify(token(), keys = TrustedServiceKeys.empty).flip.map(r => assertTrue(r == Disabled))
    },
    suite("rejection reasons")(
      test("unknown_kid for a kid that is not configured") {
        rejectedAs(UnknownKid, token(kid = "c-1"))
      },
      test("unknown_kid for a token without a kid") {
        val header = Ed25519TestSigner.headerJson()
        rejectedAs(UnknownKid, Ed25519TestSigner.signRaw(header, claim().toJson, keyPairA.getPrivate))
      },
      test("bad_signature for a token signed by another key") {
        rejectedAs(BadSignature, token(key = keyPairB.getPrivate))
      },
      test("bad_signature for a token that is not a JWS") {
        rejectedAs(BadSignature, "not-a-token")
      },
      test("bad_audience for another server's URL") {
        rejectedAs(BadAudience, token(claim(aud = Some(Set("https://api.other.org")))))
      },
      test("bad_audience for a missing aud") {
        rejectedAs(BadAudience, token(claim(aud = None)))
      },
      test("bad_audience for an aud with a path") {
        rejectedAs(BadAudience, token(claim(aud = Some(Set(s"$audience/v3")))))
      },
      test("expired once now reaches exp plus the 30 s leeway") {
        rejectedAs(Expired, token(claim(iat = Some(now - 330), exp = Some(now - 30))))
      },
      test("lifetime_too_long for exp - iat of 15 min 1 s") {
        rejectedAs(LifetimeTooLong, token(claim(exp = Some(now + 901))))
      },
      test("bad_claims for a missing jti, exp or iat") {
        for {
          noJti <- verify(token(claim(jti = None))).flip
          noExp <- verify(token(claim(exp = None))).flip
          noIat <- verify(token(claim(iat = None))).flip
        } yield assertTrue(noJti == BadClaims, noExp == BadClaims, noIat == BadClaims)
      },
      test("bad_claims for an iss or sub that is not the key's name") {
        for {
          iss <- verify(token(claim(iss = Some("svc-x")))).flip
          sub <- verify(token(claim(sub = None))).flip
        } yield assertTrue(iss == BadClaims, sub == BadClaims)
      },
      test("bad_claims for a token from key A claiming key B's name") {
        rejectedAs(BadClaims, token(claim(iss = Some("svc-b"), sub = Some("svc-b"))))
      },
      test("bad_claims for an iat beyond the leeway in the future") {
        rejectedAs(BadClaims, token(claim(iat = Some(now + 31), exp = Some(now + 331))))
      },
      test("bad_claims for a blank jti") {
        rejectedAs(BadClaims, token(claim(jti = Some(" "))))
      },
      test("bad_claims, not a short lifetime, when exp - iat overflows") {
        rejectedAs(BadClaims, token(claim(iat = Some(Long.MinValue), exp = Some(now + 300))))
      },
      test("bad_claims for an exp before the iat") {
        rejectedAs(BadClaims, token(claim(iat = Some(now), exp = Some(now - 1))))
      },
    ),
    suite("times")(
      test("accepts exp - iat of exactly 15 min") {
        accepted(token(claim(exp = Some(now + 900))))
      },
      test("accepts a token up to 30 s after its exp") {
        accepted(token(claim(iat = Some(now - 329), exp = Some(now - 29))))
      },
      test("accepts an iat up to 30 s in the future") {
        accepted(token(claim(iat = Some(now + 30), exp = Some(now + 330))))
      },
    ),
    suite("audience")(
      test("accepts aud as a string and as an array with one matching element") {
        for {
          asString <- accepted(token(claim(aud = Some(Set(audience)))))
          asArray  <- accepted(token(claim(aud = Some(Set("https://elsewhere.org", audience)))))
        } yield asString && asArray
      },
      test("aud https://h matches a server configured as https, h, port 443") {
        accepted(token(claim(aud = Some(Set("https://h")))), server(host = "h"))
      },
      test("aud http://localhost:3333 matches a server on that port") {
        accepted(
          token(claim(aud = Some(Set("http://localhost:3333")))),
          server(protocol = "http", host = "localhost", port = 3333),
        )
      },
      test("an uppercase host and a trailing slash match") {
        accepted(token(claim(aud = Some(Set("HTTPS://API.Example.ORG/")))))
      },
      test("an explicit default port matches, another port does not") {
        for {
          defaultPort <- accepted(token(claim(aud = Some(Set("https://api.example.org:443")))))
          otherPort   <- rejectedAs(BadAudience, token(claim(aud = Some(Set("https://api.example.org:8443")))))
        } yield defaultPort && otherPort
      },
      test("a host name with an underscore matches") {
        accepted(token(claim(aud = Some(Set("http://dsp_api:3333")))), server("http", "dsp_api", 3333))
      },
    ),
    suite("rejection log")(
      test("a rejected token with a configured kid logs one WARN line with the reason and the kid, not the token") {
        val rejected = token(claim(aud = Some(Set("https://api.other.org"))))
        for {
          _     <- verify(rejected).flip
          lines <- rejectionLines
        } yield assertTrue(
          lines.map(_.logLevel) == Chunk(LogLevel.Warning),
          lines.map(_.message()) == Chunk("Service token rejected: reason=bad_audience kid=a-1"),
        )
      },
      test("an unconfigured kid is not logged") {
        for {
          _     <- verify(token(kid = "planted-kid")).flip
          lines <- rejectionLines
        } yield assertTrue(lines.map(_.message()) == Chunk("Service token rejected: reason=unknown_kid kid=-"))
      },
      test("with no keys configured the reason is disabled") {
        for {
          _     <- verify(token(), keys = TrustedServiceKeys.empty).flip
          lines <- rejectionLines
        } yield assertTrue(lines.map(_.message()) == Chunk("Service token rejected: reason=disabled kid=-"))
      },
      test("an accepted token logs no rejection") {
        verify(token()) *> rejectionLines.map(lines => assertTrue(lines.isEmpty))
      },
    ),
  )

  private val rejectionLines: UIO[Chunk[ZTestLogger.LogEntry]] =
    ZTestLogger.logOutput.map(_.filter(_.message().startsWith("Service token rejected:")))
}
