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

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.stream.ActorMaterializer
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.permissionsmessages.{AdministrativePermissionForProjectGroupGetRequestADM, PermissionType}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{OntologyConstants, SharedTestDataADM}

import scala.concurrent.{ExecutionContext, Future}

object PermissionsRouteADM {
    val PermissionsBasePath = PathMatcher("admin" / "permissions")
    val PermissionsBasePathString: String = "/admin/permissions"
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
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

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getAdministrativePermission

    private def getAdministrativePermission: Route = path(PermissionsBasePath / Segment / Segment) { (projectIri, groupIri) =>
        get {
            requestContext =>
                val params = requestContext.request.uri.query().toMap
                val permissionType = params.getOrElse("permissionType", PermissionType.AP)
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield permissionType match {
                    case _ => AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)
                }

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
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
