/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.jwt

import zio.json.*
import zio.json.ast.Json

/**
 * Represents the payload (claims) of a JWT token.
 *
 * @param content   custom content as a JSON string (stored in the payload alongside standard claims)
 * @param issuer    the "iss" claim
 * @param subject   the "sub" claim
 * @param audience  the "aud" claim
 * @param expiration the "exp" claim (seconds since epoch)
 * @param issuedAt  the "iat" claim (seconds since epoch)
 * @param jwtId     the "jti" claim
 */
final case class JwtClaim(
  content: String = "{}",
  issuer: Option[String] = None,
  subject: Option[String] = None,
  audience: Option[Set[String]] = None,
  expiration: Option[Long] = None,
  issuedAt: Option[Long] = None,
  jwtId: Option[String] = None,
) {

  /**
   * Adds a key-value pair to the content JSON. The content must be a valid JSON object.
   */
  def +(key: String, value: String): JwtClaim = {
    val currentObj = content.fromJson[Json.Obj].getOrElse(Json.Obj())
    val updated    = Json.Obj(currentObj.fields :+ (key -> Json.Str(value)): _*)
    copy(content = updated.toJson)
  }

  /**
   * Serializes this claim to a JSON string suitable for use as the JWT payload.
   * Standard claims are merged with the content object.
   */
  def toJson: String = {
    val contentFields                        = content.fromJson[Json.Obj].getOrElse(Json.Obj()).fields.toList
    val standardFields: List[(String, Json)] = List(
      issuer.map("iss" -> Json.Str(_)),
      subject.map("sub" -> Json.Str(_)),
      audience.map(a => "aud" -> (if (a.size == 1) Json.Str(a.head) else Json.Arr(a.map(Json.Str(_)).toSeq: _*))),
      expiration.map("exp" -> Json.Num(_)),
      issuedAt.map("iat" -> Json.Num(_)),
      jwtId.map("jti" -> Json.Str(_)),
    ).flatten
    Json.Obj((contentFields ++ standardFields): _*).toJson
  }
}

object JwtClaim {

  /**
   * Parses a JWT payload JSON string into a JwtClaim.
   */
  def fromJsonString(json: String): Either[String, JwtClaim] =
    json.fromJson[Json.Obj].map { obj =>
      val fields                                = obj.fields.toMap
      def str(key: String): Option[String]      = fields.get(key).flatMap(_.asString)
      def num(key: String): Option[Long]        = fields.get(key).flatMap(_.asNumber).map(_.value.longValue)
      def aud(key: String): Option[Set[String]] = fields.get(key).flatMap { v =>
        v.asString.map(Set(_)).orElse(v.asArray.map(_.flatMap(_.asString).toSet))
      }

      val standardKeys  = Set("iss", "sub", "aud", "exp", "iat", "jti")
      val contentFields = fields.filterNot { case (k, _) => standardKeys.contains(k) }
      val contentJson   = if (contentFields.isEmpty) "{}" else Json.Obj(contentFields.toSeq: _*).toJson

      JwtClaim(
        content = contentJson,
        issuer = str("iss"),
        subject = str("sub"),
        audience = aud("aud"),
        expiration = num("exp"),
        issuedAt = num("iat"),
        jwtId = str("jti"),
      )
    }
}
