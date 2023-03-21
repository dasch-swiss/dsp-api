/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.UnsafeZioRun

/**
 * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
 */
final case class AuthenticationRouteV1(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: zio.Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime) {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v1" / "authenticate") {
      get { requestContext =>
        requestContext.complete {
          UnsafeZioRun.runToFuture(Authenticator.doAuthenticateV1(requestContext))
        }
      }
    } ~ path("v1" / "session") {
      get { requestContext =>
        requestContext.complete {
          UnsafeZioRun.runToFuture {
            val params = requestContext.request.uri.query().toMap
            if (params.contains("logout")) {
              Authenticator.doLogoutV2(requestContext)
            } else if (params.contains("login")) {
              Authenticator.doLoginV1(requestContext)
            } else {
              Authenticator.doAuthenticateV1(requestContext)
            }
          }
        }
      } ~ post { requestContext =>
        requestContext.complete {
          UnsafeZioRun.runToFuture(Authenticator.doLoginV1(requestContext))
        }
      } ~ delete { requestContext =>
        requestContext.complete(UnsafeZioRun.runToFuture(Authenticator.doLogoutV2(requestContext)))
      }
    }
}
