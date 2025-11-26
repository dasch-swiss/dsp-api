/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.json.*
import zio.json.internal.Write
import zio.json.interop.refined.*

import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.*

case class AuthenticationEndpointsV2(
  private val baseEndpoints: BaseEndpoints,
  private val authenticator: Authenticator,
) {

  private val basePath: EndpointInput[Unit] = "v2" / "authentication"

  val getV2Authentication = baseEndpoints.securedEndpoint.get
    .in(basePath)
    .out(jsonBody[CheckResponse])

  val postV2Authentication = baseEndpoints.publicEndpoint.post
    .in(basePath)
    .in(jsonBody[LoginPayload])
    .out(jsonBody[TokenResponse])

  val deleteV2Authentication = baseEndpoints.publicEndpoint.delete
    .in(basePath)
    .in(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .out(jsonBody[LogoutResponse])

  val getV2Login = baseEndpoints.publicEndpoint.get
    .in("v2" / "login")
    .out(htmlBodyUtf8)

  val postV2Login = baseEndpoints.publicEndpoint.post
    .in("v2" / "login")
    .in(formBody[LoginForm])
    .out(jsonBody[TokenResponse])
}

object AuthenticationEndpointsV2 {

  val layer = ZLayer.derive[AuthenticationEndpointsV2]

  final case class CheckResponse(message: String)
  object CheckResponse {
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
    import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*

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

  final case class LoginForm(username: String, password: String)
}
