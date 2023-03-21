/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginApiRequestPayloadV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.UnsafeZioRun

/**
 * A route providing API v2 authentication support. It allows the creation of "sessions", which are used in the SALSAH app.
 */
final case class AuthenticationRouteV2(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime)
    with AuthenticationV2JsonProtocol {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v2" / "authentication") {
      get { // authenticate credentials
        requestContext =>
          requestContext.complete(UnsafeZioRun.runToFuture(Authenticator.doAuthenticateV2(requestContext)))
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
            requestContext.complete {
              UnsafeZioRun.runToFuture(
                Authenticator.doLoginV2(
                  KnoraPasswordCredentialsV2(
                    UserIdentifierADM(apiRequest.iri, apiRequest.email, apiRequest.username),
                    apiRequest.password
                  )
                )
              )
            }
          }
        } ~
        delete { // logout
          requestContext =>
            requestContext.complete(UnsafeZioRun.runToFuture(Authenticator.doLogoutV2(requestContext)))
        }
    } ~
      path("v2" / "login") {
        get { // html login interface (necessary for IIIF Authentication API support)
          requestContext =>
            requestContext.complete(UnsafeZioRun.runToFuture(Authenticator.presentLoginFormV2(requestContext)))
        } ~
          post { // called by html login interface (necessary for IIIF Authentication API support)
            formFields(Symbol("username"), Symbol("password")) { (username, password) => requestContext =>
              {
                requestContext.complete {
                  UnsafeZioRun.runToFuture(
                    Authenticator.doLoginV2(
                      KnoraPasswordCredentialsV2(UserIdentifierADM(maybeUsername = Some(username)), password)
                    )
                  )
                }
              }
            }
          }
      }
}
