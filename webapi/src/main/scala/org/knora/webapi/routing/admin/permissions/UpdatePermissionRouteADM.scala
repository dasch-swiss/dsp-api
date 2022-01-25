/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

object UpdatePermissionRouteADM {
  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class UpdatePermissionRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  import UpdatePermissionRouteADM._

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    updatePermissionGroup(featureFactoryConfig) ~
      updatePermissionHasPermissions(featureFactoryConfig) ~
      updatePermissionResourceClass(featureFactoryConfig) ~
      updatePermissionProperty(featureFactoryConfig)

  /**
   * Update a permission's group
   */
  private def updatePermissionGroup(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment / "group") { iri =>
      put {
        entity(as[ChangePermissionGroupApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield PermissionChangeGroupRequestADM(
            permissionIri = permissionIri,
            changePermissionGroupRequest = apiRequest,
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
   * Update a permission's set of hasPermissions.
   */
  private def updatePermissionHasPermissions(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment / "hasPermissions") { iri =>
      put {
        entity(as[ChangePermissionHasPermissionsApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield PermissionChangeHasPermissionsRequestADM(
            permissionIri = permissionIri,
            changePermissionHasPermissionsRequest = apiRequest,
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
   * Update a doap permission by setting it for a new resource class
   */
  private def updatePermissionResourceClass(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment / "resourceClass") { iri =>
      put {
        entity(as[ChangePermissionResourceClassApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield PermissionChangeResourceClassRequestADM(
            permissionIri = permissionIri,
            changePermissionResourceClassRequest = apiRequest,
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
   * Update a doap permission by setting it for a new property class
   */
  private def updatePermissionProperty(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(PermissionsBasePath / Segment / "property") { iri =>
      put {
        entity(as[ChangePermissionPropertyApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield PermissionChangePropertyRequestADM(
            permissionIri = permissionIri,
            changePermissionPropertyRequest = apiRequest,
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
