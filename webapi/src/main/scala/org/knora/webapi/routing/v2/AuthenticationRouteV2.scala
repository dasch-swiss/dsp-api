/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginApiRequestPayloadV2
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.security.Authenticator

/**
 * A route providing API v2 authentication support. It allows the creation of "sessions", which are used in the SALSAH app.
 */
final case class AuthenticationRouteV2()(
  private implicit val runtime: Runtime[Authenticator],
) extends AuthenticationV2JsonProtocol {

  def makeRoute: Route =
    path("v2" / "authentication") {
      get { // authenticate credentials
        ctx => RouteUtilV2.complete(ctx, ZIO.serviceWithZIO[Authenticator](_.doAuthenticateV2(ctx)))
      } ~
        post { // login
          /* send iri, email, or username, and password in body as:
           * {
           *   "iri|username|email": "value_of_iri_username_or_email",
           *   "password": "userspassword"
           * }, e.g., for email:
           * {
           *   "email": "email@example.com",
           *   "password": "userspassword"
           * }
           *
           * Returns a JWT token (and session cookie), which can be supplied with every request thereafter in
           * the authorization header with the bearer scheme: 'Authorization: Bearer abc.def.ghi'
           */
          entity(as[LoginApiRequestPayloadV2]) { apiRequest => requestContext =>
            val task = for {
              crId <-
                ZIO
                  .fromOption(CredentialsIdentifier.fromOptions(apiRequest.iri, apiRequest.email, apiRequest.username))
                  .orElseFail(BadRequestException("Invalid user identifier."))
              credentials = KnoraPasswordCredentialsV2(crId, apiRequest.password)
              res        <- ZIO.serviceWithZIO[Authenticator](_.doLoginV2(credentials))
            } yield res
            RouteUtilV2.complete(requestContext, task)
          }
        } ~
        // logout
        delete(ctx => RouteUtilV2.complete(ctx, ZIO.serviceWithZIO[Authenticator](_.doLogoutV2(ctx))))
    } ~
      path("v2" / "login") {
        get { // html login interface (necessary for IIIF Authentication API support)
          ctx => RouteUtilV2.complete(ctx, ZIO.serviceWithZIO[Authenticator](_.presentLoginFormV2(ctx)))
        } ~
          post { // called by html login interface (necessary for IIIF Authentication API support)
            formFields(Symbol("username"), Symbol("password")) { (username, password) => requestContext =>
              {
                val task = for {
                  username <-
                    ZIO.fromEither(Username.from(username)).orElseFail(BadRequestException("Invalid username."))
                  credentials = KnoraPasswordCredentialsV2(CredentialsIdentifier.UsernameIdentifier(username), password)
                  res        <- ZIO.serviceWithZIO[Authenticator](_.doLoginV2(credentials))
                } yield res
                RouteUtilV2.complete(requestContext, task)
              }
            }
          }
      }
}
