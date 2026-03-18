/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Encodes and decodes JWT tokens using HMAC-SHA256.
 *
 * This is a minimal JWT implementation covering only the HS256 algorithm,
 * which is the only algorithm used in this project.
 *
 * The secret key is accepted as `Array[Byte]` rather than `String` to avoid
 * keeping key material in immutable, non-clearable JVM strings.
 */
object JwtCodec {

  private val Encoder = Base64.getUrlEncoder.withoutPadding()
  private val Decoder = Base64.getUrlDecoder

  private def base64UrlEncode(bytes: Array[Byte]): String =
    Encoder.encodeToString(bytes)

  private def base64UrlEncode(s: String): String =
    base64UrlEncode(s.getBytes(StandardCharsets.UTF_8))

  private def base64UrlDecode(s: String): Array[Byte] =
    Decoder.decode(s)

  private def base64UrlDecodeToString(s: String): String =
    new String(base64UrlDecode(s), StandardCharsets.UTF_8)

  private def hmacSha256(data: String, secret: Array[Byte]): Array[Byte] = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(secret, "HmacSHA256"))
    mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
  }

  /**
   * Encodes a JWT from a pre-built header JSON string and claim.
   *
   * @param header    the JWT header as a JSON string
   * @param claimJson the JWT payload as a JSON string
   * @param secret    the HMAC-SHA256 secret key bytes
   * @return the encoded JWT string
   */
  def encode(header: String, claimJson: String, secret: Array[Byte]): String = {
    val headerEncoded  = base64UrlEncode(header)
    val payloadEncoded = base64UrlEncode(claimJson)
    val signingInput   = s"$headerEncoded.$payloadEncoded"
    val signature      = base64UrlEncode(hmacSha256(signingInput, secret))
    s"$signingInput.$signature"
  }

  /**
   * Encodes a JWT from a JwtClaim, using the default HS256 header.
   *
   * @param claim  the JWT claims
   * @param secret the HMAC-SHA256 secret key bytes
   * @return the encoded JWT string
   */
  def encode(claim: JwtClaim, secret: Array[Byte]): String =
    encode("""{"typ":"JWT","alg":"HS256"}""", claim.toJson, secret)

  /**
   * Decodes and verifies a JWT token, returning just the claim.
   *
   * @param token  the JWT string
   * @param secret the HMAC-SHA256 secret key bytes
   * @return a Try containing the JwtClaim, or a Failure if the token is invalid
   */
  def decode(token: String, secret: Array[Byte]): Try[JwtClaim] =
    decodeAll(token, secret).map(_._2)

  /**
   * Decodes and verifies a JWT token, returning header, claim, and signature.
   *
   * Validates:
   *   - HMAC-SHA256 signature
   *   - Algorithm is exactly HS256
   *   - Expiration claim is present and not in the past
   *   - Optionally: issuer and audience if provided
   *
   * @param token            the JWT string
   * @param secret           the HMAC-SHA256 secret key bytes
   * @param expectedIssuer   if provided, the "iss" claim must match this value
   * @param expectedAudience if provided, the "aud" claim must contain all values in this set
   * @return a Try containing (JwtHeader, JwtClaim, signature), or a Failure if invalid
   */
  def decodeAll(
    token: String,
    secret: Array[Byte],
    expectedIssuer: Option[String] = None,
    expectedAudience: Option[Set[String]] = None,
  ): Try[(JwtHeader, JwtClaim, String)] = {
    val parts = token.split("\\.", -1)
    if (parts.length != 3) {
      Failure(new IllegalArgumentException("Invalid JWT format: expected 3 parts"))
    } else {
      val Array(headerB64, payloadB64, signatureB64) = parts
      val signingInput                               = s"$headerB64.$payloadB64"

      // Verify signature
      val expectedSig = hmacSha256(signingInput, secret)
      val actualSig   = Try(base64UrlDecode(signatureB64))

      actualSig match {
        case Failure(e)                                                     => Failure(new SecurityException(s"Invalid signature encoding: ${e.getMessage}"))
        case Success(actual) if !MessageDigest.isEqual(expectedSig, actual) =>
          Failure(new SecurityException("Invalid JWT signature"))
        case Success(_) =>
          for {
            headerJson <- Try(base64UrlDecodeToString(headerB64))
            header     <- JwtHeader.fromJsonString(headerJson).fold(e => Failure(new Exception(e)), Success(_))
            _          <- header.alg match {
                   case Some("HS256") => Success(())
                   case Some(other)   => Failure(new SecurityException(s"Unsupported algorithm: $other"))
                   case None          => Failure(new SecurityException("Missing required algorithm claim"))
                 }
            payloadJson <- Try(base64UrlDecodeToString(payloadB64))
            claim       <- JwtClaim.fromJsonString(payloadJson).fold(e => Failure(new Exception(e)), Success(_))
            _           <- validateExpiration(claim)
            _           <- validateIssuer(claim, expectedIssuer)
            _           <- validateAudience(claim, expectedAudience)
          } yield (header, claim, signatureB64)
      }
    }
  }

  private def validateExpiration(claim: JwtClaim): Try[Unit] =
    claim.expiration match {
      case Some(exp) =>
        val now = java.time.Instant.now.getEpochSecond
        if (now > exp) Failure(new SecurityException("JWT token has expired"))
        else Success(())
      case None => Failure(new SecurityException("JWT token missing required expiration claim"))
    }

  private def validateIssuer(claim: JwtClaim, expectedIssuer: Option[String]): Try[Unit] =
    expectedIssuer match {
      case Some(expected) if !claim.issuer.contains(expected) =>
        Failure(new SecurityException(s"JWT issuer mismatch: expected $expected"))
      case _ => Success(())
    }

  private def validateAudience(claim: JwtClaim, expectedAudience: Option[Set[String]]): Try[Unit] =
    expectedAudience match {
      case Some(expected) if !expected.subsetOf(claim.audience.getOrElse(Set.empty)) =>
        Failure(new SecurityException(s"JWT audience mismatch: expected $expected"))
      case _ => Success(())
    }
}
