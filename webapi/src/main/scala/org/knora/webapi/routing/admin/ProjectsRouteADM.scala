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

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, ContentDispositionTypes, `Content-Disposition`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.IRI

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object ProjectsRouteADM {
    val ProjectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")
    val ProjectsBasePathString = "/admin/projects"
}

@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ProjectsADMJsonProtocol {

    import ProjectsRouteADM._

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route =
        getProjects ~
            addProject ~
            getKeywords ~
            getProjectKeywords ~
            getProjectByIri ~ getProjectByShortname ~ getProjectByShortcode ~
            changeProject ~
            deleteProject ~
            getProjectMembersByIri ~ getProjectMembersByShortname ~ getProjectMembersByShortcode ~
            getProjectAdminMembersByIri ~ getProjectAdminMembersByShortname ~ getProjectAdminMembersByShortcode ~
            getProjectRestrictedViewSettingsByIri ~ getProjectRestrictedViewSettingsByShortname ~
            getProjectRestrictedViewSettingsByShortcode ~ getProjectData


    /* return all projects */
    @ApiOperation(value = "Get projects", nickname = "getProjects", httpMethod = "GET", response = classOf[ProjectsGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def getProjects: Route = path(ProjectsBasePath) {
        get { requestContext =>
            val requestMessage: Future[ProjectsGetRequestADM] = for {
                requestingUser <- getUserADM(requestContext)
            } yield ProjectsGetRequestADM(requestingUser = requestingUser)
            RouteUtilADM.runJsonRoute(
                requestMessage,
                requestContext,
                settings,
                responderManager,
                log
            )
        }
    }

    /* create a new project */
    @ApiOperation(value = "Add new project", nickname = "addProject", httpMethod = "POST", response = classOf[ProjectOperationResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"project\" to create", required = true,
            dataTypeClass = classOf[CreateProjectApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def addProject: Route = path(ProjectsBasePath) {
        post {
            entity(as[CreateProjectApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[ProjectCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectCreateRequestADM(
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

    /* returns all unique keywords for all projects as a list */
    private def getKeywords: Route = path(ProjectsBasePath / "Keywords") {
        get {
            requestContext =>
                val requestMessage: Future[ProjectsKeywordsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectsKeywordsGetRequestADM(requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /* returns all keywords for a single project */
    private def getProjectKeywords: Route = path(ProjectsBasePath / "iri" / Segment / "Keywords") { value =>
        get {
            requestContext =>
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                val requestMessage: Future[ProjectKeywordsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectKeywordsGetRequestADM(projectIri = checkedProjectIri, requestingUser = requestingUser)

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
     * returns a single project identified through iri
     */
    private def getProjectByIri: Route = path(ProjectsBasePath / "iri" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                } yield ProjectGetRequestADM(ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)), requestingUser = requestingUser)

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
     * returns a single project identified through shortname.
     */
    private def getProjectByShortname: Route = path(ProjectsBasePath / "shortname" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    shortNameDec = stringFormatter.validateAndEscapeProjectShortname(value, throw BadRequestException(s"Invalid project shortname $value"))

                } yield ProjectGetRequestADM(ProjectIdentifierADM(maybeShortname = Some(shortNameDec)), requestingUser = requestingUser)

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
     * returns a single project identified through shortcode.
     */
    private def getProjectByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(value, throw BadRequestException(s"Invalid project shortcode $value"))

                } yield ProjectGetRequestADM(ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)), requestingUser = requestingUser)

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
     * update a project identified by iri
     */
    private def changeProject: Route = path(ProjectsBasePath / "iri" / Segment) { value =>
        put {
            entity(as[ChangeProjectApiRequestADM]) { apiRequest =>
                requestContext =>
                    val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                    /* the api request is already checked at time of creation. see case class. */

                    val requestMessage: Future[ProjectChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectChangeRequestADM(
                        projectIri = checkedProjectIri,
                        changeProjectRequest = apiRequest,
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
     * API MAY CHANGE: update project status to false
     */
    @ApiMayChange
    private def deleteProject: Route = path(ProjectsBasePath / "iri" / Segment) { value =>
        delete {
            requestContext =>
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                val requestMessage: Future[ProjectChangeRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectChangeRequestADM(
                    projectIri = checkedProjectIri,
                    changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
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
     * API MAY CHANGE: returns all members part of a project identified through iri
     */
    @ApiMayChange
    private def getProjectMembersByIri: Route = path(ProjectsBasePath / "iri" / Segment / "members") { value =>
        get {

            requestContext =>
                val requestMessage: Future[ProjectMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                } yield ProjectMembersGetRequestADM(ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)), requestingUser = requestingUser)

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
     * API MAY CHANGE: returns all members part of a project identified through shortname
     */
    @ApiMayChange
    private def getProjectMembersByShortname: Route = path(ProjectsBasePath / "shortname" / Segment / "members") { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    shortNameDec = stringFormatter.validateAndEscapeProjectShortname(value, throw BadRequestException(s"Invalid project shortname $value"))

                } yield ProjectMembersGetRequestADM(ProjectIdentifierADM(maybeShortname = Some(shortNameDec)), requestingUser = requestingUser)

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
     * API MAY CHANGE: returns all members part of a project identified through shortcode
     */
    @ApiMayChange
    private def getProjectMembersByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment / "members") { value =>
        get {

            requestContext =>
                val requestMessage: Future[ProjectMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(value, throw BadRequestException(s"Invalid project shortcode $value"))

                } yield ProjectMembersGetRequestADM(ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)), requestingUser = requestingUser)

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
     * API MAY CHANGE: returns all admin members part of a project identified through iri
     */
    @ApiMayChange
    private def getProjectAdminMembersByIri: Route = path(ProjectsBasePath / "iri" / Segment / "admin-members") { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                } yield ProjectAdminMembersGetRequestADM(ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)), requestingUser = requestingUser)

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
     * API MAY CHANGE: returns all admin members part of a project identified through shortname
     */
    @ApiMayChange
    private def getProjectAdminMembersByShortname: Route = path(ProjectsBasePath / "shortname" / Segment / "admin-members") { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedShortname = stringFormatter.validateAndEscapeProjectShortname(value, throw BadRequestException(s"Invalid project shortname $value"))

                } yield ProjectAdminMembersGetRequestADM(ProjectIdentifierADM(maybeShortname = Some(checkedShortname)), requestingUser = requestingUser)

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
     * API MAY CHANGE: returns all admin members part of a project identified through shortcode
     */
    @ApiMayChange
    private def getProjectAdminMembersByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment / "admin-members") { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedShortcode = stringFormatter.validateProjectShortcode(value, throw BadRequestException(s"Invalid project shortcode $value"))

                } yield ProjectAdminMembersGetRequestADM(ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)), requestingUser = requestingUser)

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
     * Returns the project's restricted view settings identified through IRI.
     */
    @ApiMayChange
    private def getProjectRestrictedViewSettingsByIri: Route = path(ProjectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)

                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(maybeIri = Some(value)), requestingUser)

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
     * Returns the project's restricted view settings identified through shortname.
     */
    @ApiMayChange
    private def getProjectRestrictedViewSettingsByShortname: Route = path(ProjectsBasePath / "shortname" / Segment / "RestrictedViewSettings") { value: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    shortNameDec = java.net.URLDecoder.decode(value, "utf-8")

                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(maybeShortname = Some(shortNameDec)), requestingUser)

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
     * Returns the project's restricted view settings identified through shortcode.
     */
    @ApiMayChange
    private def getProjectRestrictedViewSettingsByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(maybeShortcode = Some(value)), requestingUser)

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
     * Returns all ontologies, data, and configuration belonging to a project.
     */
    private def getProjectData: Route = path(ProjectsBasePath / "iri" / Segment / "AllData") { projectIri: IRI =>
        get {
            respondWithHeader(`Content-Disposition`(ContentDispositionTypes.attachment, Map(("filename", "project-data.trig")))) {
                requestContext =>
                    val projectIdentifier = ProjectIdentifierADM(maybeIri = Some(projectIri))

                    val httpEntityFuture: Future[HttpEntity.Chunked] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage = ProjectDataGetRequestADM(projectIdentifier, requestingUser)
                        responseMessage <- (responderManager ? requestMessage).mapTo[ProjectDataGetResponseADM]

                        // Stream the output file back to the client, then delete the file.

                        source: Source[ByteString, Unit] = FileIO.fromPath(responseMessage.projectDataFile.toPath).watchTermination() {
                            case (_: Future[IOResult], result: Future[Done]) =>
                                result.onComplete((_: Try[Done]) => responseMessage.projectDataFile.delete)
                        }

                        httpEntity = HttpEntity(ContentTypes.`application/octet-stream`, source)
                    } yield httpEntity

                    requestContext.complete(httpEntityFuture)
            }
        }
    }
}
