/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import zio.Scope
import zio.test.*

object JwtCodecSpec extends ZIOSpecDefault {

  private val secret = "test-secret-key-for-hmac-sha256"

  val spec: Spec[TestEnvironment & Scope, Any] = suite("JwtCodec")(
    suite("encode and decode roundtrip")(
      test("roundtrips a simple claim") {
        val claim = JwtClaim(
          issuer = Some("test-issuer"),
          subject = Some("user-123"),
          audience = Some(Set("aud1", "aud2")),
          issuedAt = Some(1000000L),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
          jwtId = Some("jwt-id-1"),
        )
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret)
        assertTrue(
          decoded.isSuccess,
          decoded.get.issuer.contains("test-issuer"),
          decoded.get.subject.contains("user-123"),
          decoded.get.audience.contains(Set("aud1", "aud2")),
          decoded.get.issuedAt.contains(1000000L),
          decoded.get.jwtId.contains("jwt-id-1"),
        )
      },
      test("roundtrips with custom content") {
        val claim = JwtClaim(
          content = """{"scope":"admin","foo":"bar"}""",
          issuer = Some("iss"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(
          decoded.content.contains("\"scope\":\"admin\""),
          decoded.content.contains("\"foo\":\"bar\""),
          decoded.issuer.contains("iss"),
        )
      },
      test("roundtrips a claim with single audience") {
        val claim = JwtClaim(
          audience = Some(Set("single-aud")),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(decoded.audience.contains(Set("single-aud")))
      },
      test("roundtrips with the + operator for adding claims") {
        val claim = JwtClaim(
          issuer = Some("iss"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        ) + ("scope", "admin")
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(decoded.content.contains("\"scope\":\"admin\""))
      },
    ),
    suite("encode with custom header")(
      test("uses a custom header string") {
        val header = """{"typ":"JWT","alg":"HS256"}"""
        val claim  = JwtClaim(subject = Some("sub"), expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token  = JwtCodec.encode(header, claim.toJson, secret)
        val result = JwtCodec.decodeAll(token, secret)
        assertTrue(
          result.isSuccess,
          result.get._1.typ.contains("JWT"),
          result.get._1.alg.contains("HS256"),
          result.get._2.subject.contains("sub"),
        )
      },
    ),
    suite("decodeAll")(
      test("returns header, claim, and signature") {
        val claim = JwtClaim(
          issuer = Some("iss"),
          subject = Some("sub"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token  = JwtCodec.encode(claim, secret)
        val result = JwtCodec.decodeAll(token, secret)
        assertTrue(
          result.isSuccess,
          result.get._1.typ.contains("JWT"),
          result.get._1.alg.contains("HS256"),
          result.get._2.issuer.contains("iss"),
          result.get._2.subject.contains("sub"),
          result.get._3.nonEmpty,
        )
      },
    ),
    suite("validation")(
      test("fails with wrong secret") {
        val claim = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token = JwtCodec.encode(claim, secret)
        assertTrue(JwtCodec.decode(token, "wrong-secret").isFailure)
      },
      test("fails with expired token") {
        val claim = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond - 10))
        val token = JwtCodec.encode(claim, secret)
        assertTrue(JwtCodec.decode(token, secret).isFailure)
      },
      test("fails with malformed token") {
        assertTrue(JwtCodec.decode("not.a.valid.jwt", secret).isFailure)
      },
      test("fails with tampered payload") {
        val claim = JwtClaim(
          subject = Some("original"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token = JwtCodec.encode(claim, secret)
        val parts = token.split("\\.")
        // Tamper with the payload by replacing it
        val tampered =
          s"${parts(0)}.${java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("{\"sub\":\"tampered\"}".getBytes)}.${parts(2)}"
        assertTrue(JwtCodec.decode(tampered, secret).isFailure)
      },
    ),
    suite("JwtClaim")(
      test("toJson and fromJsonString roundtrip") {
        val claim = JwtClaim(
          content = """{"scope":"read"}""",
          issuer = Some("iss"),
          subject = Some("sub"),
          audience = Some(Set("aud")),
          expiration = Some(123L),
          issuedAt = Some(100L),
          jwtId = Some("jti"),
        )
        val json   = claim.toJson
        val parsed = JwtClaim.fromJsonString(json)
        assertTrue(
          parsed.isRight,
          parsed.toOption.get.issuer.contains("iss"),
          parsed.toOption.get.subject.contains("sub"),
          parsed.toOption.get.audience.contains(Set("aud")),
          parsed.toOption.get.expiration.contains(123L),
          parsed.toOption.get.issuedAt.contains(100L),
          parsed.toOption.get.jwtId.contains("jti"),
          parsed.toOption.get.content.contains("\"scope\":\"read\""),
        )
      },
    ),
  )
}
