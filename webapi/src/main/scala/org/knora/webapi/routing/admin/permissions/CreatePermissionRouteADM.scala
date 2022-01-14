/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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

import scala.concurrent.Future

object CreatePermissionRouteADM {
  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class CreatePermissionRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  import CreatePermissionRouteADM._

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    createAdministrativePermission(featureFactoryConfig) ~
      createDefaultObjectAccessPermission(featureFactoryConfig)

  /**
   * Create a new administrative permission
   */
  private def createAdministrativePermission(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / "ap") {
      post {
        /* create a new administrative permission */
        entity(as[CreateAdministrativePermissionAPIRequestADM]) { apiRequest => requestContext =>
          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield AdministrativePermissionCreateRequestADM(
            createRequest = apiRequest,
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

  /**
   * Create default object access permission
   */
  private def createDefaultObjectAccessPermission(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / "doap") {
      post {
        /* create a new default object access permission */
        entity(as[CreateDefaultObjectAccessPermissionAPIRequestADM]) { apiRequest => requestContext =>
          val requestMessage: Future[DefaultObjectAccessPermissionCreateRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield DefaultObjectAccessPermissionCreateRequestADM(
            createRequest = apiRequest,
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
}
