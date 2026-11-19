/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.util.Base64
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.matching.Regex

/**
 * Verifies JWS tokens signed with Ed25519 (`alg` `EdDSA`, RFC 8037) using the JDK provider only.
 *
 * There is no signing here: the private key belongs to the client that mints the token. Failures carry fixed
 * messages that never quote the token.
 */
object EdDsaJwt {

  /** The only `alg` value accepted, compared exactly. */
  val Algorithm: String = "EdDSA"

  private val Ed25519         = "Ed25519"
  private val SignatureLength = 64

  // Unpadded base64url only: the JDK decoder would also accept `=` padding, which RFC 7515 forbids.
  private val Base64UrlPart: Regex = "^[A-Za-z0-9_-]+$".r

  private final case class Parts(header: String, payload: String, signature: String) {
    def signingInput: String = s"$header.$payload"
  }

  /**
   * Parses the header without verifying the signature. The result is untrusted: use it only to choose a verifier
   * and look up the key.
   */
  def peekHeader(token: String): Try[JwtHeader] =
    split(token).flatMap(parts => decodeHeader(parts.header))

  /**
   * Verifies the signature over the received `header.payload` bytes with `key` and returns the header and claim.
   * Fails unless `alg` is exactly `EdDSA`, `crit` is absent, the key is an Ed25519 key and the signature is a valid
   * 64-byte Ed25519 signature. Checks no claim: expiry, audience and the rest are the caller's.
   */
  def verify(token: String, key: PublicKey): Try[(JwtHeader, JwtClaim)] =
    for {
      parts     <- split(token)
      header    <- decodeHeader(parts.header)
      _         <- check(header.alg.contains(Algorithm), "Unsupported JWT algorithm")
      _         <- check(!header.crit, "Unsupported JWT crit header parameter")
      _         <- checkKey(key)
      signature <- decode(parts.signature)
      _         <- check(signature.length == SignatureLength, "Invalid JWT signature length")
      _         <- verifySignature(parts.signingInput, signature, key)
      payload   <- decode(parts.payload)
      claim     <- JwtClaim
                 .fromJsonString(new String(payload, StandardCharsets.UTF_8))
                 .fold(_ => fail("Invalid JWT payload"), Success(_))
    } yield (header, claim)

  private def split(token: String): Try[Parts] =
    token.split("\\.", -1) match {
      case Array(h, p, s) if Seq(h, p, s).forall(Base64UrlPart.matches) => Success(Parts(h, p, s))
      case _                                                            => fail("Invalid JWT format")
    }

  private def decodeHeader(headerB64: String): Try[JwtHeader] =
    decode(headerB64).flatMap(bytes =>
      JwtHeader
        .fromJsonString(new String(bytes, StandardCharsets.UTF_8))
        .fold(_ => fail("Invalid JWT header"), Success(_)),
    )

  private def decode(part: String): Try[Array[Byte]] =
    Try(Base64.getUrlDecoder.decode(part)).orElse(fail("Invalid JWT base64url encoding"))

  private def checkKey(key: PublicKey): Try[Unit] =
    key match {
      case k: EdECPublicKey if k.getParams.getName.equalsIgnoreCase(Ed25519) => Success(())
      case _                                                                 => fail("Not an Ed25519 public key")
    }

  // The JDK throws SignatureException for some malformed signatures and returns false for others; both fail here.
  private def verifySignature(signingInput: String, signature: Array[Byte], key: PublicKey): Try[Unit] =
    Try {
      val verifier = Signature.getInstance(Ed25519)
      verifier.initVerify(key)
      verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII))
      verifier.verify(signature)
    }.recover { case _ => false }.flatMap(valid => check(valid, "Invalid JWT signature"))

  private def check(condition: Boolean, message: String): Try[Unit] =
    if (condition) Success(()) else fail(message)

  private def fail[A](message: String): Try[A] = Failure(new SecurityException(message))
}
