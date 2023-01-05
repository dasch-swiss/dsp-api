/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.middleware

import zhttp.http._
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.admin.AuthenticatorService

final case class AuthenticationMiddleware(authenticatorService: AuthenticatorService) {

  /**
   * An authentication middleware that transforms a HttpApp (Request => Response)
   * into a custom Http ((Request, UserADM) => Response).
   * This makes the requesting user available in the context of the request in a ZIO HTTP route.
   *
   * Can be used like this:
   *    {{{
   *      final case class Route(authMiddleware: AuthenticationMiddleware){
   *        private val middleware = authMiddleware.authenticationMiddleware
   *
   *        private val authenticatedRoutes: UHttp[(Request, UserADM), Response] =
   *          Http.collectZIO[(Request, UserADM)] {
   *            case (Method.GET -> !! / "admin" / "users", user: UserADM)  => ???
   *          }
   *
   *        val routes = authenticatedRoutes @@ middleware
   *      }
   *    }}}
   */
  val authenticationMiddleware: UMiddleware[(Request, UserADM), Response, Request, Response] =
    Middleware.codecZIO[Request, Response](
      request =>
        for {
          ru <-
            authenticatorService
              .getUser(request)
              .catchAll(_ => ZIO.succeed(KnoraSystemInstances.Users.AnonymousUser))
        } yield (request, ru),
      out => ZIO.succeed(out)
    )
}

object AuthenticationMiddleware {
  val layer: URLayer[AppConfig & AuthenticatorService, AuthenticationMiddleware] =
    ZLayer.fromFunction(AuthenticationMiddleware.apply _)
}
