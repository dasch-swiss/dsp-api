/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route

import java.util.UUID
import scala.concurrent.Future

import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
final case class CreatePermissionRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: zio.Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    createAdministrativePermission() ~
      createDefaultObjectAccessPermission()

  /**
   * Create a new administrative permission
   */
  private def createAdministrativePermission(): Route =
    path(permissionsBasePath / "ap") {
      post {
        /* create a new administrative permission */
        entity(as[CreateAdministrativePermissionAPIRequestADM]) { apiRequest => requestContext =>
          val requestMessage = for {
            requestingUser <- getUserADM(requestContext)
          } yield AdministrativePermissionCreateRequestADM(
            createRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * Create default object access permission
   */
  private def createDefaultObjectAccessPermission(): Route =
    path(permissionsBasePath / "doap") {
      post {
        /* create a new default object access permission */
        entity(as[CreateDefaultObjectAccessPermissionAPIRequestADM]) { apiRequest => requestContext =>
          val requestMessage: Future[DefaultObjectAccessPermissionCreateRequestADM] = for {
            requestingUser <- getUserADM(requestContext)
          } yield DefaultObjectAccessPermissionCreateRequestADM(
            createRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }
}
