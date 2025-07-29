/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.infrastructure

import zio.Duration

/**
 * Configuration for JWT token generation and validation.
 */
final case class JwtConfig(secret: String, expiration: Duration, issuer: Option[String]) {
  def issuerAsString(): String = issuer.getOrElse(
    throw new IllegalStateException(
      "This should never happen, the issuer may be left blank in application.conf but the default is taken from external host and port.",
    ),
  )
}

/**
 * Configuration for DSP-Ingest service integration.
 */
final case class DspIngestConfig(baseUrl: String, audience: String)

/**
 * Simple IRI wrapper for user identification in JWT tokens.
 */
final case class UserIri(value: String) extends AnyVal {
  override def toString: String = value
}