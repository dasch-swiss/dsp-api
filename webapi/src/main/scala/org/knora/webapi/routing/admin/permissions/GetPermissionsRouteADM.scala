/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route

import java.util.UUID

import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
class GetPermissionsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getAdministrativePermissionForProjectGroup() ~
      getAdministrativePermissionsForProject() ~
      getDefaultObjectAccessPermissionsForProject() ~
      getPermissionsForProject()

  private def getAdministrativePermissionForProjectGroup(): Route =
    path(permissionsBasePath / "ap" / Segment / Segment) { (projectIri, groupIri) =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  private def getAdministrativePermissionsForProject(): Route =
    path(permissionsBasePath / "ap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield AdministrativePermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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

  private def getDefaultObjectAccessPermissionsForProject(): Route =
    path(permissionsBasePath / "doap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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

  private def getPermissionsForProject(): Route =
    path(permissionsBasePath / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield PermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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
