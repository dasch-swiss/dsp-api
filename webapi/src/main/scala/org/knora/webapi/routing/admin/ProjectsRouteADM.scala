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

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.BadRequestException
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

object ProjectsRouteADM {
    val ProjectsBasePath = PathMatcher("admin" / "projects")
}

@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ProjectsADMJsonProtocol {

    import ProjectsRouteADM.ProjectsBasePath

    override def knoraApiPath: Route =
                getProjects ~
                addProject ~
                getKeywords ~
                getProjectKeywords ~
                getProject ~
                changeProject ~
                deleteProject ~
                getProjectMembers ~
                getProjectAdminMembers ~
                getProjectRestrictedViewSettings


    /* return all projects */
    @ApiOperation(value = "Get projects", nickname = "getProjects", httpMethod = "GET", response = classOf[ProjectsGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def getProjects: Route = path(ProjectsBasePath) {
        get {requestContext =>
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
    private def getProjectKeywords: Route = path(ProjectsBasePath / Segment / "Keywords") { value =>
        get {
            requestContext =>
                val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

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

    /* returns a single project identified either through iri, shortname, or shortcode */
    private def getProject: Route = path(ProjectsBasePath / Segment) { value =>
        get {
            parameters("identifier" ? "iri") { identifier: String =>
                requestContext =>
                    val requestMessage: Future[ProjectGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield if (identifier == "shortname") { // identify project by shortname.
                        val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                    } else if (identifier == "shortcode") {
                        val shortcodeDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(shortcodeDec), requestingUser = requestingUser)
                    } else { // identify project by iri. this is the default case.
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                        ProjectGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
    }

    /* update a project identified by iri */
    private def changeProject: Route = path(ProjectsBasePath / Segment) { value =>
        put {
            entity(as[ChangeProjectApiRequestADM]) { apiRequest =>
                requestContext =>
                    val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

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
    private def deleteProject: Route =  path(ProjectsBasePath / Segment) { value =>
        delete {
            requestContext =>
                val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

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
      * API MAY CHANGE: returns all members part of a project identified through iri, shortname or shortcode
      */
    @ApiMayChange
    private def getProjectMembers: Route = path(ProjectsBasePath / Segment / "members") { value =>
        get {

            parameters("identifier" ? "iri") { identifier: String =>
                requestContext =>
                    val requestMessage: Future[ProjectMembersGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield if (identifier == "shortname") {
                        // identify project by shortname
                        val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectMembersGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                    } else if (identifier == "shortcode") {
                        // identify project by shortcode
                        val shortcodeDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectMembersGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(shortcodeDec), requestingUser = requestingUser)
                    } else {
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                        ProjectMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
    }

    /**
      * API MAY CHANGE: returns all admin members part of a project identified through iri, shortname or shortcode
      */
    @ApiMayChange
    private def getProjectAdminMembers: Route = path(ProjectsBasePath / Segment / "admin-members" ) { value =>
        get {

            parameters("identifier" ? "iri") { identifier: String =>
                requestContext =>
                    val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield if (identifier == "shortname") {
                        // identify project by shortname
                        val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectAdminMembersGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                    } else if (identifier == "shortcode") {
                        // identify project by shortcode
                        val shortcodeDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectAdminMembersGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(shortcodeDec), requestingUser = requestingUser)
                    } else {
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                        ProjectAdminMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
    }


    /**
      * Returns the project's restricted view settings.
      */
    @ApiMayChange
    private def getProjectRestrictedViewSettings: Route = path(ProjectsBasePath / Segment / "RestrictedViewSettings") { identifier: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(identifier), requestingUser)

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
