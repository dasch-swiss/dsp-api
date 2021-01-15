/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

object DeletePermissionRouteADM {
  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class DeletePermissionRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  import DeletePermissionRouteADM._

  /**
    * Returns the route.
    */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    deletePermission(featureFactoryConfig)

  /**
    * Delete a permission
    */
  private def deletePermission(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment) { iri =>
      delete { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          PermissionDeleteRequestADM(
            permissionIri = iri,
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
