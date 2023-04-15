/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginApiRequestPayloadV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2

/**
 * A route providing API v2 authentication support. It allows the creation of "sessions", which are used in the SALSAH app.
 */
final case class AuthenticationRouteV2()(
  private implicit val runtime: Runtime[StringFormatter with Authenticator]
) extends AuthenticationV2JsonProtocol {

  def makeRoute: Route =
    path("v2" / "authentication") {
      get { // authenticate credentials
        ctx => RouteUtilV2.complete(ctx, Authenticator.doAuthenticateV2(ctx))
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
            val task = ZIO.serviceWithZIO[StringFormatter] { sf: StringFormatter =>
              val userId      = UserIdentifierADM(apiRequest.iri, apiRequest.email, apiRequest.username)(sf)
              val credentials = KnoraPasswordCredentialsV2(userId, apiRequest.password)
              Authenticator.doLoginV2(credentials)
            }
            RouteUtilV2.complete(requestContext, task)
          }
        } ~
        // logout
        delete(ctx => RouteUtilV2.complete(ctx, Authenticator.doLogoutV2(ctx)))
    } ~
      path("v2" / "login") {
        get { // html login interface (necessary for IIIF Authentication API support)
          ctx => RouteUtilV2.complete(ctx, Authenticator.presentLoginFormV2(ctx))
        } ~
          post { // called by html login interface (necessary for IIIF Authentication API support)
            formFields(Symbol("username"), Symbol("password")) { (username, password) => requestContext =>
              {
                val task =
                  ZIO.serviceWithZIO[StringFormatter] { sf: StringFormatter =>
                    val userId      = UserIdentifierADM(maybeUsername = Some(username))(sf)
                    val credentials = KnoraPasswordCredentialsV2(userId, password)
                    Authenticator.doLoginV2(credentials)
                  }
                RouteUtilV2.complete(requestContext, task)
              }
            }
          }
      }
}
