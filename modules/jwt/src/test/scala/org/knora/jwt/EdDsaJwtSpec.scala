/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import org.junit.runner.RunWith
import zio.Scope
import zio.json.ast.Json
import zio.test.*

import java.security.KeyPairGenerator
import java.util.Base64

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class EdDsaJwtSpec extends ZIOSpecDefault {

  import Ed25519TestSigner.*

  private val keyPair      = generateKeyPair()
  private val otherKeyPair = generateKeyPair()
  private val kid          = "svc-2026-11"

  private def claim(jti: String = "jti-1") = JwtClaim(
    issuer = Some("svc"),
    subject = Some("svc"),
    audience = Some(Set("https://api.example.org")),
    issuedAt = Some(1000L),
    expiration = Some(1300L),
    jwtId = Some(jti),
  )

  private val validToken = sign(claim(), kid, keyPair.getPrivate)

  private def segments(token: String): (String, String, String) = token.split('.') match {
    case Array(h, p, s) => (h, p, s)
    case _              => throw new IllegalArgumentException("not a JWS")
  }

  private def withSignature(token: String, signature: String): String = {
    val (h, p, _) = segments(token)
    s"$h.$p.$signature"
  }

  private def rejected(token: String) = assertTrue(EdDsaJwt.verify(token, keyPair.getPublic).isFailure)

  val spec: Spec[TestEnvironment & Scope, Any] = suite("EdDsaJwt")(
    test("verifies a token signed with the matching key and returns its header and claim") {
      val result = EdDsaJwt.verify(validToken, keyPair.getPublic)
      assertTrue(
        result.map(_._1.alg).toOption.flatten.contains("EdDSA"),
        result.map(_._1.kid).toOption.flatten.contains(kid),
        result.map(_._2).toOption.contains(claim()),
      )
    },
    test("peekHeader returns alg and kid without a key") {
      val header = EdDsaJwt.peekHeader(validToken)
      assertTrue(header.toOption.flatMap(_.alg).contains("EdDSA"), header.toOption.flatMap(_.kid).contains(kid))
    },
    test("peekHeader fails on a token that is not three base64url segments") {
      assertTrue(EdDsaJwt.peekHeader("not-a-token").isFailure, EdDsaJwt.peekHeader("a.b").isFailure)
    },
    test("rejects a tampered payload") {
      val (h, _, s) = segments(validToken)
      val tampered  = s"$h.${encode(claim("jti-2").toJson)}.$s"
      rejected(tampered)
    },
    test("rejects a signature by another key") {
      rejected(sign(claim(), kid, otherKeyPair.getPrivate))
    },
    test("rejects alg HS256, none and eddsa, even with a valid Ed25519 signature") {
      val tokens = List("HS256", "none", "eddsa").map(alg =>
        signRaw(headerJson(alg = alg, kid = Some(kid)), claim().toJson, keyPair.getPrivate),
      )
      assertTrue(tokens.forall(EdDsaJwt.verify(_, keyPair.getPublic).isFailure))
    },
    test("rejects a header without alg") {
      val header = s"""{"typ":"JWT","kid":"$kid"}"""
      rejected(signRaw(header, claim().toJson, keyPair.getPrivate))
    },
    test("rejects a 63-byte signature") {
      val (_, _, s) = segments(validToken)
      val truncated = base64Url(Base64.getUrlDecoder.decode(s).take(63))
      rejected(withSignature(validToken, truncated))
    },
    test("rejects a malformed signature without throwing") {
      val notBase64    = withSignature(validToken, "abcde")
      val garbage64    = withSignature(validToken, base64Url(Array.fill[Byte](64)(-1)))
      val emptySegment = withSignature(validToken, "")
      val (h, p, s)    = segments(validToken)
      val fourSegments = s"$h.$p.$s.$s"
      val results      = List(notBase64, garbage64, emptySegment, fourSegments).map(EdDsaJwt.verify(_, keyPair.getPublic))
      assertTrue(results.forall(_.isFailure))
    },
    test("rejects a padded signature that would otherwise verify") {
      val (_, _, s) = segments(validToken)
      val padded    = s + "=" * ((4 - s.length % 4) % 4)
      assertTrue(padded != s) && rejected(withSignature(validToken, padded))
    },
    test("rejects standard (non-url) base64 that would otherwise verify") {
      // Retry until the signature contains a character that differs between the two alphabets.
      val (token, signatureBytes) = LazyList
        .from(0)
        .map { i =>
          val t = sign(claim(s"jti-$i"), kid, keyPair.getPrivate)
          (t, Base64.getUrlDecoder.decode(segments(t)._3))
        }
        .find { case (t, _) => segments(t)._3.exists(c => c == '-' || c == '_') }
        .get
      val standard = Base64.getEncoder.withoutPadding().encodeToString(signatureBytes)
      assertTrue(standard != segments(token)._3) && rejected(withSignature(token, standard))
    },
    test("rejects whitespace inside a segment") {
      val (h, p, s) = segments(validToken)
      rejected(s"$h.$p.${s.take(10)} ${s.drop(10)}")
    },
    test("rejects a token with a crit header parameter") {
      val header = headerJson(kid = Some(kid), extra = "crit" -> Json.Arr(Json.Str("exp")))
      rejected(signRaw(header, claim().toJson, keyPair.getPrivate))
    },
    test("peekHeader and verify fail, without throwing, on a deeply nested oversized header") {
      val nested = "{\"alg\":\"EdDSA\",\"x\":" + "[" * 200000 + "]" * 200000 + "}"
      val token  = s"${encode(nested)}.${encode(claim().toJson)}.${segments(validToken)._3}"
      assertTrue(EdDsaJwt.peekHeader(token).isFailure, EdDsaJwt.verify(token, keyPair.getPublic).isFailure)
    },
    test("rejects a key that is not an Ed25519 key") {
      val rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic
      assertTrue(EdDsaJwt.verify(validToken, rsa).isFailure)
    },
  )
}
