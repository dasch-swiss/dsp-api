/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import zio.Scope
import zio.test.*

import java.nio.charset.StandardCharsets

object JwtCodecSpec extends ZIOSpecDefault {

  private val secret = "test-secret-key-for-hmac-sha256".getBytes(StandardCharsets.UTF_8)

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
        val claim       = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token       = JwtCodec.encode(claim, secret)
        val wrongSecret = "wrong-secret".getBytes(StandardCharsets.UTF_8)
        assertTrue(JwtCodec.decode(token, wrongSecret).isFailure)
      },
      test("fails with expired token") {
        val claim = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond - 10))
        val token = JwtCodec.encode(claim, secret)
        assertTrue(JwtCodec.decode(token, secret).isFailure)
      },
      test("fails with missing expiration") {
        val claim = JwtClaim(subject = Some("sub"))
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
      test("fails with missing algorithm in header") {
        val headerNoAlg = """{"typ":"JWT"}"""
        val claim       = JwtClaim(subject = Some("sub"), expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token       = JwtCodec.encode(headerNoAlg, claim.toJson, secret)
        assertTrue(JwtCodec.decode(token, secret).isFailure)
      },
      test("fails with unsupported algorithm in header") {
        val headerRS256 = """{"typ":"JWT","alg":"RS256"}"""
        val claim       = JwtClaim(subject = Some("sub"), expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token       = JwtCodec.encode(headerRS256, claim.toJson, secret)
        assertTrue(JwtCodec.decode(token, secret).isFailure)
      },
    ),
    suite("issuer and audience validation")(
      test("validates expected issuer") {
        val claim = JwtClaim(
          issuer = Some("correct-issuer"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token = JwtCodec.encode(claim, secret)
        assertTrue(
          JwtCodec.decodeAll(token, secret, expectedIssuer = Some("correct-issuer")).isSuccess,
          JwtCodec.decodeAll(token, secret, expectedIssuer = Some("wrong-issuer")).isFailure,
        )
      },
      test("validates expected audience") {
        val claim = JwtClaim(
          audience = Some(Set("aud1", "aud2")),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token = JwtCodec.encode(claim, secret)
        assertTrue(
          JwtCodec.decodeAll(token, secret, expectedAudience = Some(Set("aud1"))).isSuccess,
          JwtCodec.decodeAll(token, secret, expectedAudience = Some(Set("aud1", "aud2"))).isSuccess,
          JwtCodec.decodeAll(token, secret, expectedAudience = Some(Set("aud3"))).isFailure,
        )
      },
      test("skips issuer/audience validation when not specified") {
        val claim = JwtClaim(
          issuer = Some("any-issuer"),
          audience = Some(Set("any-aud")),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token = JwtCodec.encode(claim, secret)
        assertTrue(JwtCodec.decodeAll(token, secret).isSuccess)
      },
    ),
    suite("security edge cases")(
      test("rejects alg:none attack") {
        val headerNone = """{"typ":"JWT","alg":"none"}"""
        val claim      = JwtClaim(subject = Some("sub"), expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token      = JwtCodec.encode(headerNone, claim.toJson, secret)
        assertTrue(JwtCodec.decode(token, secret).isFailure)
      },
      test("rejects tampered header") {
        val claim = JwtClaim(
          subject = Some("sub"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token = JwtCodec.encode(claim, secret)
        val parts = token.split("\\.")
        // Replace header with a different one (same alg but different typ)
        val tamperedHeader =
          java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"typ":"XXX","alg":"HS256"}""".getBytes)
        val tampered = s"$tamperedHeader.${parts(1)}.${parts(2)}"
        assertTrue(JwtCodec.decode(tampered, secret).isFailure)
      },
      test("rejects empty secret") {
        val emptySecret = Array.emptyByteArray
        val claim       = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val result      = scala.util.Try(JwtCodec.encode(claim, emptySecret))
        assertTrue(result.isFailure)
      },
    ),
    suite("malformed input boundaries")(
      test("fails with two-part token") {
        assertTrue(JwtCodec.decode("header.payload", secret).isFailure)
      },
      test("fails with empty segments") {
        assertTrue(JwtCodec.decode("..", secret).isFailure)
      },
      test("fails with empty string") {
        assertTrue(JwtCodec.decode("", secret).isFailure)
      },
      test("fails with single segment") {
        assertTrue(JwtCodec.decode("onlyone", secret).isFailure)
      },
    ),
    suite("JwtClaim edge cases")(
      test("content with standard claim key collision preserves standard claim") {
        val claim = JwtClaim(
          content = """{"iss":"evil","custom":"value"}""",
          issuer = Some("real"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(
          decoded.issuer.contains("real"),
          decoded.content.contains("\"custom\":\"value\""),
        )
      },
      test("roundtrips unicode in claims") {
        val claim = JwtClaim(
          subject = Some("用户-αβγ-🔑"),
          issuer = Some("发行者"),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(
          decoded.subject.contains("用户-αβγ-🔑"),
          decoded.issuer.contains("发行者"),
        )
      },
      test("roundtrips empty audience set") {
        val claim = JwtClaim(
          audience = Some(Set.empty),
          expiration = Some(java.time.Instant.now.getEpochSecond + 3600),
        )
        val json   = claim.toJson
        val parsed = JwtClaim.fromJsonString(json)
        assertTrue(
          parsed.isRight,
          parsed.toOption.get.audience.contains(Set.empty[String]),
        )
      },
      test("fromJsonString rejects non-object JSON") {
        assertTrue(
          JwtClaim.fromJsonString("""[1,2,3]""").isLeft,
          JwtClaim.fromJsonString(""""hello"""").isLeft,
          JwtClaim.fromJsonString("42").isLeft,
        )
      },
      test("roundtrips minimal claim with only expiration") {
        val claim   = JwtClaim(expiration = Some(java.time.Instant.now.getEpochSecond + 3600))
        val token   = JwtCodec.encode(claim, secret)
        val decoded = JwtCodec.decode(token, secret).get
        assertTrue(
          decoded.issuer.isEmpty,
          decoded.subject.isEmpty,
          decoded.audience.isEmpty,
          decoded.issuedAt.isEmpty,
          decoded.jwtId.isEmpty,
          decoded.content == "{}",
        )
      },
    ),
    suite("backward compatibility with jwt-scala")(
      test("decodes a token produced by jwt-scala (jwt-zio-json 11.0.3)") {
        // Token generated with secret "UP 4888, nice 4-8-4 steam engine" using the same payload
        // shape as jwt-scala's JwtZIOJson.encode produces (field order, compact JSON, HS256).
        // Claims: iss=0.0.0.0:3333, sub=http://rdfh.ch/users/8bkQjIL3Tc-G1TWyAQZxrw,
        //         aud=[Knora, Sipi, http://localhost:3340], exp=253402300799 (9999-12-31T23:59:59Z),
        //         iat=1773755770, jti=wfc0lbBxRz2fAf0wpm1iXA, scope=admin
        val jwtScalaToken =
          "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiIwLjAuMC4wOjMzMzMiLCJzdWIiOiJodHRwOi8vcmRmaC5jaC91c2Vycy84YmtRaklMM1RjLUcxVFd5QVFaeHJ3IiwiYXVkIjpbIktub3JhIiwiU2lwaSIsImh0dHA6Ly9sb2NhbGhvc3Q6MzM0MCJdLCJleHAiOjI1MzQwMjMwMDc5OSwiaWF0IjoxNzczNzU1NzcwLCJqdGkiOiJ3ZmMwbGJCeFJ6MmZBZjB3cG0xaVhBIiwic2NvcGUiOiJhZG1pbiJ9.6Rvq0zdBW3y9gYRgs5zY4oRSzHiK_yFUV1jEwkGZOyo"
        val jwtScalaSecret = "UP 4888, nice 4-8-4 steam engine".getBytes(StandardCharsets.UTF_8)
        val decoded        = JwtCodec.decodeAll(jwtScalaToken, jwtScalaSecret)
        assertTrue(
          decoded.isSuccess,
          decoded.get._1.typ.contains("JWT"),
          decoded.get._1.alg.contains("HS256"),
          decoded.get._2.issuer.contains("0.0.0.0:3333"),
          decoded.get._2.subject.contains("http://rdfh.ch/users/8bkQjIL3Tc-G1TWyAQZxrw"),
          decoded.get._2.audience.contains(Set("Knora", "Sipi", "http://localhost:3340")),
          decoded.get._2.expiration.contains(253402300799L),
          decoded.get._2.issuedAt.contains(1773755770L),
          decoded.get._2.jwtId.contains("wfc0lbBxRz2fAf0wpm1iXA"),
          decoded.get._2.content.contains("\"scope\":\"admin\""),
        )
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
