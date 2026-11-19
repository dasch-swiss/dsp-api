/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import zio.*

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import org.knora.jwt.Ed25519TestSigner
import org.knora.jwt.JwtClaim

/**
 * The export service the E2E server trusts. A test-only key: its public half is in this module's `application.conf`,
 * which the server reads at startup, before any test could generate a key. Never trust it on a real server.
 */
object ExportServiceTestKey {

  val Name = "e2e-export-service"
  val Kid  = "e2e-export-service-test"

  private val privateKeyPkcs8 = "MC4CAQAwBQYDK2VwBCIEICMqkUAeB4OZfgMLGzgjfDL9JRC9Y5o4ZC0J5YeRuT63"

  private lazy val privateKey: PrivateKey =
    KeyFactory
      .getInstance("Ed25519")
      .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(privateKeyPkcs8)))

  /** A token as the export service mints it: five minutes long, for the given audience, with a fresh `jti`. */
  def token(audience: String): UIO[String] =
    for {
      now <- Clock.instant.map(_.getEpochSecond)
      jti <- Random.nextUUID
    } yield Ed25519TestSigner.sign(
      JwtClaim(
        issuer = Some(Name),
        subject = Some(Name),
        audience = Some(Set(audience)),
        issuedAt = Some(now),
        expiration = Some(now + 5 * 60),
        jwtId = Some(jti.toString),
      ),
      Kid,
      privateKey,
    )
}
