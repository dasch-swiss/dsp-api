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

package org.knora.webapi.routing.admin

import java.net.URLEncoder
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.stream.ActorMaterializer
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{OntologyConstants, SharedTestDataADM}
import org.springframework.web.client.HttpClientErrorException.BadRequest

import scala.concurrent.{ExecutionContext, Future}

object PermissionsRouteADM {
    val PermissionsBasePath = PathMatcher("admin" / "permissions")
    val PermissionsBasePathString: String = "/admin/permissions"
}

@Api(value = "administrative-permissions", produces = "application/json")
@Path("/admin/administrative-permissions")
class PermissionsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {

    import PermissionsRouteADM._

    /**
     * The name of this [[ClientEndpoint]].
     */
    override val name: String = "PermissionsEndpoint"

    /**
     * The directory name to be used for this endpoint's code.
     */
    override val directoryName: String = "permissions"

    /**
     * The URL path of of this [[ClientEndpoint]].
     */
    override val urlPath: String = "/permissions"

    /**
     * A description of this [[ClientEndpoint]].
     */
    override val description: String = "An endpoint for working with Knora permissions."

    // Classes used in client function definitions.

    private val AdministrativePermissionResponse = classRef(OntologyConstants.KnoraAdminV2.AdministrativePermissionResponse.toSmartIri)

    private val projectIri: String = URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")
    private val groupIri: String = URLEncoder.encode(OntologyConstants.KnoraAdmin.ProjectMember, "utf-8")

    override def knoraApiPath: Route =
        getPermissionsForProject ~
          getPermission ~
          createAdministrativePermission ~
          changeAdministrativePermission ~
          createDefaultObjectAccessPermission ~
          changeDefaultObjectAccessPermission ~
          deletePermission

    /**
      * Get all permissions for a project.
      */
    private def getPermissionsForProject: Route = path(PermissionsBasePath / Segment) { projectIri =>
        get {
            requestContext =>
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield PermissionsForProjectGetRequestADM(projectIri, requestingUser, UUID.randomUUID())

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /**
      * Get permission by IRI
      */
    private def getPermission: Route = path(PermissionsBasePath / Segment) { permissionIri =>
        get {
            requestContext =>
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield PermissionForIriGetRequestADM(
                    permissionIri = permissionIri,
                    requestingUser = requestingUser,
                    apiRequestID = UUID.randomUUID()
                )

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /**
      * Delete permission by IRI
      */
    private def deletePermission: Route = path(PermissionsBasePath / Segment) { permissionIri =>
        delete {
            requestContext =>
                val requestMessage: Future[PermissionDeleteRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield PermissionDeleteRequestADM(
                    permissionIri = permissionIri,
                    requestingUser = requestingUser,
                    apiRequestID = UUID.randomUUID()
                )

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /**
      * Create a new administrative permission
      */
    private def createAdministrativePermission: Route = path(PermissionsBasePath / "ap") {
        post {
            entity(as[CreateAdministrativePermissionAPIRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[AdministrativePermissionCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield AdministrativePermissionCreateRequestADM(
                        createRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /**
      * Change administrative permission
      */
    private def changeAdministrativePermission: Route = path(PermissionsBasePath / "ap" / Segment) { permissionIri =>
        put {
            entity(as[ChangeAdministrativePermissionAPIRequestADM]) { apiRequest =>
                requestContext =>

                    if (apiRequest.iri != permissionIri) {
                        throw new BadRequest("The permission IRI in the route does not match the permission IRI in the payload.")
                    }

                    val requestMessage: Future[AdministrativePermissionChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield AdministrativePermissionChangeRequestADM(
                        changeRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }


    /**
      * Create default object access permission
      */
    private def createDefaultObjectAccessPermission: Route = path(PermissionsBasePath / "doap") {
        post {
            entity(as[CreateDefaultObjectAccessPermissionAPIRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[DefaultObjectAccessPermissionCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield DefaultObjectAccessPermissionCreateRequestADM(
                        createRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /**
      * Change default object access permission
      */
    private def changeDefaultObjectAccessPermission: Route = path(PermissionsBasePath / "doap" / Segment) { permissionIri =>
        put {
            entity(as[ChangeDefaultObjectAccessPermissionAPIRequestADM]) { apiRequest =>
                requestContext =>

                    if (apiRequest.iri != permissionIri) {
                        throw new BadRequest("The permission IRI in the route does not match the permission IRI in the payload.")
                    }

                    val requestMessage: Future[DefaultObjectAccessPermissionChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield DefaultObjectAccessPermissionChangeRequestADM(
                        changeRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }



    // #getAdministrativePermissionFunction
    private val getAdministrativePermissionFunction: ClientFunction =
        "getAdministrativePermission" description "Gets the administrative permission for a project and group." params(
            "projectIri" description "The project IRI." paramType UriDatatype,
            "groupIri" description "The group IRI." paramType UriDatatype,
            "permissionType" description "The permission type." paramOptionType StringDatatype
        ) doThis {
            httpGet(
                path = arg("projectIri") / arg("groupIri"),
                params = Seq("permissionType" -> arg("permissionType"))
            )
        } returns AdministrativePermissionResponse
    // #getAdministrativePermissionFunction

    private def getAdministrativePermissionTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$PermissionsBasePathString/$projectIri/$groupIri"))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-administrative-permission-response"),
            text = responseStr
        )
    }

    /**
     * The functions defined by this [[ClientEndpoint]].
     */
    override val functions: Seq[ClientFunction] = Seq(
        getAdministrativePermissionFunction
    )

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        Future.sequence {
            Set(
                getAdministrativePermissionTestResponse
            )
        }
    }
}
