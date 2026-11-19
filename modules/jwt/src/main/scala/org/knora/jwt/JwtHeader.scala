/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import zio.json.*
import zio.json.ast.Json

/**
 * Represents the header of a JWT token.
 *
 * @param kid  the "kid" (key id) header parameter
 * @param crit whether the "crit" header parameter is present; no extension is understood, so a verifier that sees
 *             it must reject the token (RFC 7515 section 4.1.11)
 */
final case class JwtHeader(
  typ: Option[String] = None,
  alg: Option[String] = None,
  kid: Option[String] = None,
  crit: Boolean = false,
)

object JwtHeader {

  /**
   * Parses a JWT header JSON string into a JwtHeader.
   */
  def fromJsonString(json: String): Either[String, JwtHeader] =
    json.fromJson[Json.Obj].map { obj =>
      val fields = obj.fields.toMap
      JwtHeader(
        typ = fields.get("typ").flatMap(_.asString),
        alg = fields.get("alg").flatMap(_.asString),
        kid = fields.get("kid").flatMap(_.asString),
        crit = fields.contains("crit"),
      )
    }
}
