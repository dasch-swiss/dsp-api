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
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.{ClientEndpoint, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.{ExecutionContext, Future}


object PermissionsRouteADM {
    val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
    val PermissionsBasePathString: String = "/admin/permissions"
}


@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class PermissionsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with PermissionsADMJsonProtocol with ClientEndpoint {

    import PermissionsRouteADM._

    /**
     * The directory name to be used for this endpoint's code.
     */
    override val directoryName: String = "permissions"

    private val projectIri: String = URLEncoder.encode(SharedTestDataADM.imagesProject.id, "utf-8")
    private val groupIri: String = URLEncoder.encode(OntologyConstants.KnoraAdmin.ProjectMember, "utf-8")

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route =
        getAdministrativePermissionProjectGroup ~
        getAdministrativePermissionsForProject ~
        getDefaultObjectAccessPermissionsForProject ~
        getPermissionsForProject ~
        createAdministrativePermission ~
        createDefaultObjectAccessPermission


    private def getAdministrativePermissionProjectGroup: Route = path(PermissionsBasePath / "ap" / Segment / Segment) {
        (projectIri, groupIri) =>
            get {
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
    }

    private def getAdministrativePermissionForProjectGroupTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$PermissionsBasePathString/ap/$projectIri/$groupIri") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-administrative-permission-for-project-group-response"),
            text = responseStr
        )
    }

    private def getAdministrativePermissionsForProject: Route = path(PermissionsBasePath / "ap" / Segment) { projectIri =>
        get {
            requestContext =>
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = projectIri,
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

    private def getAdministrativePermissionsForProjectTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$PermissionsBasePathString/ap/$projectIri") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-administrative-permissions-for-project-response"),
            text = responseStr
        )
    }

    private def getDefaultObjectAccessPermissionsForProject: Route = path(PermissionsBasePath / "doap" / Segment) { projectIri =>
        get {
            requestContext =>
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield DefaultObjectAccessPermissionsForProjectGetRequestADM(
                    projectIri = projectIri,
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

    private def getDefaultObjectAccessPermissionsForProjectTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$PermissionsBasePathString/doap/$projectIri") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-defaultObjectAccess-permissions-for-project-response"),
            text = responseStr
        )
    }


    private def getPermissionsForProject: Route = path(PermissionsBasePath / Segment) {
        (projectIri) =>
            get {
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield PermissionsForProjectGetRequestADM(
                        projectIri = projectIri,
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

    private def getPermissionsForProjectTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$PermissionsBasePathString/$projectIri") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-permissions-for-project-response"),
            text = responseStr
        )
    }

    /**
     * Create a new administrative permission
     */
    private def createAdministrativePermission: Route = path(PermissionsBasePath / "ap") {
        post {
            /* create a new administrative permission */
                entity(as[CreateAdministrativePermissionAPIRequestADM]) { apiRequest =>
                    requestContext =>
                        val requestMessage = for {
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

    private def createAdminPermissionTestRequestAndResponse: Future[Set[TestDataFileContent]] = {
        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-administrative-permission-request"),
                    text = SharedTestDataADM.createAdministrativePermissionRequest
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-administrative-permission-response"),
                    text = SharedTestDataADM.createAdministrativePermissionResponse
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-administrative-permission-withCustomIRI-request"),
                    text = SharedTestDataADM.createAdministrativePermissionWithCustomIriRequest
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-administrative-permission-withCustomIRI-response"),
                    text = SharedTestDataADM.createAdministrativePermissionWithCustomIriResponse
                )
            )
        )
    }

    /**
     * Create default object access permission
     */
    private def createDefaultObjectAccessPermission: Route = path(PermissionsBasePath / "doap") {
        post {
            /* create a new default object access permission */
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
    private def createDOAPermissionTestRequestAndResponse: Future[Set[TestDataFileContent]] = {
        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-defaultObjectAccess-permission-request"),
                    text = SharedTestDataADM.createDefaultObjectAccessPermissionRequest
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-defaultObjectAccess-permission-response"),
                    text = SharedTestDataADM.createDefaultObjectAccessPermissionResponse
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-defaultObjectAccess-permission-withCustomIRI-request"),
                    text = SharedTestDataADM.createDefaultObjectAccessPermissionWithCustomIriRequest
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-defaultObjectAccess-permission-withCustomIRI-response"),
                    text = SharedTestDataADM.createDefaultObjectAccessPermissionWithCustomIriResponse
                )
            )
        )
    }


    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext,
                             actorSystem: ActorSystem, materializer: Materializer
                            ): Future[Set[TestDataFileContent]] =  {
        for {
                getAdminPermissionForPG <- getAdministrativePermissionForProjectGroupTestResponse
                getAdminPermissionsForP <- getAdministrativePermissionsForProjectTestResponse
                getDOAPermissionsForP <- getDefaultObjectAccessPermissionsForProjectTestResponse
                getAllPermissionsForP <- getPermissionsForProjectTestResponse
                createAP <- createAdminPermissionTestRequestAndResponse
                createDOAP <- createDOAPermissionTestRequestAndResponse

        } yield createAP ++ createDOAP + getAdminPermissionForPG + getAdminPermissionsForP + getDOAPermissionsForP + getAllPermissionsForP
    }
}
