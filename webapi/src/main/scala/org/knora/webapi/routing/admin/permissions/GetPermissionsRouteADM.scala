/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.admin.permissions

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

object GetPermissionsRouteADM {
  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class GetPermissionsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  import GetPermissionsRouteADM._

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getAdministrativePermissionForProjectGroup(featureFactoryConfig) ~
      getAdministrativePermissionsForProject(featureFactoryConfig) ~
      getDefaultObjectAccessPermissionsForProject(featureFactoryConfig) ~
      getPermissionsForProject(featureFactoryConfig)

  private def getAdministrativePermissionForProjectGroup(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / "ap" / Segment / Segment) { (projectIri, groupIri) =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  private def getAdministrativePermissionsForProject(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / "ap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield AdministrativePermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  private def getDefaultObjectAccessPermissionsForProject(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / "doap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  private def getPermissionsForProject(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield PermissionsForProjectGetRequestADM(
          projectIri = projectIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
}
