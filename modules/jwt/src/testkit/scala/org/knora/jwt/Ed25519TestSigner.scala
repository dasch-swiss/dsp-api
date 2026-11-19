/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import zio.json.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/**
 * Mints Ed25519-signed tokens for tests, the way an export service client does. Test-only: dsp-api never signs
 * with a private key.
 */
object Ed25519TestSigner {

  def generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

  /** The base64 of the X.509 SubjectPublicKeyInfo DER, as `openssl pkey -pubout` prints it without the armour. */
  def spkiBase64(key: PublicKey): String = Base64.getEncoder.encodeToString(key.getEncoded)

  def sign(claim: JwtClaim, kid: String, privateKey: PrivateKey): String =
    signRaw(headerJson(kid = Some(kid)), claim.toJson, privateKey)

  /** A header with the given `alg`, optional `kid` and extra fields, for tests that need a non-standard header. */
  def headerJson(alg: String = "EdDSA", kid: Option[String] = None, extra: (String, Json)*): String = {
    val fields = List("alg" -> Json.Str(alg), "typ" -> Json.Str("JWT")) ++ kid.map("kid" -> Json.Str(_)) ++ extra
    Json.Obj(fields*).toJson
  }

  /** Signs `header.payload` with Ed25519 whatever the header says. */
  def signRaw(headerJson: String, payloadJson: String, privateKey: PrivateKey): String = {
    val signingInput = s"${encode(headerJson)}.${encode(payloadJson)}"
    s"$signingInput.${base64Url(signBytes(signingInput, privateKey))}"
  }

  def signBytes(signingInput: String, privateKey: PrivateKey): Array[Byte] = {
    val signer = Signature.getInstance("Ed25519")
    signer.initSign(privateKey)
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII))
    signer.sign()
  }

  def encode(json: String): String = base64Url(json.getBytes(StandardCharsets.UTF_8))

  def base64Url(bytes: Array[Byte]): String = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
}
