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
import org.knora.webapi.{BadRequestException, OntologyConstants}
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._

import scala.concurrent.Future

object ProjectsRouteADM {
    val ProjectsBasePath = PathMatcher("admin" / "projects")
}

@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ProjectsADMJsonProtocol with ClientEndpoint {

    import ProjectsRouteADM.ProjectsBasePath

    /**
      * The name of this [[ClientEndpoint]].
      */
    override val name: String = "ProjectsEndpoint"

    /**
      * The URL path of this [[ClientEndpoint]].
      */
    override val urlPath: String = "/groups"

    /**
      * A description of this [[ClientEndpoint]].
      */
    override val description: String = "An endpoint for working with Knora groups."

    // Classes used in client function definitions.

    private val Project = classRef(OntologyConstants.KnoraAdminV2.ProjectClass.toSmartIri)
    private val ProjectsResponse = classRef(OntologyConstants.KnoraAdminV2.ProjectsResponse.toSmartIri)
    private val ProjectResponse = classRef(OntologyConstants.KnoraAdminV2.ProjectResponse.toSmartIri)
    private val KeywordsResponse = classRef(OntologyConstants.KnoraAdminV2.KeywordsResponse.toSmartIri)
    private val MembersResponse = classRef(OntologyConstants.KnoraAdminV2.MembersResponse.toSmartIri)
    private val ProjectRestrictedViewSettingsResponse = classRef(OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettingsResponse.toSmartIri)

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
        getProjectRestrictedViewSettingsByIri ~ getProjectRestrictedViewSettingsByShortname ~ getProjectRestrictedViewSettingsByShortcode


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

    private val getProjectsFunction: ClientFunction =
        "getProjects" description "Returns a list of all projects." params() doThis {
            httpGet(BasePath)
        } returns ProjectsResponse

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

    private val createProjectFunction: ClientFunction =
        "createProject" description "Creates a project." params (
            "project" description "The project to be created." paramType Project
            ) doThis {
            httpPost(
                path = BasePath,
                body = Some(arg("project"))
            )
        } returns ProjectResponse

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

    private val getKeywordsFunction: ClientFunction =
        "getKeywords" description "Gets all the unique keywords for all projects." params() doThis {
            httpGet(str("Keywords"))
        } returns KeywordsResponse

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

    private val getProjectKeywordsFunction: ClientFunction =
        "getProjectKeywords" description "Gets all the keywords for a project." params(
            "projectIri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            httpGet(str("iri") / arg("projectIri") / str("Keywords"))
        } returns KeywordsResponse

    /**
      * returns a single project identified through iri
      */
    private def getProjectByIri: Route = path(ProjectsBasePath / "iri" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                } yield ProjectGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectFunction: ClientFunction =
        "getProject" description "Gets a project by a property." params(
            "property" description "The name of the property by which the project is identified." paramType enum("iri", "shortname", "shortcode"),
            "value" description "The value of the property by which the project is identified." paramType StringDatatype
        ) doThis {
            httpGet(arg("property") / arg("value"))
        } returns ProjectResponse

    private val getProjectByIriFunction: ClientFunction =
        "getProjectByIri" description "Gets a project by IRI." params (
            "iri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            getProjectFunction withArgs(str("iri"), arg("iri") as StringDatatype)
        } returns ProjectResponse

    /**
      * returns a single project identified through shortname.
      */
    private def getProjectByShortname: Route = path(ProjectsBasePath / "shortname" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    shortNameDec = stringFormatter.validateAndEscapeProjectShortname(value, throw BadRequestException(s"Invalid project shortname $value"))

                } yield ProjectGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectByShortnameFunction: ClientFunction =
        "getProjectByShortname" description "Gets a project by shortname." params (
            "shortname" description "The shortname of the project." paramType StringDatatype
            ) doThis {
            getProjectFunction withArgs(str("shortname"), arg("shortname"))
        } returns ProjectResponse

    /**
      * returns a single project identified through shortcode.
      */
    private def getProjectByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                    checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(value, throw BadRequestException(s"Invalid project shortcode $value"))

                } yield ProjectGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(checkedShortcode), requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectByShortcodeFunction: ClientFunction =
        "getProjectByShortcode" description "Gets a project by shortcode." params (
            "shortcode" description "The shortcode of the project." paramType StringDatatype
            ) doThis {
            getProjectFunction withArgs(str("shortcode"), arg("shortcode"))
        } returns ProjectResponse

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

    private val updateProjectFunction: ClientFunction =
        "updateProject" description "Updates a project." params(
            "project" description "The project to be updated." paramType Project
            ) doThis {
            httpPut(
                path = str("iri") / argMember("project", "id"),
                body = Some(json(
                    "shortname" -> argMember("project", "shortname"),
                    "longname" -> argMember("project", "longname"),
                    "description" -> argMember("project", "description"),
                    "keywords" -> argMember("project", "keywords"),
                    "logo" -> argMember("project", "logo"),
                    "status" -> argMember("project", "status"),
                    "selfjoin" -> argMember("project", "selfjoin")
                ))
            )
        } returns ProjectResponse

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

    private val deleteProjectFunction: ClientFunction =
        "deleteProject" description "Deletes a project. This method does not actually delete a project, but sets the status to false." params (
            "project" description "The project to be deleted." paramType Project
            ) doThis {
            httpDelete(
                path = str("iri") / argMember("project", "id")
            )
        } returns ProjectResponse

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

                } yield ProjectMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectMembersFunction: ClientFunction =
        "getProjectMembers" description "Gets a project's members by a property." params(
            "property" description "The name of the property by which the project is identified." paramType enum("iri", "shortname", "shortcode"),
            "value" description "The value of the property by which the project is identified." paramType StringDatatype
        ) doThis {
            httpGet(arg("property") / arg("value") / str("members"))
        } returns MembersResponse

    private val getProjectMembersByIriFunction: ClientFunction =
        "getProjectMembersByIri" description "Gets the members of a project by IRI." params(
            "iri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            getProjectMembersFunction withArgs(str("iri"), arg("iri") as StringDatatype)
        } returns MembersResponse

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

                } yield ProjectMembersGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectMembersByShortnameFunction: ClientFunction =
        "getProjectMembersByShortname" description "Gets a project's members by shortname." params (
            "shortname" description "The shortname of the project." paramType StringDatatype
            ) doThis {
            getProjectMembersFunction withArgs(str("shortname"), arg("shortname"))
        } returns MembersResponse

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

                } yield ProjectMembersGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(checkedShortcode), requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectMembersByShortcodeFunction: ClientFunction =
        "getProjectMembersByShortcode" description "Gets a project's members by shortcode." params (
            "shortcode" description "The shortcode of the project." paramType StringDatatype
            ) doThis {
            getProjectMembersFunction withArgs(str("shortcode"), arg("shortcode"))
        } returns MembersResponse

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

                } yield ProjectAdminMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectAdminMembersFunction: ClientFunction =
        "getProjectAdminMembers" description "Gets a project's admin members by a property." params(
            "property" description "The name of the property by which the project is identified." paramType enum("iri", "shortname", "shortcode"),
            "value" description "The value of the property by which the project is identified." paramType StringDatatype
        ) doThis {
            httpGet(arg("property") / arg("value") / str("admin-members"))
        } returns MembersResponse

    private val getProjectAdminMembersByIriFunction: ClientFunction =
        "getProjectAdminMembersByIri" description "Gets the admin members of a project by IRI." params(
            "iri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            getProjectAdminMembersFunction withArgs(str("iri"), arg("iri") as StringDatatype)
        } returns MembersResponse

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

                } yield ProjectAdminMembersGetRequestADM(maybeIri = None, maybeShortname = Some(checkedShortname), maybeShortcode = None, requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectAdminMembersByShortnameFunction: ClientFunction =
        "getProjectAdminMembersByShortname" description "Gets a project's admin members by shortname." params (
            "shortname" description "The shortname of the project." paramType StringDatatype
            ) doThis {
            getProjectAdminMembersFunction withArgs(str("shortname"), arg("shortname"))
        } returns MembersResponse

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

                } yield ProjectAdminMembersGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(checkedShortcode), requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectAdminMembersByShortcodeFunction: ClientFunction =
        "getProjectAdminMembersByShortcode" description "Gets a project's admin members by shortcode." params (
            "shortcode" description "The shortcode of the project." paramType StringDatatype
            ) doThis {
            getProjectAdminMembersFunction withArgs(str("shortcode"), arg("shortcode"))
        } returns MembersResponse

    /**
      * Returns the project's restricted view settings identified through IRI.
      */
    @ApiMayChange
    private def getProjectRestrictedViewSettingsByIri: Route = path(ProjectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)

                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(iri = Some(value)), requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectRestrictedViewSettingsFunction: ClientFunction =
        "getProjectRestrictedViewSettings" description "Gets a project's restricted view settings by a property." params(
            "property" description "The name of the property by which the project is identified." paramType enum("iri", "shortname", "shortcode"),
            "value" description "The value of the property by which the project is identified." paramType StringDatatype
        ) doThis {
            httpGet(arg("property") / arg("value") / str("RestrictedViewSettings"))
        } returns ProjectRestrictedViewSettingsResponse

    private val getProjectRestrictedViewSettingByIriFunction: ClientFunction =
        "getProjectRestrictedViewSettingByIri" description "Gets a project's restricted view settings by IRI." params(
            "iri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            getProjectRestrictedViewSettingsFunction withArgs(str("iri"), arg("iri") as StringDatatype)
        } returns ProjectRestrictedViewSettingsResponse

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

                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(shortname = Some(shortNameDec)), requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectRestrictedViewSettingByShortnameFunction: ClientFunction =
        "getProjectRestrictedViewSettingByShortname" description "Gets a project's restricted view settings by shortname." params(
            "shortname" description "The shortname of the project." paramType StringDatatype
            ) doThis {
            getProjectRestrictedViewSettingsFunction withArgs(str("shortname"), arg("shortname") as StringDatatype)
        } returns ProjectRestrictedViewSettingsResponse

    /**
      * Returns the project's restricted view settings identified through shortcode.
      */
    @ApiMayChange
    private def getProjectRestrictedViewSettingsByShortcode: Route = path(ProjectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
        get {
            requestContext =>
                val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ProjectRestrictedViewSettingsGetRequestADM(ProjectIdentifierADM(shortcode = Some(value)), requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getProjectRestrictedViewSettingByShortcodeFunction: ClientFunction =
        "getProjectRestrictedViewSettingByShortcode" description "Gets a project's restricted view settings by shortcode." params(
            "shortcode" description "The shortcode of the project." paramType StringDatatype
            ) doThis {
            getProjectRestrictedViewSettingsFunction withArgs(str("shortcode"), arg("shortcode") as StringDatatype)
        } returns ProjectRestrictedViewSettingsResponse

    /**
      * The functions defined by this [[ClientEndpoint]].
      */
    override val functions: Seq[ClientFunction] = Seq(
        getProjectsFunction,
        createProjectFunction,
        getKeywordsFunction,
        getProjectKeywordsFunction,
        updateProjectFunction,
        deleteProjectFunction,
        getProjectFunction,
        getProjectByIriFunction,
        getProjectByShortnameFunction,
        getProjectByShortcodeFunction,
        getProjectMembersFunction,
        getProjectMembersByIriFunction,
        getProjectMembersByShortnameFunction,
        getProjectMembersByShortcodeFunction,
        getProjectAdminMembersFunction,
        getProjectAdminMembersByIriFunction,
        getProjectAdminMembersByShortnameFunction,
        getProjectAdminMembersByShortcodeFunction,
        getProjectRestrictedViewSettingsFunction,
        getProjectRestrictedViewSettingByIriFunction,
        getProjectRestrictedViewSettingByShortnameFunction,
        getProjectRestrictedViewSettingByShortcodeFunction
    )
}
