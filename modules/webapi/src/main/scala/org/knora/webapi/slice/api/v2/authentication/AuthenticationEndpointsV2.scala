/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.authentication

import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.ztapir.auth
import zio.*
import zio.json.*
import zio.json.internal.Write
import zio.json.interop.refined.*

import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.*
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator

final class AuthenticationEndpointsV2(
  baseEndpoints: BaseEndpoints,
  authenticator: Authenticator,
) {

  private val basePath: EndpointInput[Unit] = "v2" / "authentication"
  private val cookieName                    = authenticator.calculateCookieName()

  val getV2Authentication = baseEndpoints.publicEndpoint.get
    .in(basePath)
    .in(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .in(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))
    .out(setCookieOpt(cookieName))
    .out(jsonBody[CheckResponse])

  val postV2Authentication = baseEndpoints.publicEndpoint.post
    .in(basePath)
    .in(jsonBody[LoginPayload])
    .out(setCookie(cookieName))
    .out(jsonBody[TokenResponse])

  val deleteV2Authentication = baseEndpoints.publicEndpoint.delete
    .in(basePath)
    .in(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .in(cookie[Option[String]](cookieName))
    .out(setCookie(cookieName))
    .out(jsonBody[LogoutResponse])
}

object AuthenticationEndpointsV2 {

  val layer = ZLayer.derive[AuthenticationEndpointsV2]

  final case class CheckResponse(message: String)
  object CheckResponse {
    val OK                         = CheckResponse("credentials are OK")
    given JsonCodec[CheckResponse] = DeriveJsonCodec.gen[CheckResponse]
  }

  final case class TokenResponse(token: String)
  object TokenResponse {
    given JsonCodec[TokenResponse]       = DeriveJsonCodec.gen[TokenResponse]
    def apply(token: Jwt): TokenResponse = TokenResponse(token.jwtString)
  }

  final case class LogoutResponse(status: Int, message: String)
  object LogoutResponse {
    given JsonCodec[LogoutResponse] = DeriveJsonCodec.gen[LogoutResponse]
  }

  trait WithPassword { def password: String }
  enum LoginPayload extends WithPassword {
    case IriPassword(iri: UserIri, password: String)            extends LoginPayload
    case EmailPassword(email: Email, password: String)          extends LoginPayload
    case UsernamePassword(username: Username, password: String) extends LoginPayload
  }
  object LoginPayload {
    import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec.*

    private val iriPasswordCodec: JsonCodec[IriPassword]           = DeriveJsonCodec.gen[IriPassword]
    private val emailPasswordCodec: JsonCodec[EmailPassword]       = DeriveJsonCodec.gen[EmailPassword]
    private val usernamePasswordCodec: JsonCodec[UsernamePassword] = DeriveJsonCodec.gen[UsernamePassword]

    private val loginPayloadEncoder: JsonEncoder[LoginPayload] =
      (a: LoginPayload, indent: Option[RuntimeFlags], out: Write) =>
        a match {
          case e: LoginPayload.EmailPassword    => emailPasswordCodec.encoder.unsafeEncode(e, indent, out)
          case i: LoginPayload.IriPassword      => iriPasswordCodec.encoder.unsafeEncode(i, indent, out)
          case u: LoginPayload.UsernamePassword => usernamePasswordCodec.encoder.unsafeEncode(u, indent, out)
        }

    private val loginPayloadDecoder: JsonDecoder[LoginPayload] =
      iriPasswordCodec.decoder
        .asInstanceOf[JsonDecoder[LoginPayload]]
        .orElse(usernamePasswordCodec.decoder.asInstanceOf[JsonDecoder[LoginPayload]])
        .orElse(emailPasswordCodec.decoder.asInstanceOf[JsonDecoder[LoginPayload]])

    given loginPayloadCodec: JsonCodec[LoginPayload] = JsonCodec(loginPayloadEncoder, loginPayloadDecoder)
  }
}
